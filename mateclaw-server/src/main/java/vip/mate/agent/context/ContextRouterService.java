package vip.mate.agent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.exception.MateClawException;
import vip.mate.memory.search.SessionSearchResult;
import vip.mate.memory.search.SessionSearchService;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.service.WikiDomainProfileRegistryService;
import vip.mate.wiki.service.WikiKnowledgeBaseService;
import vip.mate.workspace.core.model.ContextRouterSummary;
import vip.mate.workspace.core.model.ProjectInsightSummary;
import vip.mate.workspace.core.service.WorkspaceService;
import vip.mate.workspace.document.WorkspaceFileService;
import vip.mate.context.ContextAssemblyService;
import vip.mate.context.contract.ContextLifecycleContract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 5 unified context router summary service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextRouterService {

    /** P1 Closure SF-1: approximate token budget (≈3.5 chars/token = ~685 tokens for 2400 chars). */
    private static final int INJECTION_CHAR_BUDGET = 2400;
    private static final int INJECTION_TOKEN_BUDGET = estimateTokens(INJECTION_CHAR_BUDGET);
    private static final int INJECTION_SECTION_ITEM_LIMIT = 3;
    private static final double CHARS_PER_TOKEN_FALLBACK = 3.5;

    private static int estimateTokens(int charLen) {
        return (int) Math.round(charLen / CHARS_PER_TOKEN_FALLBACK);
    }


    private final WorkspaceService workspaceService;
    private final AgentMapper agentMapper;
    private final WorkspaceFileService workspaceFileService;
    private final WikiKnowledgeBaseService wikiKnowledgeBaseService;
    private final WikiDomainProfileRegistryService domainProfileRegistryService;
    private final SessionSearchService sessionSearchService;
    private final ObjectMapper objectMapper;
    /** WP-4: typed/provenance-aware context assembly — augments summarize() with block provenance. */
    private final ContextAssemblyService contextAssemblyService;

    public ContextRouterSummary summarize(Long workspaceId,
                                          Long agentId,
                                          String conversationId,
                                          String requestedPath) {
        ProjectInsightSummary projectInsight = workspaceService.getProjectInsight(workspaceId, requestedPath);

        ContextRouterSummary summary = new ContextRouterSummary();
        summary.setRootPath(projectInsight.getRootPath());
        summary.setRelativePath(projectInsight.getRelativePath());
        summary.setGeneratedAt(Instant.now().toString());

        LinkedHashSet<String> activeSources = new LinkedHashSet<>();
        activeSources.add("project_cache");

        if (agentId == null) {
            summary.setActiveSources(new ArrayList<>(activeSources));
            summary.setTemplateKnowledgeKeys(List.of());
            summary.setMissingKnowledgeBindings(List.of());
            summary.setMemoryFiles(List.of());
            summary.setKnowledgeBases(List.of());
            summary.setRecentSessions(List.of());
            return summary;
        }

        AgentEntity agent = getRequiredAgent(agentId);
        if (workspaceId != null && agent.getWorkspaceId() != null && !workspaceId.equals(agent.getWorkspaceId())) {
            throw new MateClawException("err.workspace.agent_workspace_mismatch", "当前智能体不属于所选工作区");
        }

        TemplateRouteConfig routeConfig = resolveTemplateRouteConfig(agent);
        summary.setTemplateKnowledgeKeys(routeConfig.knowledgeKeys());
        summary.setSessionTemporaryEnabled(routeConfig.sessionTemporaryEnabled());
        summary.setProjectDerivedEnabled(routeConfig.projectDerivedEnabled());

        List<ContextRouterSummary.MemoryFileSummary> memoryFiles = summarizeMemoryFiles(agentId);
        summary.setMemoryFiles(memoryFiles);
        if (memoryFiles.stream().anyMatch(item -> Boolean.TRUE.equals(item.getEnabled()))) {
            activeSources.add("workspace_memory");
        }

        List<ContextRouterSummary.KnowledgeBaseSummary> knowledgeBases = summarizeKnowledgeBases(agentId, routeConfig.knowledgeKeys());
        summary.setKnowledgeBases(knowledgeBases);
        summary.setMissingKnowledgeBindings(List.of());
        if (!knowledgeBases.isEmpty()) {
            activeSources.add("wiki");
        }

        List<ContextRouterSummary.SessionSummary> recentSessions = summarizeRecentSessions(agentId, conversationId);
        summary.setRecentSessions(recentSessions);
        if (!recentSessions.isEmpty()) {
            activeSources.add("session_search");
        }

        // WP-4: augment with provenance from ContextAssemblyService (typed source taxonomy).
        // Falls back gracefully when contextAssemblyService is null or returns empty.
        enrichSourcesFromAssembly(workspaceId, agentId, conversationId, requestedPath, activeSources);

        summary.setActiveSources(new ArrayList<>(activeSources));
        return summary;
    }

    /**
     * WP-4: pull typed source taxonomy from {@link ContextAssemblyService} and merge
     * into the existing activeSources set. Non-fatal — existing source detection
     * remains the primary path when the assembly service is unavailable or returns
     * no blocks.
     */
    private void enrichSourcesFromAssembly(Long workspaceId, Long agentId,
                                          String conversationId, String requestedPath,
                                          LinkedHashSet<String> activeSources) {
        if (contextAssemblyService == null || agentId == null) {
            return;
        }
        try {
            ChatOrigin assemblyOrigin = ChatOrigin.EMPTY
                    .withWorkspace(workspaceId, requestedPath);
            List<ContextLifecycleContract.ContextBlock> blocks =
                    contextAssemblyService.assembleContext(agentId, conversationId, null, assemblyOrigin);
            for (ContextLifecycleContract.ContextBlock block : blocks) {
                String sourceName = block.provenance().sourceType().name().toLowerCase();
                activeSources.add(sourceName);
            }
        } catch (Exception e) {
            log.debug("[WP-4] ContextAssemblyService failed, preserving legacy source detection: {}",
                    e.getMessage());
        }
    }

    public String buildInjectionBlock(Long agentId,
                                      String conversationId,
                      ChatOrigin origin,
                      String userQuery) {
        if (agentId == null || origin == null || origin.workspaceId() == null) {
            return "";
        }
        try {
        ProjectInsightSummary projectInsight = workspaceService.getProjectInsight(
            origin.workspaceId(),
            origin.workspaceBasePath());
            ContextRouterSummary summary = summarize(
                    origin.workspaceId(),
                    agentId,
                    conversationId,
                    origin.workspaceBasePath());
            StringBuilder sb = new StringBuilder("<context-router>\n");
        int remaining = INJECTION_CHAR_BUDGET - sb.length();
        remaining = appendBudgetedLine(sb, remaining,
            "[Turn-scoped routing hints. Prefer these sources before repeating broad workspace scans.]\n");
        remaining = appendBudgetedKeyValue(sb, remaining, "Project",
            firstNonBlank(summary.getRelativePath(), summary.getRootPath()));
        remaining = appendBudgetedKeyValue(sb, remaining, "Source priority",
            join(prioritizeSources(summary, userQuery), 4));
        remaining = appendBudgetedKeyValue(sb, remaining, "Workspace scan policy",
            "Reuse the cached project/material index below. Do not call list_directory or index_directory_materials for the same broad workspace/project unless the user asks to refresh/sync, cache is missing, or a specific subfolder/file must be inspected.");
            if (summary.getTemplateKnowledgeKeys() != null && !summary.getTemplateKnowledgeKeys().isEmpty()) {
        remaining = appendBudgetedKeyValue(sb, remaining, "Template knowledge keys",
            join(summary.getTemplateKnowledgeKeys(), 4));
            }
            if (summary.getMissingKnowledgeBindings() != null && !summary.getMissingKnowledgeBindings().isEmpty()) {
        remaining = appendBudgetedKeyValue(sb, remaining, "Missing knowledge bindings",
            join(summary.getMissingKnowledgeBindings(), 4));
            }
        remaining = appendBudgetedList(sb, remaining, "Project cues",
            selectProjectHints(projectInsight, userQuery), INJECTION_SECTION_ITEM_LIMIT);
        remaining = appendBudgetedList(sb, remaining, "Cached material index",
            selectMaterialIndexHints(projectInsight, userQuery), 4);
        remaining = appendBudgetedList(sb, remaining, "Workspace memory",
            selectMemoryHints(summary, userQuery), INJECTION_SECTION_ITEM_LIMIT);
        remaining = appendBudgetedList(sb, remaining, "Session recall",
            selectSessionHints(agentId, conversationId, userQuery), INJECTION_SECTION_ITEM_LIMIT);
        remaining = appendBudgetedList(sb, remaining, "Wiki route",
            selectWikiHints(summary, userQuery), INJECTION_SECTION_ITEM_LIMIT);
        if (remaining >= "</context-router>".length()) {
        sb.append("</context-router>");
        } else {
        trimTrailingWhitespace(sb);
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append("</context-router>");
        }
            // P1 Closure SF-1: log actual char/token budget usage for observability
            int actualChars = sb.length();
            log.debug("[ContextRouter] Injection block: {} chars / ~{} tokens (budget={} chars / ~{} tokens)",
                    actualChars, estimateTokens(actualChars), INJECTION_CHAR_BUDGET, INJECTION_TOKEN_BUDGET);
            return sb.toString();
        } catch (Exception e) {
            log.debug("[ContextRouter] Failed to build injection block for agent {}: {}", agentId, e.getMessage());
            return "";
        }
    }

    private List<ContextRouterSummary.MemoryFileSummary> summarizeMemoryFiles(Long agentId) {
        return workspaceFileService.listFiles(agentId).stream()
                .limit(8)
                .map(file -> {
                    ContextRouterSummary.MemoryFileSummary item = new ContextRouterSummary.MemoryFileSummary();
                    item.setFilename(file.getFilename());
                    item.setEnabled(Boolean.TRUE.equals(file.getEnabled()));
                    item.setFileSize(file.getFileSize());
                    return item;
                })
                .toList();
    }

    private List<ContextRouterSummary.KnowledgeBaseSummary> summarizeKnowledgeBases(Long agentId, List<String> allowedKeys) {
        Set<String> allowed = new LinkedHashSet<>(allowedKeys);
        return wikiKnowledgeBaseService.listByAgentId(agentId).stream()
                .limit(8)
                .map(kb -> toKnowledgeBaseSummary(kb, allowed))
                .toList();
    }

    private ContextRouterSummary.KnowledgeBaseSummary toKnowledgeBaseSummary(WikiKnowledgeBaseEntity kb, Set<String> allowed) {
        ContextRouterSummary.KnowledgeBaseSummary item = new ContextRouterSummary.KnowledgeBaseSummary();
        item.setId(kb.getId());
        item.setName(kb.getName());
        item.setExternalKey(kb.getExternalKey());
        item.setKbKind(kb.getKbKind());
        item.setDomainProfileId(kb.getDomainProfileId());
        item.setDomainProfileDisplayName(formatDomainProfileLabel(kb.getDomainProfileId()));
        item.setTemplateMatched(allowed.isEmpty()
                || (StringUtils.hasText(kb.getExternalKey()) && allowed.contains(kb.getExternalKey())));
        return item;
    }

    private List<ContextRouterSummary.SessionSummary> summarizeRecentSessions(Long agentId, String currentConversationId) {
        return sessionSearchService.listRecent(agentId, 4).stream()
                .filter(item -> !Objects.equals(currentConversationId, Objects.toString(item.get("conversationId"), null)))
                .limit(3)
                .map(this::toSessionSummary)
                .toList();
    }

    private ContextRouterSummary.SessionSummary toSessionSummary(Map<String, Object> item) {
        ContextRouterSummary.SessionSummary session = new ContextRouterSummary.SessionSummary();
        session.setConversationId(Objects.toString(item.get("conversationId"), null));
        session.setTitle(Objects.toString(item.get("title"), null));
        Object messageCount = item.get("messageCount");
        if (messageCount instanceof Number number) {
            session.setMessageCount(number.intValue());
        }
        Object lastActiveTime = item.get("lastActiveTime");
        session.setLastActiveTime(lastActiveTime != null ? lastActiveTime.toString() : null);
        return session;
    }

    private TemplateRouteConfig resolveTemplateRouteConfig(AgentEntity agent) {
        if (agent == null || !StringUtils.hasText(agent.getTemplateMetadataJson())) {
            return new TemplateRouteConfig(List.of(), false, false);
        }
        try {
            JsonNode template = objectMapper.readTree(agent.getTemplateMetadataJson()).path("template");
            JsonNode knowledgeBindings = template.path("knowledgeBindings");
            JsonNode contextSources = template.path("contextSources");

            LinkedHashSet<String> keys = new LinkedHashSet<>();
            if (knowledgeBindings.isObject()) {
                appendBindingKeys(keys, knowledgeBindings.path("required"));
                appendBindingKeys(keys, knowledgeBindings.path("selectable"));
                appendBindingKeys(keys, knowledgeBindings.path("optional"));
            }

            JsonNode sessionTemporary = contextSources.isObject()
                    ? contextSources.path("sessionTemporary")
                    : knowledgeBindings.path("sessionTemporary");
            JsonNode projectDerived = contextSources.isObject()
                    ? contextSources.path("projectDerived")
                    : knowledgeBindings.path("projectDerived");

            boolean sessionTemporaryEnabled = sessionTemporary
                    .path("enabled")
                    .asBoolean(false);
            boolean projectDerivedEnabled = projectDerived
                    .path("enabled")
                    .asBoolean(false);
            return new TemplateRouteConfig(new ArrayList<>(keys), sessionTemporaryEnabled, projectDerivedEnabled);
        } catch (Exception e) {
            log.warn("[ContextRouter] Failed to parse template route config for agent {}: {}",
                    agent.getId(), e.getMessage());
            return new TemplateRouteConfig(List.of(), false, false);
        }
    }

    private void appendBindingKeys(Set<String> keys, JsonNode node) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item.isValueNode() && StringUtils.hasText(item.asText())) {
                keys.add(item.asText().trim());
            }
        }
    }

    private AgentEntity getRequiredAgent(Long agentId) {
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new MateClawException("err.agent.not_found", "Agent不存在: " + agentId);
        }
        return agent;
    }

    private List<String> prioritizeSources(ContextRouterSummary summary, String userQuery) {
        return summary.getActiveSources().stream()
                .sorted((left, right) -> Integer.compare(
                        scoreSource(right, summary, userQuery),
                        scoreSource(left, summary, userQuery)))
                .toList();
    }

    private int scoreSource(String source, ContextRouterSummary summary, String userQuery) {
        String normalizedQuery = normalize(userQuery);
        return switch (source) {
            case "project_cache" -> 90 + queryIntentBonus(normalizedQuery, "code", "bug", "fix", "test", "build", "run", "path", "file", "目录", "文件", "编译", "测试");
            case "workspace_memory" -> 70
                    + queryIntentBonus(normalizedQuery, "agent", "rule", "instruction", "profile", "memory", "cache", "规范", "约束", "说明")
                    + (summary.getMemoryFiles().stream().anyMatch(item -> Boolean.TRUE.equals(item.getEnabled())) ? 6 : 0);
            case "session_search" -> StringUtils.hasText(normalizedQuery)
                    ? 60 + queryIntentBonus(normalizedQuery, "again", "before", "history", "previous", "之前", "刚才", "上次")
                    : 10;
            case "wiki" -> 50
                    + queryIntentBonus(normalizedQuery, "knowledge", "exam", "spec", "doc", "manual", "知识", "题目", "规范", "文档")
                    + (!summary.getTemplateKnowledgeKeys().isEmpty() ? 8 : 0);
            default -> 0;
        };
    }

    private List<String> selectProjectHints(ProjectInsightSummary projectInsight, String userQuery) {
        if (projectInsight == null) {
            return List.of();
        }
        List<ScoredHint> hints = new ArrayList<>();
        addScoredHints(hints, projectInsight.getStackHints(), "Stack", 32, userQuery);
        addScoredHints(hints, projectInsight.getKeyFiles(), "Key file", 40, userQuery);
        addScoredHints(hints, projectInsight.getModuleHints(), "Folder", 26, userQuery);
        addScoredHints(hints, projectInsight.getCommandHints(), "Command", 46, userQuery);
        if (StringUtils.hasText(projectInsight.getBuildSystem())) {
            hints.add(new ScoredHint("Build system: " + projectInsight.getBuildSystem(),
                    28 + queryIntentBonus(normalize(userQuery), "build", "compile", "package", "构建", "编译")));
        }
        if (StringUtils.hasText(projectInsight.getPackageManager())) {
            hints.add(new ScoredHint("Package manager: " + projectInsight.getPackageManager(),
                    24 + queryIntentBonus(normalize(userQuery), "install", "package", "dependency", "依赖", "安装")));
        }
        return collapseHints(hints);
    }

    private List<String> selectMaterialIndexHints(ProjectInsightSummary projectInsight, String userQuery) {
        if (projectInsight == null || projectInsight.getMaterialIndexHints() == null) {
            return List.of();
        }
        List<String> hints = projectInsight.getMaterialIndexHints();
        String normalizedQuery = normalize(userQuery);
        boolean refreshIntent = queryIntentBonus(normalizedQuery,
                "refresh", "rescan", "sync", "reload", "update index", "重新扫描", "刷新", "同步", "更新索引") > 0;
        if (refreshIntent) {
            List<String> withRefresh = new ArrayList<>(hints);
            withRefresh.add("User may be asking for refresh/sync: a new directory index scan is allowed if needed.");
            return withRefresh.stream().limit(4).toList();
        }
        return hints.stream().limit(4).toList();
    }

    private List<String> selectMemoryHints(ContextRouterSummary summary, String userQuery) {
        List<ScoredHint> hints = summary.getMemoryFiles().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(item -> new ScoredHint(
                        item.getFilename() + (item.getFileSize() != null ? " (" + item.getFileSize() + " bytes)" : ""),
                        scoreMemoryFile(item.getFilename(), userQuery)))
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .toList();
        if (hints.isEmpty()) {
            return List.of();
        }
        return hints.stream().map(ScoredHint::value).limit(3).toList();
    }

    private List<String> selectSessionHints(Long agentId, String conversationId, String userQuery) {
        if (!StringUtils.hasText(userQuery)) {
            return List.of();
        }
        return sessionSearchService.search(agentId, conversationId, userQuery, 3).stream()
                .map(this::toSessionHint)
                .filter(StringUtils::hasText)
                .limit(3)
                .toList();
    }

    private List<String> selectWikiHints(ContextRouterSummary summary, String userQuery) {
        List<ScoredHint> hints = new ArrayList<>();
        for (ContextRouterSummary.KnowledgeBaseSummary kb : summary.getKnowledgeBases()) {
            String label = formatKnowledgeBaseHint(kb);
            int profileScore = domainProfileRegistryService.matchScore(kb.getDomainProfileId(), userQuery);
            hints.add(new ScoredHint(label,
                18
                    + scoreByQuery(label, userQuery)
                    + profileScore
                    + (Boolean.TRUE.equals(kb.getTemplateMatched()) ? 6 : 0)
                    + ("business".equalsIgnoreCase(kb.getKbKind()) && profileScore > 0 ? 8 : 0)));
        }
        if (hints.isEmpty() && summary.getTemplateKnowledgeKeys() != null) {
            return summary.getTemplateKnowledgeKeys().stream()
                    .limit(2)
                    .map(key -> "Awaiting KB binding: " + key)
                    .toList();
        }
        return hints.stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .map(ScoredHint::value)
                .limit(3)
                .toList();
    }

    private String formatKnowledgeBaseHint(ContextRouterSummary.KnowledgeBaseSummary kb) {
        List<String> parts = new ArrayList<>();
        parts.add(kb.getName());
        if (StringUtils.hasText(kb.getExternalKey())) {
            parts.add("[" + kb.getExternalKey() + "]");
        }
        if (StringUtils.hasText(kb.getKbKind()) && !"general".equalsIgnoreCase(kb.getKbKind())) {
            parts.add(kb.getKbKind());
        }
        if (StringUtils.hasText(kb.getDomainProfileDisplayName())) {
            parts.add(kb.getDomainProfileDisplayName());
        } else if (StringUtils.hasText(kb.getDomainProfileId())) {
            parts.add(kb.getDomainProfileId());
        }
        return String.join(" · ", parts);
    }

    private String formatDomainProfileLabel(String domainProfileId) {
        if (!StringUtils.hasText(domainProfileId)) {
            return null;
        }
        String displayName = domainProfileRegistryService.displayNameOrDefault(domainProfileId);
        if (!StringUtils.hasText(displayName) || displayName.equals(domainProfileId.trim())) {
            return domainProfileId.trim();
        }
        return displayName + " (" + domainProfileId.trim() + ")";
    }

    private void addScoredHints(List<ScoredHint> hints,
                                List<String> values,
                                String label,
                                int baseScore,
                                String userQuery) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            hints.add(new ScoredHint(label + ": " + value,
                    baseScore + scoreByQuery(value, userQuery) + labelBonus(label, value, userQuery)));
        }
    }

    private List<String> collapseHints(List<ScoredHint> hints) {
        if (hints.isEmpty()) {
            return List.of();
        }
        return hints.stream()
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .map(ScoredHint::value)
                .distinct()
                .limit(4)
                .toList();
    }

    private int scoreMemoryFile(String filename, String userQuery) {
        String normalizedFile = normalize(filename);
        int score = 24 + scoreByQuery(filename, userQuery);
        if (normalizedFile.contains("agents")) {
            score += 8 + queryIntentBonus(normalize(userQuery), "rule", "instruction", "规范", "约束");
        }
        if (normalizedFile.contains("project_cache")) {
            score += 8 + queryIntentBonus(normalize(userQuery), "project", "stack", "build", "test", "项目", "构建", "测试");
        }
        if (normalizedFile.contains("profile")) {
            score += 6 + queryIntentBonus(normalize(userQuery), "profile", "mode", "role", "画像", "模式");
        }
        return score;
    }

    private String toSessionHint(SessionSearchResult result) {
        String title = firstNonBlank(result.title(), result.conversationId());
        String snippet = truncate(result.snippet(), 180);
        if (!StringUtils.hasText(snippet)) {
            return title;
        }
        return title + ": " + snippet;
    }

    private int labelBonus(String label, String value, String userQuery) {
        String normalizedQuery = normalize(userQuery);
        if (!StringUtils.hasText(normalizedQuery)) {
            return 0;
        }
        return switch (label) {
            case "Command" -> queryIntentBonus(normalizedQuery, "test", "build", "run", "start", "lint", "typecheck", "测试", "构建", "运行", "启动")
                    + queryIntentBonus(normalize(value), "test", "build", "run", "start", "lint", "typecheck", "dev");
            case "Key file" -> queryIntentBonus(normalizedQuery, "file", "readme", "doc", "config", "文件", "文档", "配置")
                    + queryIntentBonus(normalize(value), "readme", "agent", "package", "pom", "docker", "config");
            case "Folder" -> queryIntentBonus(normalizedQuery, "folder", "module", "dir", "目录", "模块");
            case "Stack" -> queryIntentBonus(normalizedQuery, "stack", "framework", "language", "技术栈", "框架", "语言");
            default -> 0;
        };
    }

    private int scoreByQuery(String candidate, String userQuery) {
        String normalizedCandidate = normalize(candidate);
        String normalizedQuery = normalize(userQuery);
        if (!StringUtils.hasText(normalizedCandidate) || !StringUtils.hasText(normalizedQuery)) {
            return 0;
        }
        int score = 0;
        if (normalizedCandidate.contains(normalizedQuery) || normalizedQuery.contains(normalizedCandidate)) {
            score += 18;
        }
        for (String token : tokenize(normalizedQuery)) {
            if (token.length() >= 2 && normalizedCandidate.contains(token)) {
                score += Math.min(8, Math.max(2, token.length()));
            }
        }
        return score;
    }

    private int queryIntentBonus(String normalizedQuery, String... keywords) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return 0;
        }
        int score = 0;
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalizedQuery.contains(normalize(keyword))) {
                score += 5;
            }
        }
        return score;
    }

    private List<String> tokenize(String normalizedQuery) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalizedQuery.split("\\s+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty()) {
            tokens.add(normalizedQuery);
        }
        return tokens;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
    }

    private int appendBudgetedLine(StringBuilder sb, int remaining, String line) {
        if (!StringUtils.hasText(line) || remaining <= 0) {
            return remaining;
        }
        String effective = line;
        if (effective.length() > remaining) {
            effective = truncate(effective, Math.max(remaining - 1, 0));
            if (!effective.endsWith("\n")) {
                effective += "\n";
            }
        }
        if (!StringUtils.hasText(effective) || effective.length() > remaining) {
            return remaining;
        }
        sb.append(effective);
        return remaining - effective.length();
    }

    private int appendBudgetedKeyValue(StringBuilder sb, int remaining, String label, String value) {
        if (!StringUtils.hasText(value) || remaining <= 0) {
            return remaining;
        }
        String prefix = "- " + label + ": ";
        if (prefix.length() + 4 > remaining) {
            return remaining;
        }
        String line = prefix + truncate(value, remaining - prefix.length() - 1) + "\n";
        if (line.length() > remaining) {
            return remaining;
        }
        sb.append(line);
        return remaining - line.length();
    }

    private int appendBudgetedList(StringBuilder sb,
                                   int remaining,
                                   String title,
                                   List<String> values,
                                   int limit) {
        if (values == null || values.isEmpty() || remaining <= 0) {
            return remaining;
        }
        String header = "- " + title + ":\n";
        if (header.length() + 8 > remaining) {
            return remaining;
        }
        sb.append(header);
        remaining -= header.length();
        int count = 0;
        for (String value : values) {
            if (count >= limit || remaining <= 8) {
                break;
            }
            String line = "  - " + truncate(value, remaining - 5) + "\n";
            if (!StringUtils.hasText(line) || line.length() > remaining) {
                break;
            }
            sb.append(line);
            remaining -= line.length();
            count++;
        }
        return remaining;
    }

    private String truncate(String value, int maxChars) {
        if (!StringUtils.hasText(value) || maxChars <= 0) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        if (maxChars <= 3) {
            return value.substring(0, maxChars);
        }
        return value.substring(0, maxChars - 3) + "...";
    }

    private void trimTrailingWhitespace(StringBuilder sb) {
        while (!sb.isEmpty() && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    private String join(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> effective = limit > 0 ? values.stream().limit(limit).toList() : values;
        String joined = String.join(", ", effective);
        if (limit > 0 && values.size() > effective.size()) {
            joined += ", ...";
        }
        return joined;
    }

    private String firstNonBlank(String left, String right) {
        return StringUtils.hasText(left) ? left : right;
    }

    private record ScoredHint(String value, int score) {
    }

    private record TemplateRouteConfig(List<String> knowledgeKeys,
                                       boolean sessionTemporaryEnabled,
                                       boolean projectDerivedEnabled) {
    }
}
