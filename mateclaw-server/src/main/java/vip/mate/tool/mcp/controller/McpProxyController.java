package vip.mate.tool.mcp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import vip.mate.tool.mcp.runtime.YliyunMcpConnector;

import java.util.Map;

@Tag(name = "MCP 代理")
@Slf4j
@RestController
@RequestMapping("/api/v1/mcp/proxy")
@RequiredArgsConstructor
public class McpProxyController {

    private final YliyunMcpConnector connector;

    @PostMapping("/{toolName}")
    @Operation(summary = "代理调用 yliyun-mcp 工具")
    public Map<String, Object> callTool(@PathVariable String toolName,
                                         @RequestBody Map<String, Object> arguments) {
        return connector.callTool(toolName, arguments);
    }

    @GetMapping("/status")
    @Operation(summary = "查询 yliyun-mcp 连接状态")
    public Map<String, Object> status() {
        return Map.of(
            "code", 200,
            "status", connector.getStatus(),
            "available", connector.isAvailable(),
            "tools", connector.getTools().size()
        );
    }
}
