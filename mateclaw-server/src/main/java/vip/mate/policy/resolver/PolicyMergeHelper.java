package vip.mate.policy.resolver;

import vip.mate.policy.contract.PolicyApplicationMode;
import vip.mate.policy.contract.PolicyDimension;
import vip.mate.policy.contract.PolicyScope;
import vip.mate.policy.contract.EffectivePolicyContract;
import vip.mate.workspace.core.model.WorkspacePolicy;

import java.util.*;

/**
 * WP-2 helper that centralizes the restrictive-merge logic previously embedded in
 * {@link vip.mate.agent.graph.executor.ToolExecutionExecutor#mergeTemplatePolicy(WorkspacePolicy, String, boolean)}.
 *
 * <p>Each dimension has deterministic merge semantics:
 * <ul>
 *   <li>{@code SANDBOX} — rank-based: read-only (3) > workspace-write (2) > full-access (1)</li>
 *   <li>{@code APPROVAL} — strict wins over default</li>
 *   <li>{@code NETWORK} — rank-based: disabled (3) > restricted (2) > inherit (1)</li>
 *   <li>{@code ALLOWED_PATHS} — additive merge (union of both lists)</li>
 *   <li>{@code DENIED_PATHS} — additive merge (union of both lists)</li>
 *   <li>{@code RISK_OVERRIDES} — workspace values win on key collision; template values fill gaps</li>
 * </ul>
 *
 * <p>Hard constraint for template defaults:
 * <ul>
 *   <li>Template scope always uses {@link PolicyApplicationMode#TIGHTEN}.</li>
 *   <li>If a template value is less restrictive than the workspace value, the workspace value wins.</li>
 *   <li>This prevents built-in templates from silently weakening a user's workspace policy.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see EffectivePolicyResolver
 */
public final class PolicyMergeHelper {

    private PolicyMergeHelper() {
        // utility class
    }

    /**
     * Merge two policies with explicit tightening semantics.
     *
     * @param base       the higher-authority policy (e.g., workspace)
     * @param overlay    the lower-authority policy (e.g., template defaults)
     * @param overlayScope the scope of the overlay (determines application mode)
     * @return the merged policy and per-dimension provenance
     */
    public static EffectivePolicyContract.PolicyResolutionResult merge(
            WorkspacePolicy base,
            WorkspacePolicy overlay,
            PolicyScope overlayScope) {

        if (base == null && overlay == null) {
            return new EffectivePolicyContract.PolicyResolutionResult(
                    normalizePolicy(null), Collections.emptyMap());
        }
        if (base == null) {
            base = normalizePolicy(null);
        }
        if (overlay == null) {
            return new EffectivePolicyContract.PolicyResolutionResult(
                    base, Collections.emptyMap());
        }

        PolicyApplicationMode mode = (overlayScope == PolicyScope.TEMPLATE)
                ? PolicyApplicationMode.TIGHTEN
                : PolicyApplicationMode.CONSTRAIN;

        WorkspacePolicy merged = new WorkspacePolicy();
        Map<PolicyDimension, EffectivePolicyContract.DimensionProvenance> provenance = new LinkedHashMap<>();

        // SANDBOX
        String sandbox = mergeSandbox(base.getSandboxMode(), overlay.getSandboxMode(), mode);
        merged.setSandboxMode(sandbox);
        provenance.put(PolicyDimension.SANDBOX, new EffectivePolicyContract.DimensionProvenance(
                PolicyDimension.SANDBOX,
                sourceOf(sandbox, base.getSandboxMode(), overlay.getSandboxMode()),
                mode, sandbox, List.of(base.getSandboxMode(), overlay.getSandboxMode())));

        // APPROVAL
        String approval = mergeApproval(base.getApprovalPolicy(), overlay.getApprovalPolicy(), mode);
        merged.setApprovalPolicy(approval);
        provenance.put(PolicyDimension.APPROVAL, new EffectivePolicyContract.DimensionProvenance(
                PolicyDimension.APPROVAL,
                sourceOf(approval, base.getApprovalPolicy(), overlay.getApprovalPolicy()),
                mode, approval, List.of(base.getApprovalPolicy(), overlay.getApprovalPolicy())));

        // NETWORK
        String network = mergeNetwork(base.getNetworkPolicy(), overlay.getNetworkPolicy(), mode);
        merged.setNetworkPolicy(network);
        provenance.put(PolicyDimension.NETWORK, new EffectivePolicyContract.DimensionProvenance(
                PolicyDimension.NETWORK,
                sourceOf(network, base.getNetworkPolicy(), overlay.getNetworkPolicy()),
                mode, network, List.of(base.getNetworkPolicy(), overlay.getNetworkPolicy())));

        // ALLOWED_PATHS
        List<String> allowedPaths = mergeAllowedPaths(base.getAllowedPaths(), overlay.getAllowedPaths());
        merged.setAllowedPaths(allowedPaths);
        provenance.put(PolicyDimension.ALLOWED_PATHS, new EffectivePolicyContract.DimensionProvenance(
                PolicyDimension.ALLOWED_PATHS, PolicyScope.WORKSPACE, mode,
                String.valueOf(allowedPaths), List.of(String.valueOf(base.getAllowedPaths()), String.valueOf(overlay.getAllowedPaths()))));

        // DENIED_PATHS
        List<String> deniedPaths = unionLists(base.getDeniedPaths(), overlay.getDeniedPaths());
        merged.setDeniedPaths(deniedPaths);
        provenance.put(PolicyDimension.DENIED_PATHS, new EffectivePolicyContract.DimensionProvenance(
                PolicyDimension.DENIED_PATHS, PolicyScope.WORKSPACE, mode,
                String.valueOf(deniedPaths), List.of(String.valueOf(base.getDeniedPaths()), String.valueOf(overlay.getDeniedPaths()))));

        // RISK_OVERRIDES
        Map<String, String> riskOverrides = mergeRiskOverrides(base.getRiskOverrides(), overlay.getRiskOverrides());
        merged.setRiskOverrides(riskOverrides);
        provenance.put(PolicyDimension.RISK_OVERRIDES, new EffectivePolicyContract.DimensionProvenance(
                PolicyDimension.RISK_OVERRIDES, PolicyScope.WORKSPACE, mode,
                String.valueOf(riskOverrides), List.of(String.valueOf(base.getRiskOverrides()), String.valueOf(overlay.getRiskOverrides()))));

        return new EffectivePolicyContract.PolicyResolutionResult(merged, provenance);
    }

