package vip.mate.agent.model;

import lombok.Data;
import vip.mate.template.contract.TemplateSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
public class TemplateHealthDTO {
    private String templateId;
    private Long workspaceId;
    private boolean ready;
    private int requiredCount;
    private int matchedRequiredCount;
    private int seededKnowledgeBaseCount;
    private int matchedSeededKnowledgeBaseCount;
    private List<KnowledgeBindingHealth> knowledgeBindings = new ArrayList<>();
    private int checkCount;
    private int passedCheckCount;
    private List<CaseCheckHealth> caseChecks = new ArrayList<>();
    private boolean applicationEvidenceReady;
    private int appliedAgentCount;
    private List<AppliedAgentHealth> appliedAgents = new ArrayList<>();
    /** WP-6: true when TemplateSchemaValidator passed the template against the current contract. */
    private Boolean schemaValid;
    /** WP-6: set of sections present in the template per TemplateMetadataResolver classification. */
    private Set<TemplateSection> sections;

    @Data
    public static class KnowledgeBindingHealth {
        private String externalKey;
        private String name;
        private boolean required;
        private boolean matched;
        private Long knowledgeBaseId;
        private int expectedPageCount;
        private int matchedPageCount;
    }

    @Data
    public static class CaseCheckHealth {
        private String key;
        private String label;
        private boolean passed;
        private String detail;
    }

    @Data
    public static class AppliedAgentHealth {
        private Long agentId;
        private String name;
        private boolean enabled;
        private int expectedWorkspaceFileCount;
        private int matchedWorkspaceFileCount;
        private boolean memoryFilePresent;
        private boolean acceptanceFilePresent;
        private boolean promptFilesConfigured;
    }
}
