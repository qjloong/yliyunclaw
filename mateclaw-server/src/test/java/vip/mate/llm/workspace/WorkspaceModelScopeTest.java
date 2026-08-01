package vip.mate.llm.workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vip.mate.tool.builtin.ToolExecutionContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkspaceModelScopeTest {

    private final WorkspaceModelScope scope = new WorkspaceModelScope();

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        ToolExecutionContext.clear();
    }

    @Test
    void defaultsToLegacyWorkspaceWhenNoContextExists() {
        assertEquals(WorkspaceModelScope.DEFAULT_WORKSPACE_ID,
                scope.currentWorkspaceId());
    }

    @Test
    void resolvesWorkspaceFromRequiredRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Workspace-Id", "203");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals(203L, scope.currentWorkspaceId());
    }

    @Test
    void explicitAgentScopeOverridesRequestAndRestoresIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Workspace-Id", "203");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try (WorkspaceModelScope.Scope ignored = scope.open(409L)) {
            assertEquals(409L, scope.currentWorkspaceId());
        }

        assertEquals(203L, scope.currentWorkspaceId());
    }

    @Test
    void toolExecutionWorkspaceIsAvailableOutsideHttpRequest() {
        ToolExecutionContext.set("conv-1", "alice", 702L, "D:/workspace");

        assertEquals(702L, scope.currentWorkspaceId());
    }
}
