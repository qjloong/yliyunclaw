package vip.mate.policy.resolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.policy.contract.EffectivePolicyContract;
import vip.mate.policy.contract.PolicyScope;
import vip.mate.setting.resolver.TemplateSettingsResolver;
import vip.mate.workspace.core.model.WorkspacePolicy;
import vip.mate.workspace.core.service.WorkspaceService;

import java.util.Collections;

/**
 * WP-2 resolver that produces the effective {@link WorkspacePolicy} for a given execution scope
 * by layering workspace policy, template defaults, and optional runtime overrides.
 *
 * <p>This component is the policy-layer counterpart to
 * {@link vip.mate.setting.resolver.SettingResolutionAggregator}. It consumes the canonical
 * {@link PolicyMergeHelper} so that merge semantics are centralized and testable, rather than
 * being duplicated inside {@code ToolExecutionExecutor}.
 *
 * <p>Hard constraints:
 * <ul>
 *   <li>Does NOT alter {@code WorkspacePolicy} schema.</li>
 *   <li>Does NOT change {@code ToolExecutionExecutor} behavior immediately;
 *       the executor may later delegate to this resolver.</li>
 *   <li>Template defaults are applied with {@code TIGHTEN} semantics only.</li>
 *   <li>Runtime overrides (emergency stops) are applied with {@code VETO} semantics.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see PolicyMergeHelper
 * @see EffectivePolicyContract
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EffectivePolicyResolver implements EffectivePolicyContract {

    private final WorkspaceService workspaceService;
    private final TemplateSettingsResolver templateSettingsResolver;

    @Override
    public PolicyResolutionResult resolveEffectivePolicy(Long workspaceId, String templateId,
                                                          WorkspacePolicy runtimePolicy) {
        // Layer 1: Workspace policy (highest persistent authority)
        WorkspacePolicy workspacePolicy = safeResolveWorkspacePolicy(workspaceId);

        // Layer 2: Template defaults (tighten-only)
        WorkspacePolicy templatePolicy = resolveTemplatePolicy(templateId);
        PolicyResolutionResult afterTemplate = PolicyMergeHelper.merge(
                workspacePolicy, templatePolicy, PolicyScope.TEMPLATE);

        // Layer 3: Runtime overrides (veto semantics)
        if (runtimePolicy != null) {
            PolicyResolutionResult afterRuntime = PolicyMergeHelper.merge(
                    afterTemplate.effectivePolicy(), runtimePolicy, PolicyScope.RUNTIME);
            return new PolicyResolutionResult(afterRuntime.effectivePolicy(),
                    afterRuntime.provenance()); // provenance from last merge wins
        }

        return afterTemplate;
    }

    /**
     * Resolve workspace policy safely, returning a normalized empty policy on failure.
     */
    private WorkspacePolicy safeResolveWorkspacePolicy(Long workspaceId) {
        try {
            WorkspacePolicy policy = workspaceService.resolveWorkspacePolicy(workspaceId);
            return policy != null ? policy : PolicyMergeHelper.normalizePolicy(null);
        } catch (Exception e) {
            log.warn("[WP-2] Failed to resolve workspace policy for workspace {}: {}", workspaceId, e.getMessage());
            return PolicyMergeHelper.normalizePolicy(null);
        }
    }

    /**
     * Build a {@link WorkspacePolicy} from template defaults discovered by
     * {@link TemplateSettingsResolver}.
     */
    private WorkspacePolicy resolveTemplatePolicy(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        try {
            var defaults = templateSettingsResolver.resolveTemplateDefaults(templateId);
            WorkspacePolicy policy = new WorkspacePolicy();

            var sandbox = defaults.get("defaultWorkspacePolicy.sandboxMode");
            if (sandbox != null && sandbox.value() instanceof String s) {
                policy.setSandboxMode(s);
            }
            var approval = defaults.get("defaultWorkspacePolicy.approvalPolicy");
            if (approval != null && approval.value() instanceof String s) {
                policy.setApprovalPolicy(s);
            }
            var network = defaults.get("defaultWorkspacePolicy.networkPolicy");
            if (network != null && network.value() instanceof String s) {
                policy.setNetworkPolicy(s);
            }
            return policy;
        } catch (Exception e) {
            log.warn("[WP-2] Failed to resolve template policy for template {}: {}", templateId, e.getMessage());
            return null;
        }
    }
}
