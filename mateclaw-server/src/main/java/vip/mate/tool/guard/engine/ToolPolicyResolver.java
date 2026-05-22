package vip.mate.tool.guard.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.tool.metadata.ToolRuntimeMetadata;
import vip.mate.tool.metadata.ToolSourceType;
import vip.mate.tool.guard.model.*;
import vip.mate.workspace.core.model.WorkspacePolicy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.Set;

/**
 * 策略解析器
 * <p>
 * 将 Guardian 产出的 findings 映射为最终裁决（GuardDecision）。
 * <ul>
 *   <li>Guardian 只负责发现风险事实</li>
 *   <li>PolicyResolver 负责把事实映射为执行策略</li>
 * </ul>
 * 采用 findings-driven approval 策略，不按工具类型默认审批。
 */
@Slf4j
@Component
public class ToolPolicyResolver {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> FILE_TOOL_NAMES = Set.of(
        "read_file", "list_directory", "index_directory_materials", "filter_directory_materials", "write_file", "edit_file",
        "extract_document_text", "extract_pdf_text", "extract_docx_text"
    );

    private static final Set<String> FILE_PATH_KEYS = Set.of(
        "filePath", "file_path", "path", "targetPath", "target_path", "directoryPath", "directory_path"
    );

    private static final Set<String> SHELL_TOOL_NAMES = Set.of(
        "execute_shell_command", "shell_execute", "run_command"
    );

    private static final Set<String> NETWORK_TOOL_NAMES = Set.of(
        "search", "web_search", "browser_use", "browserusetool"
    );

    private static final Pattern NETWORK_COMMAND_PATTERN = Pattern.compile(
        "(?i)(curl|wget|invoke-webrequest|\\biwr\\b|\\birm\\b|git\\s+(clone|fetch|pull|push)|npm\\s+install|pnpm\\s+install|yarn\\s+add|pip\\s+install|uv\\s+pip|docker\\s+pull|apt(-get)?\\s+install|brew\\s+install)");

    public List<GuardFinding> augmentFindings(List<GuardFinding> findings, ToolInvocationContext context) {
        List<GuardFinding> merged = new ArrayList<>(findings != null ? findings : List.of());
        ToolRuntimeMetadata metadata = context != null ? context.toolMetadata() : null;
    WorkspacePolicy workspacePolicy = context != null ? context.workspacePolicy() : null;
    if (metadata == null) {
            return merged;
        }

    String override = resolveRiskOverride(context, workspacePolicy);

    addWorkspacePolicyFindings(merged, context, metadata, workspacePolicy, override);

    if (!"allow".equalsIgnoreCase(override) && shouldAddUserApprovalFinding(context, metadata)) {
            merged.add(new GuardFinding(
                    "POLICY_USER_APPROVAL_REQUIRED",
                    policySeverity(metadata),
                    policyCategory(metadata),
                    "普通用户敏感工具默认需审批",
                    buildApprovalDescription(context, metadata),
                    buildApprovalRemediation(metadata),
                    context.toolName(),
                    null,
                    "tool_metadata_policy",
                    metadata.toolName(),
                    buildPolicyMetadata(context, metadata)
            ));
        }

        return merged;
    }

    /**
     * 根据 findings 和上下文产出最终裁决
     * <p>
     * 策略（findings-driven approval）：
     * <ul>
     *   <li>无 findings → ALLOW（普通命令直接执行）</li>
     *   <li>CRITICAL → BLOCK（极端危险直接阻断）</li>
     *   <li>HIGH → NEEDS_APPROVAL（高风险需审批）</li>
     *   <li>MEDIUM → NEEDS_APPROVAL（中风险需审批）</li>
     * </ul>
     */
    public GuardDecision resolve(List<GuardFinding> findings, ToolInvocationContext context) {
        // 无 findings → 直接允许（不再按工具类型默认审批）
        if (findings == null || findings.isEmpty()) {
            return GuardDecision.ALLOW;
        }

        GuardSeverity maxSeverity = findings.stream()
                .map(GuardFinding::severity)
                .reduce(GuardSeverity.INFO, GuardSeverity::max);

        // CRITICAL → 直接 BLOCK
        if (maxSeverity.isAtLeast(GuardSeverity.CRITICAL)) {
            return GuardDecision.BLOCK;
        }

        // HIGH / MEDIUM → 需要审批
        if (maxSeverity.isAtLeast(GuardSeverity.MEDIUM)) {
            return GuardDecision.NEEDS_APPROVAL;
        }

        // LOW / INFO → 允许
        return GuardDecision.ALLOW;
    }

