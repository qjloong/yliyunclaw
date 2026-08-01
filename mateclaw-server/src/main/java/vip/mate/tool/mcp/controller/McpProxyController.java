package vip.mate.tool.mcp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.tool.mcp.model.McpServerEntity;
import vip.mate.tool.mcp.runtime.McpClientManager;
import vip.mate.tool.mcp.runtime.McpClientManager.ConnectionResult;
import vip.mate.tool.mcp.runtime.McpClientManager.ToolCallResult;
import vip.mate.tool.mcp.runtime.McpIdentityForwardService;
import vip.mate.tool.mcp.service.McpServerService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticated lightweight access to the Yliyun MCP server.
 *
 * <p>This controller is intentionally only an HTTP facade: discovery, pooled
 * connection lifecycle, tool calls, identity forwarding and error semantics all
 * go through the general {@link McpClientManager}. There is no second Yliyun
 * connector or duplicate tool registry.
 */
@Tag(name = "MCP 代理")
@Slf4j
@RestController
@RequestMapping("/api/v1/mcp/proxy")
@RequiredArgsConstructor
public class McpProxyController {

    private static final String YLIYUN_SERVER_NAME = "yliyun-mcp";

    private final McpServerService mcpServerService;
    private final McpClientManager mcpClientManager;
    private final ObjectMapper objectMapper;

    @PostMapping("/{toolName}")
    @Operation(summary = "通过通用 MCP Runtime 调用云盘工具")
    public Map<String, Object> callTool(@PathVariable String toolName,
                                        @RequestBody Map<String, Object> arguments,
                                        Authentication authentication) {
        McpServerEntity server = mcpServerService.getByName(YLIYUN_SERVER_NAME);
        ToolCallResult result = mcpClientManager.callTool(
                server.getId(), toolName, arguments, toolContext(authentication));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", result.success() ? 200 : 500);
        response.put("msg", result.success() ? "ok" : result.content());
        response.put("data", result.success() ? result.content() : null);
        response.put("errorCode", result.success() ? null : result.code());
        response.put("stage", result.stage());
        response.put("latencyMs", result.latencyMs());
        return response;
    }

    @GetMapping("/status")
    @Operation(summary = "查询通用 Runtime 中的云盘 MCP 状态")
    public Map<String, Object> status() {
        McpServerEntity server = mcpServerService.getByName(YLIYUN_SERVER_NAME);
        ConnectionResult connection = mcpClientManager.getConnectionResult(server.getId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("status", connection != null
                ? (connection.success() ? "connected" : "error")
                : "not_connected");
        response.put("available", connection != null && connection.success());
        response.put("tools", mcpClientManager.getServerTools(server.getId()).size());
        response.put("message", connection != null ? connection.message() : "尚无连接结果");
        response.put("latencyMs", connection != null ? connection.latencyMs() : null);
        return response;
    }

    @PostMapping("/diagnostics")
    @Operation(summary = "分层诊断云盘 MCP（连接、协议、工具、当前用户身份）")
    public Map<String, Object> diagnostics(
            @RequestParam(defaultValue = "false") boolean includeWrite,
            Authentication authentication) {
        McpServerEntity server = mcpServerService.getByName(YLIYUN_SERVER_NAME);
        List<Map<String, Object>> stages = new ArrayList<>();
        String traceId = "mcp-diagnostic-" + UUID.randomUUID();
        ToolContext context = toolContext(authentication, traceId);
        log.info("[MCP Diagnostics] traceId={} includeWrite={} user={}",
                traceId, includeWrite, authentication != null ? authentication.getName() : "anonymous");

        boolean configOk = "streamable_http".equals(server.getTransport())
                && server.getUrl() != null && server.getUrl().endsWith("/mcp");
        stages.add(stage("config", configOk,
                configOk ? "Streamable HTTP /mcp 配置正确"
                        : "transport 必须为 streamable_http，URL 必须指向 /mcp",
                null, 0));

        ConnectionResult transport = mcpServerService.testConnection(server);
        stages.add(stage("transport", transport.success(), transport.message(),
                transport.success() ? null : "检查 18100 端口、/mcp 路由和内部认证头",
                transport.latencyMs()));

        int discovered = transport.discoveredTools() != null
                ? transport.discoveredTools().size() : 0;
        boolean protocolOk = transport.success() && discovered == 14;
        stages.add(stage("protocol", protocolOk,
                "initialize/tools/list 发现 " + discovered + " 个工具",
                protocolOk ? null : "核对 MCP /manifest 与 MateClaw tools/list",
                transport.latencyMs()));

        ToolCallResult profile = mcpClientManager.callTool(
                server.getId(), "user.profile", Map.of(), context);
        stages.add(stage("identity", profile.success(),
                profile.content(),
                profile.success() ? null : "当前 MateClaw 用户需要唯一的 (tenantId, yliyunUserId) 映射和有效 OBO 密钥",
                profile.latencyMs()));

        ToolCallResult read = mcpClientManager.callTool(
                server.getId(), "file.list",
                Map.of("parentId", 0, "maxResults", 10), context);
        stages.add(stage("read", read.success(),
                read.success() ? "file.list 只读调用成功" : read.content(),
                read.success() ? null : "检查当前云盘用户的根目录读取权限和云盘后端日志",
                read.latencyMs()));

        if (includeWrite) {
            stages.add(runRecoverableWriteDiagnostic(server.getId(), context, traceId));
        }

        boolean ok = stages.stream().allMatch(row -> Boolean.TRUE.equals(row.get("success")));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 200);
        response.put("success", ok);
        response.put("serverId", server.getId());
        response.put("serverName", server.getName());
        response.put("traceId", traceId);
        response.put("writeIncluded", includeWrite);
        response.put("stages", stages);
        log.info("[MCP Diagnostics] traceId={} success={} stages={}",
                traceId, ok, stages.size());
        return response;
    }

