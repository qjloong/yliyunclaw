package vip.mate.tool.mcp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

@Slf4j
public class YliyunMcpToolCallback implements ToolCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ToolDefinition toolDefinition;
    private final String rawToolName;
    private final YliyunMcpConnector connector;

    public YliyunMcpToolCallback(ToolDefinition toolDefinition, String rawToolName, YliyunMcpConnector connector) {
        this.toolDefinition = toolDefinition;
        this.rawToolName = rawToolName;
        this.connector = connector;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = MAPPER.readValue(toolInput, Map.class);
            Map<String, Object> result = connector.callTool(rawToolName, args);
            int code = result.get("code") instanceof Integer ? (Integer) result.get("code") : 500;
            if (code == 200) {
                return result.get("data") instanceof String ? (String) result.get("data") : "{}";
            }
            return "{\"error\":\"" + result.getOrDefault("msg", "unknown") + "\"}";
        } catch (Exception e) {
            log.error("[YliyunMCP] Tool call failed: {} - {}", rawToolName, e.getMessage());
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
