package vip.mate.tool.mcp.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 独立 MCP Streamable HTTP 客户端 — 直接对接 FastMCP httpStream 端点。
 * <p>
 * 绕过 Spring AI MCP SDK 的传输层兼容性问题：
 * FastMCP 的 POST 响应包装在 SSE 格式（text/event-stream）中，
 * Java SDK 的 HttpClientStreamableHttpTransport 期望 application/json。
 * 本客户端直接解析 SSE 响应 + 管理 session。
 */
@Slf4j
public class YliyunMcpHttpClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    @Getter
    private String sessionId;
    @Getter
    private List<McpSchema.Tool> tools = Collections.emptyList();
    @Getter
    private boolean connected;
    @Getter
    private String serverName;
    @Getter
    private String serverVersion;

    public YliyunMcpHttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** 完整 MCP 握手：initialize → tools/list */
    public boolean connect() {
        try {
            // 1. Initialize
            Map<String, Object> initParams = Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "mateclaw", "version", "1.0")
            );
            JsonNode initResult = sendRequest("initialize", initParams);
            if (initResult == null) return false;

            serverName = path(initResult, "serverInfo", "name").asText("unknown");
            serverVersion = path(initResult, "serverInfo", "version").asText("0");
            log.info("[YliyunMCP] Initialized: {} v{}, session={}",
                    serverName, serverVersion, sessionId);

            // 2. Tools/List
            JsonNode toolsResult = sendRequest("tools/list", Map.of());
            if (toolsResult == null) return false;

            JsonNode toolsArray = toolsResult.get("tools");
            if (toolsArray != null && toolsArray.isArray()) {
                List<McpSchema.Tool> list = new ArrayList<>();
                var mapper = io.modelcontextprotocol.json.McpJsonMapper.createDefault();
                for (JsonNode t : toolsArray) {
                    String name = t.get("name").asText();
                    String desc = t.has("description") ? t.get("description").asText() : "";
                    String schemaJson = t.has("inputSchema") ? t.get("inputSchema").toString() : "{}";
                    McpSchema.Tool tool = McpSchema.Tool.builder()
                            .name(name)
                            .description(desc)
                            .inputSchema(mapper, schemaJson)
                            .build();
                    list.add(tool);
                }
                this.tools = Collections.unmodifiableList(list);
            }

            connected = true;
            log.info("[YliyunMCP] Connected: {} tools discovered", tools.size());
            return true;

        } catch (Exception e) {
            log.error("[YliyunMCP] Connection failed: {}", e.getMessage());
            connected = false;
            return false;
        }
    }

    /** 调用 MCP 工具 */
    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        try {
            JsonNode result = sendRequest("tools/call",
                    Map.of("name", toolName, "arguments", arguments));
            if (result == null) {
                return errorResult("no response from MCP server");
            }
            // Extract content from result
            List<McpSchema.Content> contents = new ArrayList<>();
            if (result.has("content") && result.get("content").isArray()) {
                for (JsonNode c : result.get("content")) {
                    String type = c.has("type") ? c.get("type").asText() : "text";
                    String text = c.has("text") ? c.get("text").asText() : c.toString();
                    contents.add(new McpSchema.TextContent(text));
                }
            } else {
                contents.add(new McpSchema.TextContent(result.toString()));
            }
            return new McpSchema.CallToolResult(contents, false);
        } catch (Exception e) {
            return errorResult(e.getMessage());
        }
    }

    private McpSchema.CallToolResult errorResult(String msg) {
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("{\"error\":\"" + msg + "\"}")), true);
    }

    /** 发送 JSON-RPC 请求并解析响应 */
    private JsonNode sendRequest(String method, Map<String, Object> params) {
        try {
            Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", System.currentTimeMillis(),
                "method", method,
                "params", params
            );
            String body = MAPPER.writeValueAsString(req);

            var reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("X-Internal-Service", "mateclaw")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30));
            if (sessionId != null) {
                reqBuilder.header("mcp-session-id", sessionId);
            }

            HttpResponse<String> resp = HTTP.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            // 提取 session ID
            String newSession = resp.headers().firstValue("mcp-session-id").orElse(null);
            if (newSession != null) sessionId = newSession;

            // 解析 SSE 或 JSON 响应
            String responseBody = resp.body();
            String json = extractJson(responseBody);

            JsonNode root = MAPPER.readTree(json);
            if (root.has("error")) {
                log.error("[YliyunMCP] RPC error for {}: {}", method, root.get("error"));
                return null;
            }
            return root.get("result");

        } catch (Exception e) {
            log.error("[YliyunMCP] Request failed for {}: {}", method, e.getMessage());
            return null;
        }
    }

    /** 从 SSE 包装中提取 JSON */
    private String extractJson(String body) {
        if (body == null || body.isBlank()) return "{}";
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) return trimmed;
        for (String line : trimmed.split("\n")) {
            if (line.startsWith("data: ")) return line.substring(6);
        }
        return trimmed;
    }

    private static JsonNode path(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node == null) break;
            node = node.get(key);
        }
        return node != null ? node : MAPPER.createObjectNode();
    }
}
