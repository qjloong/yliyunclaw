package vip.mate.template.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import vip.mate.template.contract.TemplateFieldClassification;
import vip.mate.template.contract.TemplateSection;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * WP-6 resolver that scans built-in template JSON files and produces a classified
 * field inventory aligned with {@link TemplateSection}.
 *
 * <p>This component centralizes the template metadata parsing that is currently
 * scattered across {@link vip.mate.agent.service.TemplateService},
 * {@link vip.mate.agent.context.ContextRouterService}, and
 * {@link vip.mate.agent.graph.executor.ToolExecutionExecutor}.
 *
 * <p>First-pass classification rules:
 * <table>
 *   <tr><th>Field / Path</th><th>Section</th><th>Runtime</th><th>Policy</th><th>Seeds</th></tr>
 *   <tr><td>id, name, category, domain, version, icon</td><td>IDENTITY_METADATA</td><td>false</td><td>false</td><td>false</td></tr>
 *   <tr><td>runtime.*</td><td>RUNTIME_DEFAULTS</td><td>true</td><td>false</td><td>false</td></tr>
 *   <tr><td>defaultWorkspacePolicy.*</td><td>SAFETY_DEFAULTS</td><td>true</td><td>true</td><td>false</td></tr>
 *   <tr><td>contextSources.*</td><td>CONTEXT_DECLARATIONS</td><td>true</td><td>false</td><td>false</td></tr>
 *   <tr><td>workspaceFiles</td><td>MATERIALIZATION</td><td>false</td><td>false</td><td>true</td></tr>
 *   <tr><td>defaultKnowledgeBases</td><td>MATERIALIZATION</td><td>false</td><td>false</td><td>true</td></tr>
 *   <tr><td>qualityGates, mockAcceptanceTasks, mvpScope</td><td>ACCEPTANCE_CONTRACT</td><td>false</td><td>false</td><td>false</td></tr>
 *   <tr><td>tools, capabilityPack</td><td>CAPABILITY_DECLARATION</td><td>true</td><td>false</td><td>false</td></tr>
 *   <tr><td>homeQuickStarts, interactionHints, starterPrompts</td><td>UX_METADATA</td><td>false</td><td>false</td><td>false</td></tr>
 * </table>
 *
 * @author MateClaw Team
 * @see vip.mate.template.contract.TemplateApplicationContract
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateMetadataResolver {

    private static final String TEMPLATES_PATH = "classpath:templates/*.json";

    private final ObjectMapper objectMapper;

    /**
     * Classify all top-level fields of a built-in template.
     *
     * @param templateId the template id (e.g., "coding-agent")
     * @return a list of field classifications; empty if template not found
     */
    public List<TemplateFieldClassification> classifyTemplateFields(String templateId) {
        JsonNode root = loadTemplate(templateId);
        if (root == null || !root.isObject()) {
            return List.of();
        }

        List<TemplateFieldClassification> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            result.add(classifyField(fieldName));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Return the set of sections present in a template.
     */
    public Set<TemplateSection> resolveSections(String templateId) {
        return classifyTemplateFields(templateId).stream()
                .map(TemplateFieldClassification::section)
                .collect(java.util.LinkedHashSet::new, Set::add, Set::addAll);
    }

    /**
     * Check whether a template contains runtime-contract fields.
     */
    public boolean hasRuntimeContract(String templateId) {
        return classifyTemplateFields(templateId).stream()
                .anyMatch(TemplateFieldClassification::affectsRuntime);
    }

    /**
     * Check whether a template contains policy-tightening fields.
     */
    public boolean hasPolicyDefaults(String templateId) {
        return classifyTemplateFields(templateId).stream()
                .anyMatch(TemplateFieldClassification::canTightenPolicy);
    }

    private TemplateFieldClassification classifyField(String fieldName) {
        return switch (fieldName) {
            case "id", "name", "nameZh", "description", "descriptionZh", "category", "domain",
                    "version", "icon", "tags", "sortOrder", "agentType" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.IDENTITY_METADATA,
                            false, false, false, List.of("TemplateService"));
            case "runtime" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.RUNTIME_DEFAULTS,
                            true, false, false, List.of("TemplateService", "ContextRouterService"));
            case "defaultWorkspacePolicy" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.SAFETY_DEFAULTS,
                            true, true, false, List.of("TemplateService", "ToolExecutionExecutor"));
            case "contextSources", "knowledgeBindings" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.CONTEXT_DECLARATIONS,
                            true, false, false, List.of("ContextRouterService"));
            case "workspaceFiles" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.MATERIALIZATION,
                            false, false, true, List.of("TemplateService"));
            case "defaultKnowledgeBases" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.MATERIALIZATION,
                            false, false, true, List.of("TemplateService"));
            case "qualityGates", "mockAcceptanceTasks", "mvpScope" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.ACCEPTANCE_CONTRACT,
                            false, false, false, List.of("TemplateService"));
            case "tools", "capabilityPack", "permissions" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.CAPABILITY_DECLARATION,
                            true, false, false, List.of("TemplateService", "AgentGraphBuilder"));
            case "homeQuickStarts", "interactionHints", "starterPrompts", "homeSubtitle" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.UX_METADATA,
                            false, false, false, List.of("TemplateService"));
            case "systemPrompt", "maxIterations" ->
                    new TemplateFieldClassification(fieldName, TemplateSection.RUNTIME_DEFAULTS,
                            true, false, false, List.of("TemplateService", "AgentGraphBuilder"));
            default ->
                    new TemplateFieldClassification(fieldName, TemplateSection.IDENTITY_METADATA,
                            false, false, false, List.of());
        };
    }

    private JsonNode loadTemplate(String templateId) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(TEMPLATES_PATH);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".json")) continue;
                String id = filename.substring(0, filename.length() - 5);
                if (templateId.equals(id)) {
                    try (InputStream is = resource.getInputStream()) {
                        return objectMapper.readTree(is);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[WP-6] Failed to load template {}: {}", templateId, e.getMessage());
        }
        return null;
    }
}
