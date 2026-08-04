package vip.mate.auth.yliyun;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.agent.AgentService;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.agent.service.TemplateService;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.repository.WorkspaceMapper;

import java.util.Collection;
import java.util.List;

/**
 * Ensures every Yliyun tenant workspace has one ready-to-use cloud assistant.
 *
 * <p>The workspace row is locked while checking and applying the template so
 * concurrent first logins on different application nodes cannot seed duplicate
 * agents. The existing workspace/name uniqueness constraint remains the final
 * database guard.
 */
@Slf4j
@Service
public class YliyunAssistantProvisioningService {

    static final String TEMPLATE_ID = "builtin.yliyun_assistant";

    private final WorkspaceMapper workspaceMapper;
    private final AgentMapper agentMapper;
    private final TemplateService templateService;
    private final AgentService agentService;

    public YliyunAssistantProvisioningService(
            WorkspaceMapper workspaceMapper,
            AgentMapper agentMapper,
            @Lazy TemplateService templateService,
            @Lazy AgentService agentService) {
        this.workspaceMapper = workspaceMapper;
        this.agentMapper = agentMapper;
        this.templateService = templateService;
        this.agentService = agentService;
    }

    @Transactional
    public AgentEntity ensureAssistant(Long workspaceId, Long creatorUserId) {
        if (workspaceId == null || creatorUserId == null) {
            throw new IllegalArgumentException("workspaceId and creatorUserId are required");
        }

        WorkspaceEntity workspace = workspaceMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceEntity>()
                        .eq(WorkspaceEntity::getId, workspaceId)
                        .last("FOR UPDATE"));
        if (workspace == null) {
            throw new IllegalStateException("Yliyun workspace does not exist: " + workspaceId);
        }

        AgentEntity existing = agentMapper.selectOne(
                new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getWorkspaceId, workspaceId)
                        .eq(AgentEntity::getTemplateId, TEMPLATE_ID)
                        .eq(AgentEntity::getDeleted, 0)
                        .last("LIMIT 1"));
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(true);
                agentService.updateAgent(existing);
                log.info("[Yliyun] Reactivated cloud assistant: workspaceId={}, agentId={}",
                        workspaceId, existing.getId());
            }
            return existing;
        }

        AgentEntity created = templateService.applyTemplate(
                TEMPLATE_ID, workspaceId, creatorUserId, "zh-CN");
        log.info("[Yliyun] Provisioned cloud assistant: workspaceId={}, agentId={}",
                workspaceId, created.getId());
        return created;
    }

    /**
     * Reversibly deprovision the application-specific assistant.
     *
     * <p>The tenant workspace, members, conversations and provider credentials
     * are deliberately retained. Re-enabling the cloud application and
     * completing a fresh SSO reactivates the same assistant through
     * {@link #ensureAssistant(Long, Long)}.</p>
     */
    @Transactional
    public int deactivateAssistants(Collection<Long> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0;
        }
        List<AgentEntity> assistants = agentMapper.selectList(
                new LambdaQueryWrapper<AgentEntity>()
                        .in(AgentEntity::getWorkspaceId, workspaceIds)
                        .eq(AgentEntity::getTemplateId, TEMPLATE_ID)
                        .eq(AgentEntity::getDeleted, 0));
        int updated = 0;
        for (AgentEntity assistant : assistants) {
            if (!Boolean.TRUE.equals(assistant.getEnabled())) {
                continue;
            }
            assistant.setEnabled(false);
            agentService.updateAgent(assistant);
            updated++;
        }
        log.info("[Yliyun] Deactivated cloud assistants: workspaces={}, agents={}",
                workspaceIds.size(), updated);
        return updated;
    }
}
