package vip.mate.auth.yliyun;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.agent.service.TemplateService;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.repository.WorkspaceMapper;

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

    public YliyunAssistantProvisioningService(
            WorkspaceMapper workspaceMapper,
            AgentMapper agentMapper,
            @Lazy TemplateService templateService) {
        this.workspaceMapper = workspaceMapper;
        this.agentMapper = agentMapper;
        this.templateService = templateService;
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
            return existing;
        }

        AgentEntity created = templateService.applyTemplate(
                TEMPLATE_ID, workspaceId, creatorUserId, "zh-CN");
        log.info("[Yliyun] Provisioned cloud assistant: workspaceId={}, agentId={}",
                workspaceId, created.getId());
        return created;
    }
}
