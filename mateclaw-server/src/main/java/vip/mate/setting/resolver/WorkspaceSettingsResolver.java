package vip.mate.setting.resolver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.setting.contract.EffectiveSettingsContract;
import vip.mate.setting.contract.SettingFieldClass;
import vip.mate.setting.contract.SettingMergeStrategy;
import vip.mate.setting.contract.SettingSource;
import vip.mate.workspace.core.model.WorkspacePolicy;
import vip.mate.workspace.core.service.WorkspaceService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WP-1 resolver that extracts and normalizes workspace-level settings from
 * {@code WorkspaceEntity.settingsJson} without changing the existing storage format.
 *
 * <p>This component provides a <em>unified read-only view</em> over the workspace
 * settings surface. It reuses the same merge and normalization rules already
 * present in {@link WorkspaceService}, but exposes them through the canonical
 * {@link EffectiveSettingsContract} so that later packages (WP-2 through WP-6)
 * can query workspace configuration without depending on {@code WorkspaceService}
 * internals.
 *
 * <p>Hard constraints:
 * <ul>
 *   <li>Does NOT alter {@code WorkspaceEntity} schema.</li>
 *   <li>Does NOT change {@code WorkspaceService} behavior.</li>
 *   <li>Treats {@code projectPermissionMode} and {@code workspacePolicy} as
 *       {@link SettingFieldClass#SAFETY_POLICY} fields to surface the mixed
 *       semantics explicitly (WP-0 blocker A01).</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see SystemSettingsResolver
 * @see TemplateSettingsResolver
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceSettingsResolver {

    private static final String SETTINGS_KEY_PROJECT_PERMISSION_MODE = "projectPermissionMode";
    private static final String SETTINGS_KEY_WORKSPACE_POLICY = "workspacePolicy";

    private final ObjectMapper objectMapper;

    /**
     * Parse a settings JSON blob into a flat map of {@link EffectiveSettingsContract.ResolutionResult}
     * entries, classifying known policy keys explicitly.
     *
     * @param settingsJson the raw {@code WorkspaceEntity.settingsJson} value; may be null or blank
     * @param workspaceId  the workspace id for provenance (diagnostics only)
     * @return a read-only map of resolved entries; empty if input is null/blank
     */
    public Map<String, EffectiveSettingsContract.ResolutionResult<?>> resolveWorkspaceSettingsJson(
            String settingsJson, Long workspaceId) {

        Map<String, Object> raw = readSettingsMap(settingsJson);
        if (raw.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, EffectiveSettingsContract.ResolutionResult<?>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            SettingFieldClass fieldClass = classifyField(key);
            SettingMergeStrategy strategy = inferMergeStrategy(key);
            String stringValue = value != null ? String.valueOf(value) : null;

            result.put(key, new EffectiveSettingsContract.ResolutionResult<>(
                    stringValue,
                    SettingSource.WORKSPACE_SHARED_SETTINGS,
                    fieldClass,
                    strategy,
                    "workspaceId=" + workspaceId));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Extract the normalized workspace policy from a settings JSON blob.
     * This mirrors the logic in {@code WorkspaceService.extractWorkspacePolicy()}
     * but returns the result together with classification metadata.
     */
    public EffectiveSettingsContract.ResolutionResult<WorkspacePolicy> resolveWorkspacePolicy(
            String settingsJson, Long workspaceId) {

        Map<String, Object> raw = readSettingsMap(settingsJson);
        Object value = raw.get(SETTINGS_KEY_WORKSPACE_POLICY);
        WorkspacePolicy policy = null;
        if (value != null) {
            try {
                policy = objectMapper.convertValue(value, WorkspacePolicy.class);
            } catch (IllegalArgumentException e) {
                log.warn("[WP-1] Failed to parse workspace policy for workspace {}: {}", workspaceId, e.getMessage());
            }
        }
        return new EffectiveSettingsContract.ResolutionResult<>(
                policy,
                SettingSource.WORKSPACE_SHARED_SETTINGS,
                SettingFieldClass.SAFETY_POLICY,
                SettingMergeStrategy.RESTRICTIVE_MERGE,
                "workspaceId=" + workspaceId);
    }

    /**
     * Extract the project permission mode from a settings JSON blob.
     */
    public EffectiveSettingsContract.ResolutionResult<String> resolveProjectPermissionMode(
            String settingsJson, Long workspaceId) {

        Map<String, Object> raw = readSettingsMap(settingsJson);
        Object value = raw.get(SETTINGS_KEY_PROJECT_PERMISSION_MODE);
        String mode = value != null ? String.valueOf(value) : null;
        return new EffectiveSettingsContract.ResolutionResult<>(
                mode,
                SettingSource.WORKSPACE_SHARED_SETTINGS,
                SettingFieldClass.SAFETY_POLICY,
                SettingMergeStrategy.RESTRICTIVE_MERGE,
                "workspaceId=" + workspaceId);
    }

    private Map<String, Object> readSettingsMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[WP-1] Failed to parse workspace settings JSON: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private SettingFieldClass classifyField(String key) {
        return switch (key) {
            case SETTINGS_KEY_PROJECT_PERMISSION_MODE, SETTINGS_KEY_WORKSPACE_POLICY ->
                    SettingFieldClass.SAFETY_POLICY;
            default -> SettingFieldClass.PRODUCT_SETTING;
        };
    }

    private SettingMergeStrategy inferMergeStrategy(String key) {
        return switch (key) {
            case SETTINGS_KEY_PROJECT_PERMISSION_MODE, SETTINGS_KEY_WORKSPACE_POLICY ->
                    SettingMergeStrategy.RESTRICTIVE_MERGE;
            default -> SettingMergeStrategy.SCALAR_OVERRIDE;
        };
    }
}
