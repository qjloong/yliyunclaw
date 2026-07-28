package vip.mate.tool.mcp.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import vip.mate.tool.ToolRegistry;
import vip.mate.tool.mcp.model.McpServerEntity;
import vip.mate.tool.mcp.repository.McpServerMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class YliyunMcpConnector {

    private final McpServerMapper mcpServerMapper;
    private final ToolRegistry toolRegistry;

    @Getter
    private volatile YliyunMcpHttpClient client;
    @Getter
    private volatile boolean available;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        McpServerEntity server = mcpServerMapper.selectOne(
                new LambdaQueryWrapper<McpServerEntity>()
                        .eq(McpServerEntity::getName, "yliyun-mcp")
                        .eq(McpServerEntity::getEnabled, true));
        if (server == null) {
            log.warn("[YliyunMCP] No enabled yliyun-mcp server found in DB");
            return;
        }

        String url = server.getUrl();
        if (url == null || url.isBlank()) {
            url = "http://localhost:18100/mcp";
        }

        log.info("[YliyunMCP] Connecting to {} ...", url);
        client = new YliyunMcpHttpClient(url);
        available = client.connect();

        if (available) {
            log.info("[YliyunMCP] ✅ Ready: {} tools, session={}",
                    client.getTools().size(), client.getSessionId());
            registerTools();
            updateDbStatus(server.getId(), "connected", null, client.getTools().size());
        } else {
            log.warn("[YliyunMCP] ❌ Connection failed");
            updateDbStatus(server.getId(), "error", "HTTP connection failed", 0);
        }
    }

    private void registerTools() {
        int count = 0;
        for (McpSchema.Tool tool : client.getTools()) {
            String prefix = "mcp__yliyun__";
            String fullName = prefix + tool.name();
            String schemaJson = "{}";
            try {
                schemaJson = new ObjectMapper().writeValueAsString(tool.inputSchema());
            } catch (Exception ignored) {}
            ToolDefinition def = ToolDefinition.builder()
                    .name(fullName)
                    .description("[云盘] " + (tool.description() != null ? tool.description() : tool.name()))
                    .inputSchema(schemaJson)
                    .build();
            ToolCallback callback = new YliyunMcpToolCallback(def, tool.name(), this);
            toolRegistry.registerPluginTool(callback, () -> available);
            count++;
        }
        log.info("[YliyunMCP] Registered {} tools in ToolRegistry", count);
    }

    private void updateDbStatus(Long id, String status, String error, int toolCount) {
        try {
            McpServerEntity update = new McpServerEntity();
            update.setId(id);
            update.setLastStatus(status);
            update.setLastError(error);
            update.setToolCount(toolCount);
            if ("connected".equals(status)) {
                update.setLastConnectedTime(LocalDateTime.now());
            }
            mcpServerMapper.updateById(update);
        } catch (Exception e) {
            log.warn("[YliyunMCP] Failed to update DB status: {}", e.getMessage());
        }
    }

    public List<McpSchema.Tool> getTools() {
        return client != null ? client.getTools() : Collections.emptyList();
    }

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        if (client == null || !available) {
            return Map.of("code", 500, "msg", "yliyun-mcp 未连接", "data", null);
        }
        try {
            McpSchema.CallToolResult result = client.callTool(toolName, arguments);
            String text = result.content().stream()
                    .filter(c -> c instanceof McpSchema.TextContent)
                    .map(c -> ((McpSchema.TextContent) c).text())
                    .findFirst().orElse("{}");
            return Map.of("code", 200, "msg", "ok", "data", text);
        } catch (Exception e) {
            return Map.of("code", 500, "msg", e.getMessage(), "data", null);
        }
    }

    public String getStatus() {
        if (client == null) return "not_configured";
        if (available) return "connected (" + getTools().size() + " tools)";
        return "disconnected";
    }
}
