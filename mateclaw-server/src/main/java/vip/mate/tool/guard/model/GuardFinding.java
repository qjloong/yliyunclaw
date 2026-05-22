package vip.mate.tool.guard.model;

import java.util.Map;

/**
 * 单条安全发现
 * <p>
 * 由 Guardian 评估产出，携带完整的威胁上下文信息。
 * 使用不可变 record，产出后不允许被修改。
 */
public record GuardFinding(
        String ruleId,
        GuardSeverity severity,
        GuardCategory category,
        String title,
        String description,
        String remediation,
        String toolName,
        String paramName,
        String matchedPattern,
        String snippet,
        Map<String, Object> metadata
) {

    public GuardFinding(String ruleId, GuardSeverity severity, GuardCategory category,
                        String title, String description, String remediation,
                        String toolName, String paramName, String matchedPattern, String snippet) {
        this(ruleId, severity, category, title, description, remediation,
                toolName, paramName, matchedPattern, snippet, Map.of());
    }

    /**
     * 转为可序列化的 Map（用于 SSE 事件和 JSON 存储）
     */
    public Map<String, Object> toMap() {
        return Map.ofEntries(
                Map.entry("ruleId", ruleId != null ? ruleId : ""),
                Map.entry("severity", severity != null ? severity.name() : ""),
                Map.entry("category", category != null ? category.name() : ""),
                Map.entry("title", title != null ? title : ""),
                Map.entry("description", description != null ? description : ""),
                Map.entry("remediation", remediation != null ? remediation : ""),
                Map.entry("toolName", toolName != null ? toolName : ""),
                Map.entry("paramName", paramName != null ? paramName : ""),
                Map.entry("matchedPattern", matchedPattern != null ? matchedPattern : ""),
                                Map.entry("snippet", snippet != null ? snippet : ""),
                                Map.entry("metadata", metadata != null ? metadata : Map.of())
        );
    }
}
