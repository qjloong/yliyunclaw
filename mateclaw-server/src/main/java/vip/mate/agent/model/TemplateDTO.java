package vip.mate.agent.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 模板 DTO
 *
 * @author MateClaw Team
 */
@Data
public class TemplateDTO {

    private String id;
    private String name;
    private String nameZh;
    private String description;
    private String descriptionZh;
    private String icon;
    private String agentType;
    private String tags;
    private String version;
    private String status;
    private String category;
    private String domain;
    private String visibility;
    private String ownerType;
    private Integer maxIterations;
    private String systemPrompt;
    private Boolean featured;
    private Integer sortOrder;
    private Map<String, Object> runtime;
    private Map<String, Object> permissions;
    private Map<String, Object> defaultWorkspacePolicy;
    private Map<String, Object> agentProfile;
    private Map<String, Object> capabilityPack;
    private List<Map<String, Object>> pluginBindings;
    private Map<String, Object> contextSources;
    private Map<String, Object> knowledgeBindings;
    private List<Map<String, Object>> tools;
    private Map<String, Object> inputSchema;
    private List<String> outputFormats;
    private Map<String, Object> qualityGates;
    private List<Map<String, Object>> mockAcceptanceTasks;
    private Map<String, Object> interactionHints;
    private List<Map<String, Object>> starterPrompts;
    private String homeSubtitle;
    private List<Map<String, Object>> homeQuickStarts;
    private Map<String, Object> mvpScope;
    private List<DefaultKnowledgeBaseTemplate> defaultKnowledgeBases;
    private List<WorkspaceFileTemplate> workspaceFiles;

    @Data
    public static class WorkspaceFileTemplate {
        private String filename;
        private String content;
        private Boolean enabled;
        private Integer sortOrder;
    }

    @Data
    public static class DefaultKnowledgeBaseTemplate {
        private String name;
        private String externalKey;
        private String description;
        private Boolean required;
        private List<DefaultWikiPageTemplate> pages;
    }

    @Data
    public static class DefaultWikiPageTemplate {
        private String slug;
        private String title;
        private String summary;
        private String content;
        private String pageType;
    }
}