    /**
     * 构建人类可读的摘要
     */
    public String buildSummary(List<GuardFinding> findings, GuardDecision decision) {
        if (findings == null || findings.isEmpty()) {
            // 无 findings 时不应该有 NEEDS_APPROVAL 或 BLOCK
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("检测到 ").append(findings.size()).append(" 项安全风险");

        // 列出最高风险的发现
        findings.stream()
                .filter(f -> f.severity() != null && f.severity().isAtLeast(GuardSeverity.MEDIUM))
                .limit(3)
                .forEach(f -> sb.append("\n- [").append(f.severity().name()).append("] ").append(f.title()));

        if (findings.size() > 3) {
            sb.append("\n- ... 及其他 ").append(findings.size() - 3).append(" 项");
        }

        return sb.toString();
    }

    private boolean shouldAddUserApprovalFinding(ToolInvocationContext context, ToolRuntimeMetadata metadata) {
        if (context == null || metadata == null) {
            return false;
        }
        if (!isStrictUser(context.accountRole())) {
            return false;
        }
        if ("full".equalsIgnoreCase(context.workspacePolicyMode())) {
            return false;
        }
        if (metadata.readOnly()) {
            return false;
        }

        String toolName = context.toolName() != null ? context.toolName() : "";
        if ("execute_shell_command".equals(toolName) || toolName.startsWith("shell_") || toolName.startsWith("run_command")) {
            return true;
        }
        if (toolName.contains("cron")) {
            return true;
        }
        if ("write_file".equals(toolName) || "edit_file".equals(toolName)) {
            return true;
        }
        return metadata.externalMutation();
    }

    private void addWorkspacePolicyFindings(List<GuardFinding> merged,
                                            ToolInvocationContext context,
                                            ToolRuntimeMetadata metadata,
                                            WorkspacePolicy workspacePolicy,
                                            String override) {
        if (context == null || metadata == null || workspacePolicy == null) {
            addRiskOverrideFinding(merged, context, metadata, override);
            return;
        }

        if (WorkspacePolicy.SANDBOX_READ_ONLY.equalsIgnoreCase(workspacePolicy.getSandboxMode())
                && metadata.mutatesState()) {
            merged.add(new GuardFinding(
                    "WORKSPACE_SANDBOX_READ_ONLY",
                    GuardSeverity.CRITICAL,
                    GuardCategory.PRIVILEGE_ESCALATION,
                    "当前工作区为只读沙箱",
                    "workspace policy.sandboxMode=read-only，当前工具不允许执行写入或状态变更操作",
                    "切换到允许写入的 workspace policy，或改用只读工具。",
                    context.toolName(),
                    null,
                    "sandboxMode",
                    context.toolName(),
                    buildWorkspacePolicyMetadata(context, metadata)
            ));
        }

        addPathBoundaryFindings(merged, context, metadata, workspacePolicy);
        addNetworkPolicyFindings(merged, context, metadata, workspacePolicy);

        if (WorkspacePolicy.APPROVAL_STRICT.equalsIgnoreCase(workspacePolicy.getApprovalPolicy())
                && metadata.mutatesState()
                && !"allow".equalsIgnoreCase(override)) {
            merged.add(new GuardFinding(
                    "WORKSPACE_APPROVAL_STRICT",
                    GuardSeverity.MEDIUM,
                    GuardCategory.PRIVILEGE_ESCALATION,
                    "工作区启用了严格审批",
                    "workspace policy.approvalPolicy=strict，所有有状态变更工具默认需要审批",
                    "确认变更后继续，或将工作区审批策略调整为 default。",
                    context.toolName(),
                    null,
                    "approvalPolicy",
                    context.toolName(),
                    buildWorkspacePolicyMetadata(context, metadata)
            ));
        }

        addRiskOverrideFinding(merged, context, metadata, override);
    }

    private void addPathBoundaryFindings(List<GuardFinding> merged,
                                         ToolInvocationContext context,
                                         ToolRuntimeMetadata metadata,
                                         WorkspacePolicy workspacePolicy) {
        Optional<Path> candidatePath = extractTargetPath(context);
        if (candidatePath.isEmpty()) {
            return;
        }

        Path candidate = candidatePath.get();
        for (String deniedPath : safeList(workspacePolicy.getDeniedPaths())) {
            Path policyPath = normalizePolicyPath(deniedPath, context.workspaceBasePath());
            if (policyPath != null && candidate.startsWith(policyPath)) {
                merged.add(new GuardFinding(
                        "WORKSPACE_DENIED_PATH",
                        GuardSeverity.CRITICAL,
                        GuardCategory.PATH_TRAVERSAL,
                        "命中了工作区拒绝路径",
                        "目标路径位于 workspace policy.deniedPaths 中，已被工作区策略拒绝",
                        "调整输出路径，或由管理员修改工作区 deniedPaths。",
                        context.toolName(),
                        "path",
                        deniedPath,
                        candidate.toString(),
                        buildWorkspacePolicyMetadata(context, metadata)
                ));
                return;
            }
        }

        List<String> allowedPaths = safeList(workspacePolicy.getAllowedPaths());
        if (!allowedPaths.isEmpty()) {
            boolean matched = allowedPaths.stream()
                    .map(path -> normalizePolicyPath(path, context.workspaceBasePath()))
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(candidate::startsWith);
            if (!matched) {
                merged.add(new GuardFinding(
                        "WORKSPACE_ALLOWED_PATH_MISS",
                        metadata.mutatesState() ? GuardSeverity.HIGH : GuardSeverity.MEDIUM,
                        GuardCategory.PATH_TRAVERSAL,
                        "目标路径不在允许范围内",
                        "目标路径未命中 workspace policy.allowedPaths，当前工作区限制了可访问目录",
                        "将路径调整到 allowedPaths 下，或由管理员扩展工作区允许路径。",
                        context.toolName(),
                        "path",
                        String.join(", ", allowedPaths),
                        candidate.toString(),
                        buildWorkspacePolicyMetadata(context, metadata)
                ));
            }
        }
    }

    private void addNetworkPolicyFindings(List<GuardFinding> merged,
                                          ToolInvocationContext context,
                                          ToolRuntimeMetadata metadata,
                                          WorkspacePolicy workspacePolicy) {
        String networkPolicy = workspacePolicy.getNetworkPolicy();
        if (WorkspacePolicy.NETWORK_INHERIT.equalsIgnoreCase(networkPolicy)) {
            return;
        }

        boolean externalNetworkAttempt = isNetworkSensitiveInvocation(context, metadata);
        if (!externalNetworkAttempt) {
            return;
        }

        GuardSeverity severity = WorkspacePolicy.NETWORK_DISABLED.equalsIgnoreCase(networkPolicy)
                ? GuardSeverity.CRITICAL
                : GuardSeverity.HIGH;
        merged.add(new GuardFinding(
                "WORKSPACE_NETWORK_POLICY",
                severity,
                GuardCategory.NETWORK_ABUSE,
                "工作区限制了网络访问",
                "当前工具调用命中了 workspace policy.networkPolicy，网络访问需要额外限制",
                WorkspacePolicy.NETWORK_DISABLED.equalsIgnoreCase(networkPolicy)
                        ? "请改用离线工作流，或由管理员放宽 networkPolicy。"
                        : "请确认该网络访问属于允许范围后继续。",
                context.toolName(),
                null,
                networkPolicy,
                context.rawArguments(),
                buildWorkspacePolicyMetadata(context, metadata)
        ));
    }

    private void addRiskOverrideFinding(List<GuardFinding> merged,
                                        ToolInvocationContext context,
                                        ToolRuntimeMetadata metadata,
                                        String override) {
        if (override == null || override.isBlank() || "allow".equalsIgnoreCase(override)) {
            return;
        }

        GuardSeverity severity = switch (override.toLowerCase(Locale.ROOT)) {
            case "block", "critical" -> GuardSeverity.CRITICAL;
            case "approve", "high" -> GuardSeverity.HIGH;
            case "medium" -> GuardSeverity.MEDIUM;
            case "low" -> GuardSeverity.LOW;
            default -> null;
        };
        if (severity == null) {
            return;
        }

        merged.add(new GuardFinding(
                "WORKSPACE_RISK_OVERRIDE",
                severity,
                policyCategory(metadata),
                "工作区风险覆盖已生效",
                "workspace policy.riskOverrides 为当前工具声明了附加风险等级：" + override,
                "如需调整，请修改当前工作区的 riskOverrides。",
                context != null ? context.toolName() : null,
                null,
                override,
                context != null ? context.toolName() : null,
                buildWorkspacePolicyMetadata(context, metadata)
        ));
    }

    private String resolveRiskOverride(ToolInvocationContext context, WorkspacePolicy workspacePolicy) {
        if (context == null || workspacePolicy == null || workspacePolicy.getRiskOverrides() == null) {
            return null;
        }
        return workspacePolicy.getRiskOverrides().get(context.toolName());
    }

    private boolean isNetworkSensitiveInvocation(ToolInvocationContext context, ToolRuntimeMetadata metadata) {
        String toolName = context.toolName() != null ? context.toolName() : "";
        if (NETWORK_TOOL_NAMES.contains(toolName) || toolName.startsWith("browser_")) {
            return true;
        }
        if (SHELL_TOOL_NAMES.contains(toolName)) {
            String command = extractCommand(context.rawArguments());
            return command != null && NETWORK_COMMAND_PATTERN.matcher(command).find();
        }
        return metadata.externalMutation();
    }

    private Optional<Path> extractTargetPath(ToolInvocationContext context) {
        if (context == null || context.rawArguments() == null || context.rawArguments().isBlank()) {
            return Optional.empty();
        }
        String toolName = context.toolName() != null ? context.toolName() : "";
        if (!FILE_TOOL_NAMES.contains(toolName) && !context.rawArguments().contains("filePath")) {
            return Optional.empty();
        }
        Map<String, Object> params = parseArguments(context.rawArguments());
        for (String key : FILE_PATH_KEYS) {
            Object value = params.get(key);
            if (value instanceof String pathText && !pathText.isBlank()) {
                Path normalized = normalizeCandidatePath(pathText, context.workspaceBasePath());
                if (normalized != null) {
                    return Optional.of(normalized);
                }
            }
        }
        return Optional.empty();
    }

    private String extractCommand(String rawArguments) {
        Map<String, Object> params = parseArguments(rawArguments);
        Object command = params.get("command");
        return command instanceof String text && !text.isBlank() ? text : rawArguments;
    }

    private Map<String, Object> parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(rawArguments, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Path normalizeCandidatePath(String rawPath, String workspaceBasePath) {
        try {
            Path path = Paths.get(rawPath);
            if (!path.isAbsolute() && workspaceBasePath != null && !workspaceBasePath.isBlank()) {
                path = Paths.get(workspaceBasePath).resolve(path);
            }
            return path.toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path normalizePolicyPath(String rawPath, String workspaceBasePath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        return normalizeCandidatePath(rawPath, workspaceBasePath);
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    private boolean isStrictUser(String accountRole) {
        if (accountRole == null || accountRole.isBlank()) {
            return true;
        }
        return !"admin".equalsIgnoreCase(accountRole) && !"system".equalsIgnoreCase(accountRole);
    }

    private GuardSeverity policySeverity(ToolRuntimeMetadata metadata) {
        if (metadata == null) {
            return GuardSeverity.MEDIUM;
        }
        if (metadata.riskLevel() != null && metadata.riskLevel().isAtLeast(GuardSeverity.HIGH)) {
            return GuardSeverity.HIGH;
        }
        return GuardSeverity.MEDIUM;
    }

    private GuardCategory policyCategory(ToolRuntimeMetadata metadata) {
        if (metadata != null && metadata.sourceType() != null
                && metadata.sourceType() != ToolSourceType.BUILTIN
                && metadata.sourceType() != ToolSourceType.UNKNOWN) {
            return GuardCategory.CODE_EXECUTION;
        }
        return GuardCategory.PRIVILEGE_ESCALATION;
    }

    private String buildApprovalDescription(ToolInvocationContext context, ToolRuntimeMetadata metadata) {
        StringBuilder sb = new StringBuilder("账号类型为普通用户，工具 ")
                .append(context.toolName())
                .append(" 在当前 workspace policy 下默认需要审批");
        if (metadata.sourceType() != null) {
            sb.append("（来源=").append(metadata.sourceType().name()).append("）");
        }
        if (context.workspaceRole() != null && !context.workspaceRole().isBlank()) {
            sb.append("，workspaceRole=").append(context.workspaceRole());
        }
        return sb.toString();
    }

    private String buildApprovalRemediation(ToolRuntimeMetadata metadata) {
        if (metadata == null) {
            return "请由管理员执行，或在获得审批后继续。";
        }
        if (metadata.readOnly()) {
            return "优先使用只读工具完成同类任务。";
        }
        if (metadata.externalMutation()) {
            return "请确认外部系统写入范围，必要时由管理员代为执行。";
        }
        return "请确认变更范围，必要时切换为管理员账号或提升 workspace policy。";
    }

    private java.util.Map<String, Object> buildPolicyMetadata(ToolInvocationContext context,
                                                              ToolRuntimeMetadata metadata) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("accountRole", context.accountRole());
        payload.put("workspaceRole", context.workspaceRole());
        payload.put("workspaceId", context.workspaceId());
        payload.put("workspaceBasePath", context.workspaceBasePath());
        payload.put("workspacePolicyMode", context.workspacePolicyMode());
        payload.put("toolMetadata", metadata.toMap());
        return payload;
    }

    private Map<String, Object> buildWorkspacePolicyMetadata(ToolInvocationContext context,
                                                             ToolRuntimeMetadata metadata) {
        Map<String, Object> payload = new LinkedHashMap<>(buildPolicyMetadata(context, metadata));
        WorkspacePolicy workspacePolicy = context != null ? context.workspacePolicy() : null;
        if (workspacePolicy != null) {
            payload.put("workspacePolicy", Map.of(
                    "sandboxMode", workspacePolicy.getSandboxMode(),
                    "approvalPolicy", workspacePolicy.getApprovalPolicy(),
                    "networkPolicy", workspacePolicy.getNetworkPolicy(),
                    "allowedPaths", safeList(workspacePolicy.getAllowedPaths()),
                    "deniedPaths", safeList(workspacePolicy.getDeniedPaths()),
                    "riskOverrides", workspacePolicy.getRiskOverrides() != null ? workspacePolicy.getRiskOverrides() : Map.of()
            ));
        }
        return payload;
    }
}
