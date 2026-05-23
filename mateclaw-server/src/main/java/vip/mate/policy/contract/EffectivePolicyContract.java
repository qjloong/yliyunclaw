package vip.mate.policy.contract;

import vip.mate.workspace.core.model.WorkspacePolicy;

/**
 * Read-only contract for resolving the effective policy for a given execution scope (WP-2).
 *
 * <p>This contract is the policy-layer counterpart to
 * {@link vip.mate.setting.contract.EffectiveSettingsContract}. It answers:
 * <ul>
 *   <li>What is the effective sandbox mode for workspace X?</li>
 *   <li>Can template Y tighten the workspace approval policy?</li>
 *   <li>Which scope provided the final network policy value?</li>
 * </ul>
 *
 * <p>Implementation constraints:
 * <ul>
 *   <li>All methods are side-effect-free.</li>
 *   <li>Template defaults must use {@link PolicyApplicationMode#TIGHTEN} semantics
 *       so they cannot silently weaken stronger workspace or global policy.</li>
 *   <li>The contract does not trigger approvals; it only produces the policy snapshot
 *       that the guard engine and approval pipeline consume.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see vip.mate.setting.contract.EffectiveSettingsContract
 */
public interface EffectivePolicyContract {

    /**
     * Resolve the complete effective policy for a workspace, optionally considering
     * a template's default policy and runtime overrides.
     *
     * @param workspaceId   the workspace id
     * @param templateId    optional built-in template id for template-default overlay
     * @param runtimePolicy optional runtime-level override (e.g., emergency stop)
     * @return the effective policy together with provenance metadata
     */
    PolicyResolutionResult resolveEffectivePolicy(Long workspaceId, String templateId,
                                                   WorkspacePolicy runtimePolicy);

    /**
     * Result of a policy resolution, carrying both the merged policy and per-dimension
     * provenance so that diagnostics can explain "why is the sandbox mode read-only?"
     */
    record PolicyResolutionResult(WorkspacePolicy effectivePolicy,
                                   java.util.Map<PolicyDimension, DimensionProvenance> provenance) {

        public PolicyResolutionResult {
            provenance = provenance != null
                    ? java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(provenance))
                    : java.util.Collections.emptyMap();
        }
    }

    /**
     * Per-dimension provenance: which scope won, what mode was applied, and what the
     * losing values were.
     */
    record DimensionProvenance(PolicyDimension dimension,
                                PolicyScope winningScope,
                                PolicyApplicationMode appliedMode,
                                String winningValue,
                                java.util.List<String> losingValues) {
    }
}
