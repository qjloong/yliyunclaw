package vip.mate.wiki.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.repository.WikiKnowledgeBaseMapper;

import java.util.List;

/**
 * Wiki 知识库服务
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiKnowledgeBaseService {

    private final WikiKnowledgeBaseMapper kbMapper;
    private final AgentMapper agentMapper;
    private final ObjectMapper objectMapper;

    /**
     * RFC-051 PR-2: optional system-page scaffold (overview / log). Marked
     * required=false + Lazy so the KB service has no construction dependency
     * on a service that needs WikiPageService — handy for the older tests that
     * still wire this class manually.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private WikiScaffoldService scaffoldService;

    private static final String DEFAULT_CONFIG = """
            # Wiki Processing Rules

            ## Quality First
            - Create high-quality pages — prefer fewer complete pages over many shallow ones
            - Each page focuses on one concept, entity, or process
            - A page must have at least 3 sentences of substantive content
            - Target 3-5 pages per source material (not 10-15)
            - If a concept already exists in the wiki, update it instead of duplicating

            ## Format
            - Use clear Markdown headers (## and ###)
            - Include a one-paragraph summary at the top of each page
            - Use [[Page Title]] syntax for bidirectional links between pages

            ## Updates
            - Merge new information into existing pages, do not replace
            - Preserve manually edited content (last_updated_by = 'manual')
            - Mark contradictions clearly with a "Note:" annotation

            ## Language
            - Write wiki pages in the same language as the source material
            - Keep technical terms consistent across pages
            """;

    public List<WikiKnowledgeBaseEntity> listAll() {
        return kbMapper.selectList(
                new LambdaQueryWrapper<WikiKnowledgeBaseEntity>()
                        .orderByDesc(WikiKnowledgeBaseEntity::getUpdateTime));
    }

    /**
     * 按工作区列出知识库
     */
    public List<WikiKnowledgeBaseEntity> listByWorkspace(Long workspaceId) {
        return kbMapper.selectList(
                new LambdaQueryWrapper<WikiKnowledgeBaseEntity>()
                        .eq(WikiKnowledgeBaseEntity::getWorkspaceId, workspaceId)
                        .orderByDesc(WikiKnowledgeBaseEntity::getUpdateTime));
    }

    public WikiKnowledgeBaseEntity findByWorkspaceAndExternalKey(Long workspaceId, String externalKey) {
        if (workspaceId == null || externalKey == null || externalKey.isBlank()) {
            return null;
        }
        return kbMapper.selectOne(
                new LambdaQueryWrapper<WikiKnowledgeBaseEntity>()
                        .eq(WikiKnowledgeBaseEntity::getWorkspaceId, workspaceId)
                        .eq(WikiKnowledgeBaseEntity::getExternalKey, externalKey.trim())
                        .last("LIMIT 1"));
    }

    public WikiKnowledgeBaseEntity findByWorkspaceAndName(Long workspaceId, String name) {
        if (workspaceId == null || name == null || name.isBlank()) {
            return null;
        }
        return kbMapper.selectOne(
                new LambdaQueryWrapper<WikiKnowledgeBaseEntity>()
                        .eq(WikiKnowledgeBaseEntity::getWorkspaceId, workspaceId)
                        .eq(WikiKnowledgeBaseEntity::getName, name.trim())
                        .last("LIMIT 1"));
    }

    /**
     * 获取 Agent 可访问的知识库：Agent 专属 KB + 公共 KB（agent_id IS NULL）
     */
    public List<WikiKnowledgeBaseEntity> listByAgentId(Long agentId) {
        AgentEntity agent = agentId != null ? agentMapper.selectById(agentId) : null;
        List<Long> boundIds = parseKnowledgeBaseIds(agent != null ? agent.getKnowledgeBaseIdsJson() : null);
        if (agent == null || boundIds.isEmpty()) {
            return List.of();
        }
        return kbMapper.selectList(
                new LambdaQueryWrapper<WikiKnowledgeBaseEntity>()
                        .in(WikiKnowledgeBaseEntity::getId, boundIds)
                        .eq(WikiKnowledgeBaseEntity::getWorkspaceId, agent.getWorkspaceId())
                        .orderByDesc(WikiKnowledgeBaseEntity::getUpdateTime));
    }

    private List<Long> parseKnowledgeBaseIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Long>>() {}).stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public WikiKnowledgeBaseEntity getById(Long id) {
        return kbMapper.selectById(id);
    }

    @Transactional
    public WikiKnowledgeBaseEntity create(String name, String description, Long agentId) {
        return create(name, description, agentId, 1L);
    }

    @Transactional
    public WikiKnowledgeBaseEntity create(String name, String description, Long agentId, Long workspaceId) {
        return create(name, description, agentId, workspaceId, null);
    }

    @Transactional
    public WikiKnowledgeBaseEntity create(String name, String description, Long agentId,
                                          Long workspaceId, String externalKey) {
        return create(name, description, agentId, workspaceId, externalKey, null);
    }

    @Transactional
    public WikiKnowledgeBaseEntity create(String name, String description, Long agentId,
                                          Long workspaceId, String externalKey, Long creatorUserId) {
        WikiKnowledgeBaseEntity entity = new WikiKnowledgeBaseEntity();
        entity.setName(name);
        entity.setExternalKey(externalKey);
        entity.setDescription(description);
        entity.setAgentId(agentId);
        entity.setWorkspaceId(workspaceId);
        entity.setCreatorUserId(creatorUserId);
        entity.setConfigContent(DEFAULT_CONFIG);
        entity.setStatus("active");
        entity.setPageCount(0);
        entity.setRawCount(0);
        kbMapper.insert(entity);
        log.info("[Wiki] Knowledge base created: id={}, name={}, workspaceId={}", entity.getId(), name, workspaceId);
        // RFC-051 PR-2: ensure overview / log system pages exist for every new KB.
        if (scaffoldService != null) {
            scaffoldService.ensureScaffold(entity.getId());
        }
        return entity;
    }

    @Transactional
    public WikiKnowledgeBaseEntity update(Long id, String name, String description, Long agentId) {
        return update(id, name, description, agentId, null, false);
    }

    @Transactional
    public WikiKnowledgeBaseEntity update(Long id, String name, String description, Long agentId,
                                          String externalKey, boolean updateExternalKey) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + id);
        }
        if (name != null) entity.setName(name);
        if (description != null) entity.setDescription(description);
        if (agentId != null) entity.setAgentId(agentId);
        if (updateExternalKey) entity.setExternalKey(externalKey);
        kbMapper.updateById(entity);
        return entity;
    }

    /**
     * 更新 KB 绑定的 embedding 模型 ID。
     * <p>
     * 切换模型后，旧的向量维度/语义空间与新模型不一致，下次搜索/处理时会被
     * WikiEmbeddingService 自动检测为"model 不匹配"触发重嵌。
     * 这里不主动清空 embedding（让 embed_model 字段的差异自己触发重建）。
     *
     * @param embeddingModelId null 表示解绑（走系统默认）
     */
    @Transactional
    public void updateEmbeddingModelId(Long id, Long embeddingModelId) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + id);
        }
        entity.setEmbeddingModelId(embeddingModelId);
        kbMapper.updateById(entity);
    }

    @Transactional
    public void updateConfig(Long id, String configContent) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + id);
        }
        entity.setConfigContent(configContent);
        kbMapper.updateById(entity);
    }

    @Transactional
    public void updateCounts(Long kbId) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(kbId);
        if (entity == null) return;
        // counts will be updated by callers via specific methods
        kbMapper.updateById(entity);
    }

    @Transactional
    public void updateStatus(Long kbId, String status) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(kbId);
        if (entity == null) return;
        entity.setStatus(status);
        kbMapper.updateById(entity);
    }

    @Transactional
    public void incrementRawCount(Long kbId) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(kbId);
        if (entity == null) return;
        entity.setRawCount(entity.getRawCount() + 1);
        kbMapper.updateById(entity);
    }

    @Transactional
    public void setPageCount(Long kbId, int count) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(kbId);
        if (entity == null) return;
        entity.setPageCount(count);
        kbMapper.updateById(entity);
    }

    @Transactional
    public void updateSourceDirectory(Long id, String path) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + id);
        }
        entity.setSourceDirectory(path);
        kbMapper.updateById(entity);
    }

    @Transactional
    public void decrementRawCount(Long kbId) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(kbId);
        if (entity == null) return;
        entity.setRawCount(Math.max(0, entity.getRawCount() - 1));
        kbMapper.updateById(entity);
    }

    /**
     * 更新知识库的 workspace 归属
     */
    public void updateWorkspaceId(Long kbId, Long workspaceId) {
        WikiKnowledgeBaseEntity entity = kbMapper.selectById(kbId);
        if (entity != null) {
            entity.setWorkspaceId(workspaceId);
            kbMapper.updateById(entity);
        }
    }

    @Transactional
    public void delete(Long id) {
        kbMapper.deleteById(id);
        log.info("[Wiki] Knowledge base deleted: id={}", id);
    }
}
