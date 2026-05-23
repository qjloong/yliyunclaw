package vip.mate.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import vip.mate.template.resolver.TemplateMetadataResolver;
import vip.mate.template.resolver.TemplateSchemaValidator;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.agent.AgentService;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.model.TemplateHealthDTO;
import vip.mate.agent.model.TemplateDTO;
import vip.mate.exception.MateClawException;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.model.WikiPageEntity;
import vip.mate.wiki.service.WikiKnowledgeBaseService;
import vip.mate.wiki.service.WikiPageService;
import vip.mate.workspace.document.WorkspaceFileService;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 模板服务
 * <p>
 * 扫描 classpath:templates/*.json 下的模板文件，
 * 支持列出模板和应用模板创建 Agent。
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final AgentService agentService;
    private final WorkspaceFileService workspaceFileService;
    private final WikiKnowledgeBaseService wikiKnowledgeBaseService;
    private final WikiPageService wikiPageService;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    /** WP-6: provides typed section classification for template field inventory. */
    private final TemplateMetadataResolver templateMetadataResolver;
    /** WP-6: validates template contracts against the current TemplateSection schema. */
    private final TemplateSchemaValidator templateSchemaValidator;

    /**
     * 列出所有可用模板
     */
    public List<TemplateDTO> listTemplates() {
        List<TemplateDTO> templates = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            Resource[] resources = resolver.getResources("classpath:templates/*.json");
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    TemplateDTO dto = objectMapper.readValue(is, TemplateDTO.class);
                    templates.add(dto);
                } catch (IOException e) {
                    log.warn("Failed to parse template file: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan template files", e);
        }

        templates.sort(Comparator
            .comparing((TemplateDTO dto) -> dto.getSortOrder() != null ? dto.getSortOrder() : Integer.MAX_VALUE)
            .thenComparing(TemplateDTO::getId));
        return templates;
    }

    public List<TemplateHealthDTO> listTemplateHealth(Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        return listTemplates().stream()
                .map(template -> buildTemplateHealth(template, wsId))
                .toList();
    }

    /**
     * WP-6: enrich template health check with schema validation.
     * Falls back gracefully when templateMetadataResolver is unavailable.
     */
    public TemplateHealthDTO enrichTemplateHealth(String templateId, Long workspaceId) {
        TemplateDTO template = findTemplateById(templateId);
        TemplateHealthDTO dto = buildTemplateHealth(template, workspaceId != null ? workspaceId : 1L);
        if (templateSchemaValidator != null && templateMetadataResolver != null) {
            try {
                var validation = templateSchemaValidator.validate(templateId);
                dto.setSchemaValid(validation.valid());
                var sections = templateMetadataResolver.resolveSections(templateId);
                dto.setSections(sections);
            } catch (Exception e) {
                log.debug("[WP-6] Template health enrichment failed for {}: {}", templateId, e.getMessage());
            }
        }
        return dto;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void repairAppliedTemplateEncodingOnStartup() {
        Map<String, TemplateDTO> templatesById = listTemplates().stream()
                .filter(template -> template.getId() != null && !template.getId().isBlank())
                .collect(Collectors.toMap(TemplateDTO::getId, template -> template, (left, right) -> left, LinkedHashMap::new));
        if (templatesById.isEmpty()) {
            return;
        }

        int repairedCount = 0;
        for (AgentEntity agent : agentService.listAgents()) {
            if (agent.getTemplateId() == null || agent.getTemplateId().isBlank()) {
                continue;
            }
            TemplateDTO template = templatesById.get(agent.getTemplateId());
            if (template == null) {
                continue;
            }
            if (repairTemplateDerivedAgent(agent, template)) {
                agentService.updateAgent(agent);
                repairedCount++;
            }
        }

        if (repairedCount > 0) {
            log.info("Repaired mojibake in {} template-derived agent(s)", repairedCount);
        }
    }

    @Transactional
    public Map<String, Integer> syncMissingDefaultFiles(String templateId, Long workspaceId) {
        long wsId = workspaceId != null ? workspaceId : 1L;
        TemplateDTO template = findTemplateById(templateId);
        SeedSyncResult seedSync = seedDefaultKnowledgeBases(template, wsId, null);
        List<AgentEntity> appliedAgents = agentService.listAgentsByWorkspace(wsId).stream()
                .filter(agent -> templateId.equals(agent.getTemplateId()))
                .toList();

        int createdFileCount = 0;
        for (AgentEntity agent : appliedAgents) {
            createdFileCount += syncMissingWorkspaceFiles(agent, template);
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("agentCount", appliedAgents.size());
        result.put("createdFileCount", createdFileCount);
        result.put("createdKnowledgeBaseCount", seedSync.createdKnowledgeBaseCount());
        result.put("createdKnowledgePageCount", seedSync.createdKnowledgePageCount());
        return result;
    }

    /**
     * 应用模板创建 Agent 及其工作区文件
     *
     * @param templateId 模板 ID
     * @param workspaceId 工作区 ID
     * @return 创建的 AgentEntity
     */
    @Transactional
    public AgentEntity applyTemplate(String templateId, Long workspaceId) {
        return applyTemplate(templateId, workspaceId, null);
    }

    @Transactional
    public AgentEntity applyTemplate(String templateId, Long workspaceId, String username) {
        TemplateDTO template = findTemplateById(templateId);
        Long creatorUserId = resolveUserId(username);

        // 1. 创建 Agent
        AgentEntity agent = new AgentEntity();
        agent.setName(firstNonBlank(template.getNameZh(), template.getName()));
        agent.setDescription(firstNonBlank(template.getDescriptionZh(), template.getDescription()));
        agent.setAgentType(template.getAgentType());
        agent.setIcon(template.getIcon());
        agent.setTags(template.getTags());
        agent.setMaxIterations(template.getMaxIterations());
        agent.setSystemPrompt(template.getSystemPrompt());
        agent.setTemplateId(template.getId());
        agent.setTemplateVersion(template.getVersion());
        agent.setTemplateCategory(template.getCategory());
        agent.setTemplateDomain(template.getDomain());
        agent.setProfileId(extractString(template.getAgentProfile(), "profileId"));
        agent.setCapabilityPackId(extractString(template.getCapabilityPack(), "packId"));
        agent.setTemplateMetadataJson(toTemplateMetadataJson(template));
        agent.setHomeSubtitle(template.getHomeSubtitle());
        agent.setHomeQuickStartsJson(toJsonOrNull(template.getHomeQuickStarts()));
        agent.setWorkspaceId(workspaceId != null ? workspaceId : 1L);
        AgentEntity created = agentService.createAgent(agent);
        seedDefaultKnowledgeBases(template, created.getWorkspaceId(), creatorUserId);

        // 2. 创建工作区文件
        if (template.getWorkspaceFiles() != null && !template.getWorkspaceFiles().isEmpty()) {
            for (TemplateDTO.WorkspaceFileTemplate wf : template.getWorkspaceFiles()) {
                workspaceFileService.saveFile(created.getId(), wf.getFilename(), wf.getContent());
            }

            // 3. 启用指定文件并按 sortOrder 排序（setPromptFiles 用列表索引作为排序值）
            List<String> enabledFilenames = getEnabledWorkspaceFilenames(template);

            if (!enabledFilenames.isEmpty()) {
                workspaceFileService.setPromptFiles(created.getId(), enabledFilenames);
            }
        }

        return created;
    }

    private TemplateDTO findTemplateById(String templateId) {
        return listTemplates().stream()
                .filter(t -> t.getId().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new MateClawException("err.agent.template_not_found", "模板不存在: " + templateId));
    }

    private int syncMissingWorkspaceFiles(AgentEntity agent, TemplateDTO template) {
        List<TemplateDTO.WorkspaceFileTemplate> workspaceFiles = template.getWorkspaceFiles() != null
                ? template.getWorkspaceFiles()
                : List.of();
        if (workspaceFiles.isEmpty()) {
            return 0;
        }

        int createdFileCount = 0;
        for (TemplateDTO.WorkspaceFileTemplate workspaceFile : workspaceFiles) {
            String filename = workspaceFile.getFilename();
            if (filename == null || filename.isBlank()) {
                continue;
            }
            if (workspaceFileService.getFile(agent.getId(), filename) != null) {
                continue;
            }
            workspaceFileService.saveFile(agent.getId(), filename, workspaceFile.getContent());
            createdFileCount++;
        }

        List<String> mergedPromptFiles = new ArrayList<>(workspaceFileService.getPromptFiles(agent.getId()));
        for (String filename : getEnabledWorkspaceFilenames(template)) {
            if (!mergedPromptFiles.contains(filename)) {
                mergedPromptFiles.add(filename);
            }
        }
        if (!mergedPromptFiles.isEmpty()) {
            workspaceFileService.setPromptFiles(agent.getId(), mergedPromptFiles);
        }
        return createdFileCount;
    }

    private List<String> getEnabledWorkspaceFilenames(TemplateDTO template) {
        if (template.getWorkspaceFiles() == null || template.getWorkspaceFiles().isEmpty()) {
            return List.of();
        }
        return template.getWorkspaceFiles().stream()
                .filter(wf -> Boolean.TRUE.equals(wf.getEnabled()))
                .sorted((a, b) -> {
                    int sa = a.getSortOrder() != null ? a.getSortOrder() : 0;
                    int sb = b.getSortOrder() != null ? b.getSortOrder() : 0;
                    return Integer.compare(sa, sb);
                })
                .map(TemplateDTO.WorkspaceFileTemplate::getFilename)
                .filter(filename -> filename != null && !filename.isBlank())
                .toList();
    }

    private SeedSyncResult seedDefaultKnowledgeBases(TemplateDTO template, Long workspaceId, Long creatorUserId) {
        if (template.getDefaultKnowledgeBases() == null || template.getDefaultKnowledgeBases().isEmpty()) {
            return SeedSyncResult.EMPTY;
        }
        long wsId = workspaceId != null ? workspaceId : 1L;
        int createdKnowledgeBaseCount = 0;
        int createdKnowledgePageCount = 0;
        for (TemplateDTO.DefaultKnowledgeBaseTemplate kbTemplate : template.getDefaultKnowledgeBases()) {
            if (kbTemplate.getName() == null || kbTemplate.getName().isBlank()) {
                continue;
            }
            WikiKnowledgeBaseEntity kb = wikiKnowledgeBaseService.findByWorkspaceAndName(wsId, kbTemplate.getName().trim());
            if (kb == null) {
                kb = wikiKnowledgeBaseService.create(
                        kbTemplate.getName(),
                        kbTemplate.getDescription(),
                        null,
                        wsId,
                        null,
                        creatorUserId);
                createdKnowledgeBaseCount++;
            }
            createdKnowledgePageCount += seedDefaultWikiPages(kb, kbTemplate.getPages());
        }
        return new SeedSyncResult(createdKnowledgeBaseCount, createdKnowledgePageCount);
    }

    private Long resolveUserId(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        UserEntity user = authService.findByUsername(username.trim());
        return user != null ? user.getId() : null;
    }

    private TemplateHealthDTO buildTemplateHealth(TemplateDTO template, Long workspaceId) {
        TemplateHealthDTO dto = new TemplateHealthDTO();
        dto.setTemplateId(template.getId());
        dto.setWorkspaceId(workspaceId);

        List<TemplateDTO.DefaultKnowledgeBaseTemplate> seeds = template.getDefaultKnowledgeBases() != null
                ? template.getDefaultKnowledgeBases()
                : List.of();
        dto.setSeededKnowledgeBaseCount(seeds.size());

        int matchedRequired = 0;
        int requiredCount = 0;
        int matchedSeeded = 0;

        for (TemplateDTO.DefaultKnowledgeBaseTemplate seed : seeds) {
            TemplateHealthDTO.KnowledgeBindingHealth item = new TemplateHealthDTO.KnowledgeBindingHealth();
            item.setName(seed.getName());
            item.setRequired(Boolean.TRUE.equals(seed.getRequired()));
            item.setExpectedPageCount(seed.getPages() != null ? seed.getPages().size() : 0);
            if (item.isRequired()) {
                requiredCount++;
            }

            WikiKnowledgeBaseEntity kb = wikiKnowledgeBaseService.findByWorkspaceAndName(workspaceId, seed.getName());
            if (kb != null) {
                item.setMatched(true);
                item.setKnowledgeBaseId(kb.getId());
                item.setMatchedPageCount(countMatchedSeedPages(kb.getId(), seed.getPages()));
                matchedSeeded++;
                if (item.isRequired()) {
                    matchedRequired++;
                }
            }
            dto.getKnowledgeBindings().add(item);
        }

        dto.setRequiredCount(requiredCount);
        dto.setMatchedRequiredCount(matchedRequired);
        dto.setMatchedSeededKnowledgeBaseCount(matchedSeeded);
        addCaseChecks(dto, template);
        dto.setCheckCount(dto.getCaseChecks().size());
        dto.setPassedCheckCount((int) dto.getCaseChecks().stream().filter(TemplateHealthDTO.CaseCheckHealth::isPassed).count());
        addApplicationEvidence(dto, template, workspaceId);
        dto.setReady(requiredCount == matchedRequired && dto.getPassedCheckCount() == dto.getCheckCount());
        return dto;
    }

    private void addApplicationEvidence(TemplateHealthDTO dto, TemplateDTO template, Long workspaceId) {
        if (template.getId() == null) {
            return;
        }
        List<AgentEntity> appliedAgents = agentService.listAgentsByWorkspace(workspaceId).stream()
                .filter(agent -> template.getId().equals(agent.getTemplateId()))
                .toList();
        dto.setAppliedAgentCount(appliedAgents.size());

        List<TemplateHealthDTO.AppliedAgentHealth> evidence = appliedAgents.stream()
                .limit(5)
                .map(agent -> buildAppliedAgentHealth(agent, template))
                .toList();
        dto.setAppliedAgents(evidence);
        dto.setApplicationEvidenceReady(!evidence.isEmpty() && evidence.stream().allMatch(this::isAppliedAgentEvidenceReady));
    }

    private TemplateHealthDTO.AppliedAgentHealth buildAppliedAgentHealth(AgentEntity agent, TemplateDTO template) {
        TemplateHealthDTO.AppliedAgentHealth item = new TemplateHealthDTO.AppliedAgentHealth();
        item.setAgentId(agent.getId());
        item.setName(agent.getName());
        item.setEnabled(Boolean.TRUE.equals(agent.getEnabled()));

        List<TemplateDTO.WorkspaceFileTemplate> expectedFiles = template.getWorkspaceFiles() != null
                ? template.getWorkspaceFiles()
                : List.of();
        Set<String> promptFiles = new HashSet<>(workspaceFileService.getPromptFiles(agent.getId()));
        int matchedFiles = 0;
        for (TemplateDTO.WorkspaceFileTemplate expected : expectedFiles) {
            if (expected.getFilename() == null || expected.getFilename().isBlank()) {
                continue;
            }
            if (workspaceFileService.getFile(agent.getId(), expected.getFilename()) != null) {
                matchedFiles++;
            }
        }
        item.setExpectedWorkspaceFileCount(expectedFiles.size());
        item.setMatchedWorkspaceFileCount(matchedFiles);
        item.setMemoryFilePresent(workspaceFileService.getFile(agent.getId(), "MEMORY.md") != null);
        item.setAcceptanceFilePresent(workspaceFileService.getFile(agent.getId(), "ACCEPTANCE.md") != null);
        item.setPromptFilesConfigured(expectedFiles.stream()
                .filter(file -> Boolean.TRUE.equals(file.getEnabled()))
                .map(TemplateDTO.WorkspaceFileTemplate::getFilename)
                .filter(name -> name != null && !name.isBlank())
                .allMatch(promptFiles::contains));
        return item;
    }

    private boolean isAppliedAgentEvidenceReady(TemplateHealthDTO.AppliedAgentHealth item) {
        return item.isMemoryFilePresent()
                && item.isAcceptanceFilePresent()
                && item.isPromptFilesConfigured()
                && item.getExpectedWorkspaceFileCount() == item.getMatchedWorkspaceFileCount();
    }

    private void addCaseChecks(TemplateHealthDTO dto, TemplateDTO template) {
        addCaseCheck(dto, "profile", "Agent profile",
                template.getAgentProfile() != null && !template.getAgentProfile().isEmpty(),
                extractString(template.getAgentProfile(), "profileId"));
        addCaseCheck(dto, "capability_pack", "Capability pack",
                template.getCapabilityPack() != null && !template.getCapabilityPack().isEmpty(),
                extractString(template.getCapabilityPack(), "packId"));
        addCaseCheck(dto, "runtime", "Runtime mode",
                template.getRuntime() != null && template.getRuntime().get("preferredMode") != null,
                template.getRuntime() != null ? String.valueOf(template.getRuntime().get("preferredMode")) : null);
        addCaseCheck(dto, "tools", "Declared tools",
                template.getTools() != null && !template.getTools().isEmpty(),
                template.getTools() != null ? template.getTools().size() + " tools" : "0 tools");
        addCaseCheck(dto, "memory_file", "Memory file",
                hasWorkspaceFile(template, "MEMORY.md"),
                hasWorkspaceFile(template, "MEMORY.md") ? "MEMORY.md" : "missing");
        addCaseCheck(dto, "acceptance_file", "Acceptance file",
                hasWorkspaceFile(template, "ACCEPTANCE.md"),
                hasWorkspaceFile(template, "ACCEPTANCE.md") ? "ACCEPTANCE.md" : "missing");
        addCaseCheck(dto, "mock_acceptance", "Mock acceptance tasks",
                template.getMockAcceptanceTasks() != null && !template.getMockAcceptanceTasks().isEmpty(),
                template.getMockAcceptanceTasks() != null ? template.getMockAcceptanceTasks().size() + " tasks" : "0 tasks");
    }

    private void addCaseCheck(TemplateHealthDTO dto, String key, String label, boolean passed, String detail) {
        TemplateHealthDTO.CaseCheckHealth check = new TemplateHealthDTO.CaseCheckHealth();
        check.setKey(key);
        check.setLabel(label);
        check.setPassed(passed);
        check.setDetail(detail);
        dto.getCaseChecks().add(check);
    }

    private boolean hasWorkspaceFile(TemplateDTO template, String filename) {
        if (template.getWorkspaceFiles() == null || filename == null) {
            return false;
        }
        return template.getWorkspaceFiles().stream()
                .anyMatch(file -> filename.equalsIgnoreCase(file.getFilename()));
    }

    private int countMatchedSeedPages(Long kbId, List<TemplateDTO.DefaultWikiPageTemplate> pages) {
        if (kbId == null || pages == null || pages.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TemplateDTO.DefaultWikiPageTemplate page : pages) {
            if (page.getSlug() != null && wikiPageService.getBySlug(kbId, page.getSlug()) != null) {
                count++;
            }
        }
        return count;
    }

    private int seedDefaultWikiPages(WikiKnowledgeBaseEntity kb, List<TemplateDTO.DefaultWikiPageTemplate> pages) {
        if (kb == null || pages == null || pages.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (TemplateDTO.DefaultWikiPageTemplate pageTemplate : pages) {
            if (pageTemplate.getSlug() == null || pageTemplate.getSlug().isBlank()) {
                continue;
            }
            WikiPageEntity existing = wikiPageService.getBySlug(kb.getId(), pageTemplate.getSlug().trim());
            if (existing != null) {
                continue;
            }
            wikiPageService.createPage(
                    kb.getId(),
                    pageTemplate.getSlug().trim(),
                    pageTemplate.getTitle(),
                    pageTemplate.getContent(),
                    pageTemplate.getSummary(),
                    "[]",
                    pageTemplate.getPageType() != null ? pageTemplate.getPageType() : "template_seed");
            inserted++;
        }
        if (inserted > 0) {
            wikiKnowledgeBaseService.setPageCount(kb.getId(), wikiPageService.countByKbId(kb.getId()));
        }
        return inserted;
    }

    private record SeedSyncResult(int createdKnowledgeBaseCount, int createdKnowledgePageCount) {
        private static final SeedSyncResult EMPTY = new SeedSyncResult(0, 0);
    }

    private String extractString(Map<String, Object> source, String key) {
        if (source == null || key == null) {
            return null;
        }
        Object value = source.get(key);
        return value != null ? value.toString() : null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private boolean repairTemplateDerivedAgent(AgentEntity agent, TemplateDTO template) {
        boolean changed = false;

        String expectedName = firstNonBlank(template.getNameZh(), template.getName());
        if (shouldRepairTextField(agent.getName(), expectedName)) {
            agent.setName(expectedName);
            changed = true;
        }

        String expectedDescription = firstNonBlank(template.getDescriptionZh(), template.getDescription());
        if (shouldRepairTextField(agent.getDescription(), expectedDescription)) {
            agent.setDescription(expectedDescription);
            changed = true;
        }

        if (shouldRepairTextField(agent.getSystemPrompt(), template.getSystemPrompt())) {
            agent.setSystemPrompt(template.getSystemPrompt());
            changed = true;
        }

        if (shouldRepairTextField(agent.getHomeSubtitle(), template.getHomeSubtitle())) {
            agent.setHomeSubtitle(template.getHomeSubtitle());
            changed = true;
        }

        String expectedHomeQuickStartsJson = toJsonOrNull(template.getHomeQuickStarts());
        if (shouldRepairStructuredField(agent.getHomeQuickStartsJson(), expectedHomeQuickStartsJson)) {
            agent.setHomeQuickStartsJson(expectedHomeQuickStartsJson);
            changed = true;
        }

        String expectedMetadataJson = toTemplateMetadataJson(template);
        if (shouldRepairStructuredField(agent.getTemplateMetadataJson(), expectedMetadataJson)) {
            agent.setTemplateMetadataJson(expectedMetadataJson);
            changed = true;
        }

        return changed;
    }

    private boolean shouldRepairTextField(String current, String expected) {
        return expected != null
                && !expected.isBlank()
                && current != null
                && !current.isBlank()
                && looksLikeUtf8Mojibake(current)
                && containsCjk(expected);
    }

    private boolean shouldRepairStructuredField(String current, String expected) {
        return expected != null
                && !expected.isBlank()
                && current != null
                && !current.isBlank()
                && looksLikeUtf8Mojibake(current);
    }

    private boolean looksLikeUtf8Mojibake(String value) {
        if (value == null || value.isBlank() || containsCjk(value)) {
            return false;
        }
        int markerHits = 0;
        String[] markers = {"Ã", "Â", "å", "ä", "æ", "ç", "é", "è", "ï", "ð", "œ", "€", "™", "鈥", "馃"};
        for (String marker : markers) {
            if (value.contains(marker)) {
                markerHits++;
            }
        }
        return markerHits >= 2;
    }

    private boolean containsCjk(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String toTemplateMetadataJson(TemplateDTO template) {
        Map<String, Object> metadata = objectMapper.convertValue(template, new TypeReference<>() {});
        metadata.remove("systemPrompt");
        metadata.remove("workspaceFiles");
        metadata.put("templateId", template.getId());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("template", metadata);
        manifest.put("capturedAt", java.time.LocalDateTime.now().toString());
        manifest.put("schemaVersion", 1);
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize template metadata for {}", template.getId(), e);
            return null;
        }
    }

    private String toJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize template value", e);
            return null;
        }
    }
}