    private ToolContext toolContext(Authentication authentication) {
        return toolContext(authentication, null);
    }

    private ToolContext toolContext(Authentication authentication, String traceId) {
        Long userId = authentication != null && authentication.getDetails() instanceof Number number
                ? number.longValue() : null;
        String username = authentication != null ? authentication.getName() : "";
        ChatOrigin origin = ChatOrigin.web(null, username, null, null, null, userId);
        if (traceId == null || traceId.isBlank()) {
            return origin.toToolContext();
        }
        Map<String, Object> context = new HashMap<>(origin.toToolContext().getContext());
        context.put(McpIdentityForwardService.TRACE_ID_CONTEXT_KEY, traceId);
        return new ToolContext(context);
    }

    private Map<String, Object> runRecoverableWriteDiagnostic(
            Long serverId, ToolContext context, String traceId) {
        String folderName = ".mateclaw-mcp-diagnostic-" + traceId.substring(traceId.length() - 12);
        ToolCallResult create = mcpClientManager.callTool(
                serverId, "file.create",
                Map.of(
                        "parentId", 0,
                        "name", folderName,
                        "type", "directory",
                        "idempotencyKey", traceId + "-create"
                ),
                context);
        if (!create.success()) {
            return stage("write", false, create.content(),
                    "检查当前云盘用户的创建权限、配额和结构化错误中的 traceId",
                    create.latencyMs());
        }

        long folderId;
        try {
            JsonNode payload = objectMapper.readTree(create.content());
            folderId = payload.path("fileId").asLong(0);
            if (folderId <= 0) {
                throw new IllegalStateException("file.create 未返回有效 fileId");
            }
        } catch (Exception e) {
            return stage("write", false,
                    "临时目录已创建，但无法解析 fileId：" + e.getMessage(),
                    "请按名称手工清理 " + folderName + "，并检查 MCP 成功响应契约",
                    create.latencyMs());
        }

        ToolCallResult cleanup = mcpClientManager.callTool(
                serverId, "file.delete",
                Map.of(
                        "fileId", folderId,
                        "permanent", false,
                        "idempotencyKey", traceId + "-cleanup"
                ),
                context);
        long latency = create.latencyMs() + cleanup.latencyMs();
        if (!cleanup.success()) {
            return stage("write", false,
                    "临时目录创建成功，但回收清理失败：" + cleanup.content(),
                    "请在云盘中清理 " + folderName + "，并使用 traceId 排查删除权限",
                    latency);
        }
        return stage("write", true,
                "file.create/file.delete 可回滚写入检查成功，临时目录已移入回收站",
                null, latency);
    }

    private Map<String, Object> stage(String name, boolean success, String message,
                                      String suggestion, long latencyMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", name);
        result.put("success", success);
        result.put("message", message);
        result.put("suggestion", suggestion);
        result.put("latencyMs", latencyMs);
        return result;
    }
}
