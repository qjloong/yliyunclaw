package vip.mate.workspace.core.model;

import lombok.Data;

import java.util.List;

/**
 * 统一上下文路由摘要。
 */
@Data
public class ContextRouterSummary {
    private String rootPath;
    private String relativePath;
    private List<String> activeSources;
    private List<String> templateKnowledgeKeys;
    private List<String> missingKnowledgeBindings;
    private boolean sessionTemporaryEnabled;
    private boolean projectDerivedEnabled;
    private List<MemoryFileSummary> memoryFiles;
    private List<KnowledgeBaseSummary> knowledgeBases;
    private List<SessionSummary> recentSessions;
    private String generatedAt;

    @Data
    public static class MemoryFileSummary {
        private String filename;
        private Boolean enabled;
        private Long fileSize;
    }

    @Data
    public static class KnowledgeBaseSummary {
        private Long id;
        private String name;
        private String externalKey;
        private Boolean templateMatched;
    }

    @Data
    public static class SessionSummary {
        private String conversationId;
        private String title;
        private Integer messageCount;
        private String lastActiveTime;
    }
}