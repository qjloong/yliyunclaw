package vip.mate.template.contract;

import vip.mate.agent.model.AgentEntity;

/**
 * Contract for applying a built-in template to create an agent and its workspace
 * artifacts (WP-6).
 *
 * <p>This contract formalizes the sequence that {@link vip.mate.agent.service.TemplateService}
 * already performs informally:
 * <ol>
 *   <li>Resolve template metadata and classify fields by section.</li>
 *   <li>Create the {@link AgentEntity} with runtime defaults and metadata snapshot.</li>
 *   <li>Seed workspace files (if {@code MATERIALIZATION} section present).</li>
 *   <li>Seed knowledge bases and pages (if {@code MATERIALIZATION} section present).</li>
 *   <li>Bind safety defaults (if {@code SAFETY_DEFAULTS} section present).</li>
 *   <li>Record the applied template version for future health checks and repairs.</li>
 * </ol>
 *
 * <p>Non-goals:
 * <ul>
 *   <li>Does not redesign the template JSON schema.</li>
 *   <li>Does not add marketplace or onboarding UI flows.</li>
 *   <li>Does not change existing agent creation behavior.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see vip.mate.template.resolver.TemplateMetadataResolver
 */
public interface TemplateApplicationContract {

    /**
     * Apply a template to a workspace, producing a new agent and seeded artifacts.
     *
     * @param templateId  the built-in template id
     * @param workspaceId the target workspace id
     * @param username    optional username for attribution
     * @return the created agent entity
     */
    AgentEntity applyTemplate(String templateId, Long workspaceId, String username);

    /**
     * Validate that a template JSON adheres to the current contract sections.
     *
     * @param templateId the template id
     * @return validation result with any unknown or misplaced fields noted
     */
    ValidationResult validateTemplateContract(String templateId);

    /**
     * Result of template contract validation.
     */
    record ValidationResult(boolean valid,
                             java.util.List<String> unknownFields,
                             java.util.List<String> misplacedFields,
                             java.util.List<String> missingRecommendedFields) {

        public static ValidationResult ok() {
            return new ValidationResult(true, java.util.List.of(), java.util.List.of(), java.util.List.of());
        }
    }
}
