package vip.mate.tool.metadata;

import vip.mate.tool.guard.model.GuardSeverity;

import java.util.Map;

/**
 * Unified runtime metadata attached to every tool invocation.
 */
public record ToolRuntimeMetadata(
        String toolName,
        ToolSourceType sourceType,
        boolean readOnly,
        boolean destructive,
        GuardSeverity riskLevel,
        long timeoutMs,
        boolean replayable,
        boolean concurrencySafe,
        boolean returnDirect
) {

    public Map<String, Object> toMap() {
        return Map.of(
                "toolName", toolName != null ? toolName : "",
                "sourceType", sourceType != null ? sourceType.name() : ToolSourceType.UNKNOWN.name(),
                "readOnly", readOnly,
                "destructive", destructive,
                "riskLevel", riskLevel != null ? riskLevel.name() : GuardSeverity.INFO.name(),
                "timeoutMs", timeoutMs,
                "replayable", replayable,
                "concurrencySafe", concurrencySafe,
                "returnDirect", returnDirect
        );
    }

    public boolean mutatesState() {
        return !readOnly;
    }

    public boolean externalMutation() {
        return !readOnly && (sourceType == ToolSourceType.MCP
                || sourceType == ToolSourceType.PLUGIN
                || sourceType == ToolSourceType.SKILL
                || sourceType == ToolSourceType.UNKNOWN);
    }
}