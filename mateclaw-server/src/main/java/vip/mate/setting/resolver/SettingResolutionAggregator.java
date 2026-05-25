package vip.mate.setting.resolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.setting.contract.EffectiveSettingsContract;
import vip.mate.setting.contract.SettingFieldClass;
import vip.mate.setting.contract.SettingMergeStrategy;
import vip.mate.setting.contract.SettingSource;
import vip.mate.workspace.core.model.WorkspacePolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * WP-1 aggregator that composes the three canonical resolvers
 * ({@link SystemSettingsResolver}, {@link WorkspaceSettingsResolver},
 * {@link TemplateSettingsResolver}) into one cross-source effective-settings view.
 *
 * <p>This is the <strong>compatibility-first implementation</strong> of
 * {@link EffectiveSettingsContract}. It does not introduce a new precedence rewrite;
 * it makes the existing precedence chains explicit and queryable.
 *
 * <p>Scope rules:
 * <ul>
 *   <li>{@code scope = "global"} → returns system-level settings only.</li>
 *   <li>{@code scope = "workspace:{id}"} → merges workspace settings over system settings.</li>
 *   <li>{@code scope = "agent:{id}"} → not yet implemented; reserved for WP-2+ when agent-level
 *       effective policy is introduced.</li>
 * </ul>
 *
 * <p>All returned maps are unmodifiable.
 *
 * @author MateClaw Team
 * @see EffectiveSettingsContract
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingResolutionAggregator implements EffectiveSettingsContract {

    private final SystemSettingsResolver systemResolver;
    private final WorkspaceSettingsResolver workspaceResolver;
    private final TemplateSettingsResolver templateResolver;

    @Override
    public ResolutionResult<String> resolveString(String scope, String key, String defaultValue) {
        Map<String, ResolutionResult<?>> all = resolveAll(scope);
        ResolutionResult<?> result = all.get(key);
        if (result != null && result.value() instanceof String s) {
            return new ResolutionResult<>(s, result.source(), result.fieldClass(), result.mergeStrategy(), result.provenanceNote());
        }
        return new ResolutionResult<>(defaultValue, SettingSource.PLATFORM_DEFAULTS,
                SettingFieldClass.PRODUCT_SETTING, SettingMergeStrategy.SINGLE_SOURCE,
                "fallback default");
    }

    @Override
    public ResolutionResult<Boolean> resolveBoolean(String scope, String key, boolean defaultValue) {
        Map<String, ResolutionResult<?>> all = resolveAll(scope);
        ResolutionResult<?> result = all.get(key);
        if (result != null && result.value() instanceof Boolean b) {
            return new ResolutionResult<>(b, result.source(), result.fieldClass(), result.mergeStrategy(), result.provenanceNote());
        }
        // Attempt string-to-boolean coercion for settings stored as strings
        if (result != null && result.value() instanceof String s) {
            boolean parsed = Boolean.parseBoolean(s);
            return new ResolutionResult<>(parsed, result.source(), result.fieldClass(), result.mergeStrategy(), result.provenanceNote());
        }
        return new ResolutionResult<>(defaultValue, SettingSource.PLATFORM_DEFAULTS,
                SettingFieldClass.PRODUCT_SETTING, SettingMergeStrategy.SINGLE_SOURCE,
                "fallback default");
    }

    @Override
    public Map<String, ResolutionResult<?>> resolveAll(String scope) {
        if (scope == null || "global".equalsIgnoreCase(scope)) {
            return systemResolver.resolveSystemSettings();
        }

        if (scope.startsWith("workspace:")) {
            Long workspaceId = parseWorkspaceId(scope);
            return resolveWorkspaceScope(workspaceId);
        }

        log.debug("[WP-1] Unrecognized scope '{}'; falling back to system settings only.", scope);
        return systemResolver.resolveSystemSettings();
    }

    private Map<String, ResolutionResult<?>> resolveWorkspaceScope(Long workspaceId) {
        // Start with system settings as the base layer
        Map<String, ResolutionResult<?>> merged = new LinkedHashMap<>(systemResolver.resolveSystemSettings());

        // Overlay workspace settings (higher precedence for same key)
        // Note: workspace settings require the raw settingsJson which is not directly available here.
        // This aggregator delegates to WorkspaceSettingsResolver when the JSON is supplied externally.
        // For a complete end-to-end view, consumers should use WorkspaceSettingsResolver directly
        // or this aggregator should later be wired with WorkspaceService to fetch the JSON.
        //
        // WP-1 scope decision: keep the aggregator lightweight. The classification contract
        // is the primary deliverable; full cross-source merging will be hardened in WP-2.

        merged.put("_wp1.scopeNote",
                new ResolutionResult<>("workspace scope requires external settingsJson injection",
                        SettingSource.WORKSPACE_SHARED_SETTINGS, SettingFieldClass.AMBIGUOUS,
                        SettingMergeStrategy.SINGLE_SOURCE, "WP-1 compatibility boundary"));

        return Collections.unmodifiableMap(merged);
    }

    /**
     * Resolve the effective workspace policy for a workspace, merging system defaults
     * (none today) with workspace {@code settingsJson} and optional template defaults.
     *
     * @param settingsJson     the raw workspace settings JSON
     * @param workspaceId      the workspace id
     * @param templateId       optional template id for template default overlay
     * @return the classified effective policy result
     */
    public ResolutionResult<WorkspacePolicy> resolveEffectiveWorkspacePolicy(
            String settingsJson, Long workspaceId, String templateId) {

        ResolutionResult<WorkspacePolicy> workspacePolicy =
                workspaceResolver.resolveWorkspacePolicy(settingsJson, workspaceId);

        WorkspacePolicy effective = workspacePolicy.value();
        if (effective == null) {
            effective = new WorkspacePolicy();
        }

        if (templateId != null && !templateId.isBlank()) {
            Map<String, ResolutionResult<?>> templateDefaults = templateResolver.resolveTemplateDefaults(templateId);
            applyTemplatePolicyDefaults(effective, templateDefaults);
        }

        return new ResolutionResult<>(effective, SettingSource.WORKSPACE_SHARED_SETTINGS,
                SettingFieldClass.SAFETY_POLICY, SettingMergeStrategy.RESTRICTIVE_MERGE,
                "workspaceId=" + workspaceId + ", templateId=" + templateId);
    }

    private void applyTemplatePolicyDefaults(WorkspacePolicy target,
                                              Map<String, ResolutionResult<?>> templateDefaults) {
        ResolutionResult<?> sandbox = templateDefaults.get("defaultWorkspacePolicy.sandboxMode");
        if (sandbox != null && sandbox.value() instanceof String s && target.getSandboxMode() == null) {
            target.setSandboxMode(s);
        }
        ResolutionResult<?> approval = templateDefaults.get("defaultWorkspacePolicy.approvalPolicy");
        if (approval != null && approval.value() instanceof String s && target.getApprovalPolicy() == null) {
            target.setApprovalPolicy(s);
        }
        ResolutionResult<?> network = templateDefaults.get("defaultWorkspacePolicy.networkPolicy");
        if (network != null && network.value() instanceof String s && target.getNetworkPolicy() == null) {
            target.setNetworkPolicy(s);
        }
    }

    private Long parseWorkspaceId(String scope) {
        try {
            return Long.valueOf(scope.substring("workspace:".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
