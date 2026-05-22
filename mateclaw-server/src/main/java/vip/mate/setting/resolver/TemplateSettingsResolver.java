package vip.mate.setting.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import vip.mate.setting.contract.EffectiveSettingsContract;
import vip.mate.setting.contract.SettingFieldClass;
import vip.mate.setting.contract.SettingMergeStrategy;
import vip.mate.setting.contract.SettingSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WP-1 resolver that extracts runtime-relevant default values from built-in template
 * JSON files and surfaces them through the canonical {@link EffectiveSettingsContract} model.
 *
 * <p>This component scans classpath templates without changing the template format or
 * the template application logic in {@code TemplateService}. It makes template defaults
 * <em>discoverable</em> as a settings source, addressing WP-0 blocker {@code A03}
 * (distributed effective-config chain).
 *
 * <p>Currently extracted fields:
 * <ul>
 *   <li>{@code runtime.preferredMode} → {@link SettingFieldClass#TEMPLATE_DEFAULT}</li>
 *   <li>{@code defaultWorkspacePolicy.sandboxMode} → {@link SettingFieldClass#SAFETY_POLICY}</li>
 *   <li>{@code defaultWorkspacePolicy.approvalPolicy} → {@link SettingFieldClass#SAFETY_POLICY}</li>
 *   <li>{@code defaultWorkspacePolicy.networkPolicy} → {@link SettingFieldClass#SAFETY_POLICY}</li>
 * </ul>
 *
 * <p>Non-goals:
 * <ul>
 *   <li>Does NOT extract purely descriptive metadata (name, description, icon).</li>
 *   <li>Does NOT extract workspace-file seed content.</li>
 *   <li>Does NOT extract knowledge-base seed declarations.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see WorkspaceSettingsResolver
 * @see SystemSettingsResolver
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateSettingsResolver {

    private static final String TEMPLATES_PATH = "classpath:templates/*.json";

    private final ObjectMapper objectMapper;

    /**
     * Resolve template-provided defaults for a given template id.
     *
     * @param templateId the built-in template id (e.g., "coding-agent")
     * @return a read-only map of classified defaults; empty if template not found or has no runtime defaults
     */
    public Map<String, EffectiveSettingsContract.ResolutionResult<?>> resolveTemplateDefaults(String templateId) {
        JsonNode root = loadTemplateNode(templateId);
        if (root == null || root.isMissingNode()) {
            return Collections.emptyMap();
        }

        Map<String, EffectiveSettingsContract.ResolutionResult<?>> result = new LinkedHashMap<>();

        JsonNode runtime = root.path("runtime");
        if (runtime.isObject()) {
            JsonNode preferredMode = runtime.path("preferredMode");
            if (preferredMode.isTextual()) {
                result.put("runtime.preferredMode",
                        new EffectiveSettingsContract.ResolutionResult<>(
                                preferredMode.asText(),
                                SettingSource.TEMPLATE_DEFAULTS,
                                SettingFieldClass.TEMPLATE_DEFAULT,
                                SettingMergeStrategy.SCALAR_OVERRIDE,
                                "templateId=" + templateId));
            }
        }

        JsonNode policy = root.path("defaultWorkspacePolicy");
        if (policy.isObject()) {
            putPolicyField(result, policy, "sandboxMode", templateId);
            putPolicyField(result, policy, "approvalPolicy", templateId);
            putPolicyField(result, policy, "networkPolicy", templateId);
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Resolve template defaults for all built-in templates as a flat map keyed by
     * {@code templateId.settingKey}.
     */
    public Map<String, EffectiveSettingsContract.ResolutionResult<?>> resolveAllTemplateDefaults() {
        Map<String, EffectiveSettingsContract.ResolutionResult<?>> result = new LinkedHashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(TEMPLATES_PATH);
            for (Resource resource : resources) {
                String templateId = inferTemplateId(resource);
                if (templateId == null) continue;
                for (Map.Entry<String, EffectiveSettingsContract.ResolutionResult<?>> entry
                        : resolveTemplateDefaults(templateId).entrySet()) {
                    result.put(templateId + "." + entry.getKey(), entry.getValue());
                }
            }
        } catch (IOException e) {
            log.warn("[WP-1] Failed to scan template files for defaults: {}", e.getMessage());
        }
        return Collections.unmodifiableMap(result);
    }

    private void putPolicyField(Map<String, EffectiveSettingsContract.ResolutionResult<?>> map,
                                JsonNode policy, String fieldName, String templateId) {
        JsonNode value = policy.path(fieldName);
        if (value.isTextual()) {
            map.put("defaultWorkspacePolicy." + fieldName,
                    new EffectiveSettingsContract.ResolutionResult<>(
                            value.asText(),
                            SettingSource.TEMPLATE_DEFAULTS,
                            SettingFieldClass.SAFETY_POLICY,
                            SettingMergeStrategy.RESTRICTIVE_MERGE,
                            "templateId=" + templateId));
        }
    }

    private JsonNode loadTemplateNode(String templateId) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(TEMPLATES_PATH);
            for (Resource resource : resources) {
                String id = inferTemplateId(resource);
                if (templateId.equals(id)) {
                    try (InputStream is = resource.getInputStream()) {
                        return objectMapper.readTree(is);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[WP-1] Failed to load template {}: {}", templateId, e.getMessage());
        }
        return null;
    }

    private String inferTemplateId(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(".json")) {
            return null;
        }
        return filename.substring(0, filename.length() - 5);
    }
}
