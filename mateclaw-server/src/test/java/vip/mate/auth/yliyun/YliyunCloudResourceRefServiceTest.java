package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vip.mate.agent.context.ChatOrigin;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YliyunCloudResourceRefServiceTest {

    @Mock
    private YliyunUserMappingService userMappingService;

    private YliyunCloudResourceRefService service;
    private McWorkspaceUserEntity mapping;
    private ChatOrigin origin;

    @BeforeEach
    void setUp() {
        YliyunTicketKeyRing keyRing = new YliyunTicketKeyRing(
                "resource-ref-test-secret-0123456789abcdef",
                "",
                "test-current",
                "test-previous");
        keyRing.validate();
        service = new YliyunCloudResourceRefService(
                keyRing, userMappingService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "appKey", "mateclaw_ai_assistant");
        ReflectionTestUtils.setField(service, "ttlSeconds", 3600L);

        mapping = mapping("1", "100", 9L, 42L, 7);
        origin = ChatOrigin.web("conv-1", "yliyun_1_100", 42L, null, null, 9L);
    }

    @Test
    void issuesAndResolvesReferenceBoundToCurrentCloudIdentity() {
        when(userMappingService.findMappingByMateUserId(9L)).thenReturn(Optional.of(mapping));

        var issued = service.issue(new YliyunCloudResourceRefService.IssueRequest(
                "file", "17485", "项目文档.md", "v3", "current-preview", "text/markdown"), 9L);
        var resolved = service.resolvePath(issued.path(), origin);

        assertThat(issued.path()).startsWith("yliyun-ref://");
        assertThat(issued.refId()).doesNotContain("17485");
        assertThat(resolved.resourceType()).isEqualTo("file");
        assertThat(resolved.resourceId()).isEqualTo("17485");
        assertThat(resolved.binding()).isEqualTo("current-preview");
        assertThat(resolved.versionId()).isEqualTo("v3");
    }

    @Test
    void rebindsVerifiedReferenceWithoutTrustingARawResourceId() {
        when(userMappingService.findMappingByMateUserId(9L)).thenReturn(Optional.of(mapping));

        var issued = service.issue(new YliyunCloudResourceRefService.IssueRequest(
                "file", "17485", "项目文档.md", "v3", "current-preview", "text/markdown"), 9L);
        var pinned = service.rebind(new YliyunCloudResourceRefService.RebindRequest(
                issued.refId(), "pinned"), 9L);
        var resolved = service.resolvePath(pinned.path(), origin);

        assertThat(pinned.refId()).isNotEqualTo(issued.refId());
        assertThat(resolved.resourceId()).isEqualTo("17485");
        assertThat(resolved.binding()).isEqualTo("pinned");
    }

    @Test
    void rejectsTamperedReference() {
        when(userMappingService.findMappingByMateUserId(9L)).thenReturn(Optional.of(mapping));
        var issued = service.issue(new YliyunCloudResourceRefService.IssueRequest(
                "file", "17485", "项目文档.md", null, "mention", null), 9L);
        String tampered = issued.path().substring(0, issued.path().length() - 1)
                + (issued.path().endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> service.resolvePath(tampered, origin))
                .hasMessageContaining("签名无效");
    }

    @Test
    void rejectsReferenceReplayedByAnotherTenantUser() {
        when(userMappingService.findMappingByMateUserId(9L)).thenReturn(Optional.of(mapping));
        var issued = service.issue(new YliyunCloudResourceRefService.IssueRequest(
                "folder", "200", "合同", null, "pinned", null), 9L);

        McWorkspaceUserEntity other = mapping("135", "100", 10L, 99L, 7);
        when(userMappingService.findMappingByMateUserId(10L)).thenReturn(Optional.of(other));
        ChatOrigin otherOrigin = ChatOrigin.web(
                "conv-2", "yliyun_135_100", 99L, null, null, 10L);

        assertThatThrownBy(() -> service.resolvePath(issued.path(), otherOrigin))
                .hasMessageContaining("不属于当前租户、用户或工作空间");
    }

    @Test
    void rejectsUnsafeResourceContractValuesBeforeSigning() {
        when(userMappingService.findMappingByMateUserId(9L)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> service.issue(
                new YliyunCloudResourceRefService.IssueRequest(
                        "file", "-1", "x", null, "current-preview", null), 9L))
                .hasMessageContaining("resourceId 格式无效");
        assertThatThrownBy(() -> service.issue(
                new YliyunCloudResourceRefService.IssueRequest(
                        "file", "1", "x", null, "unknown", null), 9L))
                .hasMessageContaining("binding 仅支持");
    }

    @Test
    void claimsMalformedSignedNamespaceAndFailsClosedDuringResolution() {
        assertThat(service.supports("yliyun-ref://not-a-valid-ref")).isTrue();
        assertThatThrownBy(() -> service.resolvePath(
                "yliyun-ref://not-a-valid-ref", origin))
                .hasMessageContaining("格式无效");
    }

    private McWorkspaceUserEntity mapping(
            String tenantId,
            String cloudUserId,
            Long mateUserId,
            Long workspaceId,
            int configVersion) {
        McWorkspaceUserEntity value = new McWorkspaceUserEntity();
        value.setYliyunTenantId(tenantId);
        value.setYliyunUserId(cloudUserId);
        value.setUserId(mateUserId);
        value.setWorkspaceId(workspaceId);
        value.setAppKey("mateclaw_ai_assistant");
        value.setConfigVersion(configVersion);
        value.setDeleted(0);
        return value;
    }
}
