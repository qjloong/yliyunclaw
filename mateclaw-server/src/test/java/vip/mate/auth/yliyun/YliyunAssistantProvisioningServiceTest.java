package vip.mate.auth.yliyun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vip.mate.agent.AgentService;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.agent.service.TemplateService;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.repository.WorkspaceMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YliyunAssistantProvisioningServiceTest {

    private WorkspaceMapper workspaceMapper;
    private AgentMapper agentMapper;
    private TemplateService templateService;
    private AgentService agentService;
    private YliyunAssistantProvisioningService service;

    @BeforeEach
    void setUp() {
        workspaceMapper = mock(WorkspaceMapper.class);
        agentMapper = mock(AgentMapper.class);
        templateService = mock(TemplateService.class);
        agentService = mock(AgentService.class);
        service = new YliyunAssistantProvisioningService(
                workspaceMapper, agentMapper, templateService, agentService);
    }

    @Test
    void freshSsoReactivatesExistingAssistantWithoutRecreatingWorkspaceData() {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(81L);
        AgentEntity assistant = assistant(91L, 81L, false);
        when(workspaceMapper.selectOne(any())).thenReturn(workspace);
        when(agentMapper.selectOne(any())).thenReturn(assistant);

        AgentEntity result = service.ensureAssistant(81L, 100L);

        assertSame(assistant, result);
        assertTrue(result.getEnabled());
        verify(agentService).updateAgent(assistant);
    }

    @Test
    void deactivationDisablesOnlyEnabledApplicationAssistants() {
        AgentEntity enabled = assistant(91L, 81L, true);
        AgentEntity alreadyDisabled = assistant(92L, 82L, false);
        when(agentMapper.selectList(any())).thenReturn(List.of(enabled, alreadyDisabled));

        int updated = service.deactivateAssistants(List.of(81L, 82L));

        assertEquals(1, updated);
        assertEquals(false, enabled.getEnabled());
        verify(agentService).updateAgent(enabled);
    }

    private AgentEntity assistant(Long id, Long workspaceId, boolean enabled) {
        AgentEntity assistant = new AgentEntity();
        assistant.setId(id);
        assistant.setWorkspaceId(workspaceId);
        assistant.setTemplateId(YliyunAssistantProvisioningService.TEMPLATE_ID);
        assistant.setEnabled(enabled);
        assistant.setDeleted(0);
        return assistant;
    }
}