    // ==================== Sandbox ====================

    public static String mergeSandbox(String base, String overlay, PolicyApplicationMode mode) {
        if (mode == PolicyApplicationMode.TIGHTEN) {
            int baseRank = sandboxRank(base);
            int overlayRank = sandboxRank(overlay);
            return (overlayRank > baseRank) ? overlay : base;
        }
        return moreRestrictiveSandbox(base, overlay);
    }

    public static String moreRestrictiveSandbox(String left, String right) {
        int leftRank = sandboxRank(left);
        int rightRank = sandboxRank(right);
        return leftRank >= rightRank ? left : right;
    }

    private static int sandboxRank(String value) {
        if (WorkspacePolicy.SANDBOX_READ_ONLY.equalsIgnoreCase(value)) return 3;
        if (WorkspacePolicy.SANDBOX_WORKSPACE_WRITE.equalsIgnoreCase(value)) return 2;
        if (WorkspacePolicy.SANDBOX_FULL_ACCESS.equalsIgnoreCase(value)) return 1;
        return 0;
    }

    // ==================== Approval ====================

    public static String mergeApproval(String base, String overlay, PolicyApplicationMode mode) {
        if (mode == PolicyApplicationMode.TIGHTEN) {
            boolean overlayStrict = WorkspacePolicy.APPROVAL_STRICT.equalsIgnoreCase(overlay);
            boolean baseStrict = WorkspacePolicy.APPROVAL_STRICT.equalsIgnoreCase(base);
            return overlayStrict ? overlay : base;
        }
        return moreRestrictiveApproval(base, overlay);
    }

    public static String moreRestrictiveApproval(String left, String right) {
        if (WorkspacePolicy.APPROVAL_STRICT.equalsIgnoreCase(left)
                || WorkspacePolicy.APPROVAL_STRICT.equalsIgnoreCase(right)) {
            return WorkspacePolicy.APPROVAL_STRICT;
        }
        return left != null ? left : right;
    }

    // ==================== Network ====================

    public static String mergeNetwork(String base, String overlay, PolicyApplicationMode mode) {
        if (mode == PolicyApplicationMode.TIGHTEN) {
            int baseRank = networkRank(base);
            int overlayRank = networkRank(overlay);
            return (overlayRank > baseRank) ? overlay : base;
        }
        return moreRestrictiveNetwork(base, overlay);
    }

    public static String moreRestrictiveNetwork(String left, String right) {
        int leftRank = networkRank(left);
        int rightRank = networkRank(right);
        return leftRank >= rightRank ? left : right;
    }

    private static int networkRank(String value) {
        if (WorkspacePolicy.NETWORK_DISABLED.equalsIgnoreCase(value)) return 3;
        if (WorkspacePolicy.NETWORK_RESTRICTED.equalsIgnoreCase(value)) return 2;
        if (WorkspacePolicy.NETWORK_INHERIT.equalsIgnoreCase(value)) return 1;
        return 0;
    }

    // ==================== Paths ====================

    public static List<String> mergeAllowedPaths(List<String> base, List<String> overlay) {
        return unionLists(base, overlay);
    }

    public static List<String> unionLists(List<String> base, List<String> overlay) {
        Set<String> union = new LinkedHashSet<>();
        if (base != null) union.addAll(base);
        if (overlay != null) union.addAll(overlay);
        return new ArrayList<>(union);
    }

    // ==================== Risk Overrides ====================

    public static Map<String, String> mergeRiskOverrides(Map<String, String> base, Map<String, String> overlay) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (base != null) merged.putAll(base);
        if (overlay != null) {
            for (Map.Entry<String, String> entry : overlay.entrySet()) {
                // Overlay fills gaps only; base wins on key collision
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    // ==================== Normalization ====================

    public static WorkspacePolicy normalizePolicy(WorkspacePolicy policy) {
        WorkspacePolicy normalized = new WorkspacePolicy();
        if (policy != null) {
            normalized.setSandboxMode(policy.getSandboxMode());
            normalized.setApprovalPolicy(policy.getApprovalPolicy());
            normalized.setNetworkPolicy(policy.getNetworkPolicy());
            normalized.setAllowedPaths(policy.getAllowedPaths());
            normalized.setDeniedPaths(policy.getDeniedPaths());
            normalized.setRiskOverrides(policy.getRiskOverrides());
        }
        return normalized;
    }

    // ==================== Provenance ====================

    private static PolicyScope sourceOf(String winner, String base, String overlay) {
        if (winner == null) return PolicyScope.WORKSPACE;
        if (winner.equalsIgnoreCase(base)) return PolicyScope.WORKSPACE;
        if (winner.equalsIgnoreCase(overlay)) return PolicyScope.TEMPLATE;
        return PolicyScope.WORKSPACE;
    }
}
