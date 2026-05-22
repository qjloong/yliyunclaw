package vip.mate.tool.guard.model;

import vip.mate.policy.contract.EffectivePolicyContract;
import vip.mate.policy.contract.PolicyDimension;
import vip.mate.tool.metadata.ToolRuntimeMetadata;
import vip.mate.workspace.core.model.WorkspacePolicy;

import java.util.Map;

/**
 * 工具调用上下文
 * <p>
 * 标准化的工具调用信息，供所有 Guardian 使用。
 * 先标准化上下文，再做风险评估。
 *
 * <p>WP-2 extension: {@code policyProvenance} carries per-dimension merge provenance
 * from {@link vip.mate.policy.resolver.EffectivePolicyResolver}, enabling diagnostic
 * explanations like "sandbox is read-only because the template tightened it".
 */
public record ToolInvocationContext(
        String toolName,
        Map<String, Object> parameters,
        String rawArguments,
        String conversationId,
        String agentId,
        String channelType,
        String userId,
        String accountRole,
        String workspaceRole,
        Long workspaceId,
        String workspaceBasePath,
        String workspacePolicyMode,
        WorkspacePolicy workspacePolicy,
        ToolRuntimeMetadata toolMetadata,
        /** WP-2: per-dimension merge provenance, null when EffectivePolicyResolver is not wired. */
        Map<PolicyDimension, EffectivePolicyContract.DimensionProvenance> policyProvenance
) {

    /**
     * 常用工厂方法 — 从工具名和原始参数创建
     */
    public static ToolInvocationContext of(String toolName, String rawArguments,
                                          String conversationId, String agentId) {
        return new ToolInvocationContext(
                toolName, Map.of(), rawArguments, conversationId, agentId,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    /**
     * 完整工厂方法
     */
    public static ToolInvocationContext of(String toolName, Map<String, Object> parameters,
                                          String rawArguments, String conversationId,
                                          String agentId, String channelType, String userId) {
        return new ToolInvocationContext(
                toolName, parameters != null ? parameters : Map.of(),
                rawArguments, conversationId, agentId, channelType, userId,
                null, null, null, null, null, null, null, null
        );
    }

    public static ToolInvocationContext of(String toolName, Map<String, Object> parameters,
                                          String rawArguments, String conversationId,
                                          String agentId, String channelType, String userId,
                                          String accountRole, String workspaceRole,
                                          Long workspaceId, String workspaceBasePath,
                                          String workspacePolicyMode, WorkspacePolicy workspacePolicy,
                                          ToolRuntimeMetadata toolMetadata,
                                          Map<PolicyDimension, EffectivePolicyContract.DimensionProvenance> policyProvenance) {
        return new ToolInvocationContext(
                toolName,
                parameters != null ? parameters : Map.of(),
                rawArguments,
                conversationId,
                agentId,
                channelType,
                userId,
                accountRole,
                workspaceRole,
                workspaceId,
                workspaceBasePath,
                workspacePolicyMode,
                workspacePolicy,
                toolMetadata,
                policyProvenance
        );
    }
}
