package vip.mate.plugin.api.agent;

import java.util.Map;

/**
 * Agent runtime context passed to extension points.
 */
public class AgentContext {

    private final String agentId;
    private final String agentName;
    private final String templateId;
    private final String profileId;
    private final String capabilityPackId;
    private final String pluginKey;
    private final String templateMetadataJson;
    private final String knowledgeBaseIdsJson;
    private final String runtimeMode;
    private final Map<String, Object> state;

    protected AgentContext(Builder builder) {
        this.agentId = builder.agentId;
        this.agentName = builder.agentName;
        this.templateId = builder.templateId;
        this.profileId = builder.profileId;
        this.capabilityPackId = builder.capabilityPackId;
        this.pluginKey = builder.pluginKey;
        this.templateMetadataJson = builder.templateMetadataJson;
        this.knowledgeBaseIdsJson = builder.knowledgeBaseIdsJson;
        this.runtimeMode = builder.runtimeMode;
        this.state = Map.copyOf(builder.state);
    }

    public String agentId() { return agentId; }
    public String agentName() { return agentName; }
    public String templateId() { return templateId; }
    public String profileId() { return profileId; }
    public String capabilityPackId() { return capabilityPackId; }
    public String pluginKey() { return pluginKey; }
    public String templateMetadataJson() { return templateMetadataJson; }
    public String knowledgeBaseIdsJson() { return knowledgeBaseIdsJson; }
    public String runtimeMode() { return runtimeMode; }
    public Map<String, Object> state() { return state; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String agentId = "";
        private String agentName = "";
        private String templateId = "";
        private String profileId = "";
        private String capabilityPackId = "";
        private String pluginKey = "";
        private String templateMetadataJson = "";
        private String knowledgeBaseIdsJson = "";
        private String runtimeMode = "";
        private Map<String, Object> state = Map.of();

        public Builder agentId(String v) { this.agentId = v != null ? v : ""; return this; }
        public Builder agentName(String v) { this.agentName = v != null ? v : ""; return this; }
        public Builder templateId(String v) { this.templateId = v != null ? v : ""; return this; }
        public Builder profileId(String v) { this.profileId = v != null ? v : ""; return this; }
        public Builder capabilityPackId(String v) { this.capabilityPackId = v != null ? v : ""; return this; }
        public Builder pluginKey(String v) { this.pluginKey = v != null ? v : ""; return this; }
        public Builder templateMetadataJson(String v) { this.templateMetadataJson = v != null ? v : ""; return this; }
        public Builder knowledgeBaseIdsJson(String v) { this.knowledgeBaseIdsJson = v != null ? v : ""; return this; }
        public Builder runtimeMode(String v) { this.runtimeMode = v != null ? v : ""; return this; }
        public Builder state(Map<String, Object> v) { this.state = v != null ? v : Map.of(); return this; }
        public AgentContext build() { return new AgentContext(this); }
    }
}
