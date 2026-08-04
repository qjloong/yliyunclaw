package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.tool.mcp.model.McpServerEntity;
import vip.mate.tool.mcp.runtime.McpClientManager;
import vip.mate.tool.mcp.service.McpServerService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YliyunCloudResourceStatusServiceTest {

    @Mock
    private YliyunCloudResourceRefService resourceRefService;
    @Mock
    private McpServerService mcpServerService;
    @Mock
    private McpClientManager mcpClientManager;

    private YliyunCloudResourceStatusService service;
    private ChatOrigin origin;

    @BeforeEach
    void setUp() {
        service = new YliyunCloudResourceStatusService(
                resourceRefService, mcpServerService, mcpClientManager, new ObjectMapper());
        McpServerEntity server = new McpServerEntity();
        server.setId(7L);
        when(mcpServerService.getByName("yliyun-mcp")).thenReturn(server);
        origin = ChatOrigin.web("conv-1", "yliyun_1_100", 42L, null, null, 9L);
    }

    @Test
    void reportsCurrentAndUpdatedFromServerVersionToken() {
        when(mcpClientManager.callTool(eq(7L), eq("file.versions"), any(), any()))
                .thenReturn(McpClientManager.ToolCallResult.success("""
                        {"currentVersionId":"v-12","resource":{"name":"项目.md","mimeType":"text/markdown"},"versions":[]}
                        """, 5));

        var current = service.inspect(ref("v-12"), origin);
        var updated = service.inspect(ref("v-11"), origin);

        assertThat(current.state()).isEqualTo(
                YliyunCloudResourceStatusService.ResourceState.CURRENT);
        assertThat(updated.state()).isEqualTo(
                YliyunCloudResourceStatusService.ResourceState.UPDATED);
        assertThat(updated.currentVersionId()).isEqualTo("v-12");
        assertThat(updated.displayName()).isEqualTo("项目.md");
    }

    @Test
    void distinguishesDeletedPermissionRevokedAndUnavailable() {
        when(mcpClientManager.callTool(eq(7L), eq("file.versions"), any(), any()))
                .thenReturn(McpClientManager.ToolCallResult.failure(
                        "FILE_NOT_FOUND", "missing", "cloud.resource", 5))
                .thenReturn(McpClientManager.ToolCallResult.failure(
                        "PERMISSION_DENIED", "denied", "cloud.authorization", 5))
                .thenReturn(McpClientManager.ToolCallResult.failure(
                        "MCP_CALL_FAILED", "offline", "mcp.transport", 5));

        assertThat(service.inspect(ref("v-1"), origin).state()).isEqualTo(
                YliyunCloudResourceStatusService.ResourceState.DELETED);
        assertThat(service.inspect(ref("v-1"), origin).state()).isEqualTo(
                YliyunCloudResourceStatusService.ResourceState.PERMISSION_REVOKED);
        var unavailable = service.inspect(ref("v-1"), origin);
        assertThat(unavailable.state()).isEqualTo(
                YliyunCloudResourceStatusService.ResourceState.UNAVAILABLE);
        assertThat(unavailable.retryable()).isTrue();
    }

    @Test
    void chatGuardRejectsChangedVersionBeforeReadingContent() {
        when(mcpClientManager.callTool(eq(7L), eq("file.versions"), any(), any()))
                .thenReturn(McpClientManager.ToolCallResult.success(
                        "{\"currentVersionId\":\"v-12\",\"resource\":{},\"versions\":[]}", 5));

        assertThatThrownBy(() -> service.requireCurrent(ref("v-11"), origin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("使用最新版本");
    }

    @Test
    void verifiedIssueReplacesClientVersionWithMcpVersion() {
        var request = new YliyunCloudResourceRefService.IssueRequest(
                "file", "17485", "client-name.md", "forged-v999",
                "current-preview", "application/client");
        var provisional = issued("provisional", null);
        var finalIssued = issued("final", "v-12");
        when(resourceRefService.issue(any(), eq(9L)))
                .thenReturn(provisional)
                .thenReturn(finalIssued);
        when(resourceRefService.resolvePath(eq(provisional.path()), any()))
                .thenReturn(ref(null));
        when(mcpClientManager.callTool(eq(7L), eq("file.versions"), any(), any()))
                .thenReturn(McpClientManager.ToolCallResult.success("""
                        {"currentVersionId":"v-12","resource":{"name":"server-name.md","mimeType":"text/markdown"},"versions":[]}
                        """, 5));

        var result = service.issueVerified(request, 9L);

        assertThat(result.versionId()).isEqualTo("v-12");
        ArgumentCaptor<YliyunCloudResourceRefService.IssueRequest> captor =
                ArgumentCaptor.forClass(YliyunCloudResourceRefService.IssueRequest.class);
        verify(resourceRefService, org.mockito.Mockito.times(2)).issue(captor.capture(), eq(9L));
        var finalRequest = captor.getAllValues().get(1);
        assertThat(finalRequest.versionId()).isEqualTo("v-12");
        assertThat(finalRequest.displayName()).isEqualTo("server-name.md");
        assertThat(finalRequest.mimeType()).isEqualTo("text/markdown");
    }

    private YliyunCloudResourceRefService.ResolvedResourceRef ref(String versionId) {
        return new YliyunCloudResourceRefService.ResolvedResourceRef(
                "file", "17485", "项目.md", versionId,
                "current-preview", "text/markdown");
    }

    private YliyunCloudResourceRefService.IssuedResourceRef issued(String id, String versionId) {
        return new YliyunCloudResourceRefService.IssuedResourceRef(
                id, "yliyun-ref://" + id, "file", "17485", "项目.md",
                versionId, "current-preview", "text/markdown", 9999999999L);
    }
}
