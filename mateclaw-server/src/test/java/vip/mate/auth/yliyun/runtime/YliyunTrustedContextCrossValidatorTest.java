package vip.mate.auth.yliyun.runtime;

import org.junit.jupiter.api.Test;
import vip.mate.auth.yliyun.McWorkspaceUserEntity;
import vip.mate.auth.yliyun.repository.McWorkspaceUserMapper;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.repository.WorkspaceMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YliyunTrustedContextCrossValidatorTest {

    @Test
    void validatesUserWorkspaceVersionAndEntitlement() {
        McWorkspaceUserMapper userMapper = mock(McWorkspaceUserMapper.class);
        WorkspaceMapper workspaceMapper = mock(WorkspaceMapper.class);
        YliyunRuntimeSecurityProperties properties = new YliyunRuntimeSecurityProperties();
        properties.getAppEntitlements().put("GOAL", "mateclaw_ai_assistant");

        McWorkspaceUserEntity mapping = mapping();
        WorkspaceEntity workspace = workspace();
        when(userMapper.selectList(any())).thenReturn(List.of(mapping));
        when(workspaceMapper.selectById(10L)).thenReturn(workspace);

        YliyunTrustedContextCrossValidator validator =
                new YliyunTrustedContextCrossValidator(userMapper, workspaceMapper, properties);
        assertSame(mapping, validator.validate(context(12)));
    }

    @Test
    void rejectsStaleSourceVersion() {
        McWorkspaceUserMapper userMapper = mock(McWorkspaceUserMapper.class);
        WorkspaceMapper workspaceMapper = mock(WorkspaceMapper.class);
        YliyunRuntimeSecurityProperties properties = new YliyunRuntimeSecurityProperties();
        when(userMapper.selectList(any())).thenReturn(List.of(mapping()));
        when(workspaceMapper.selectById(10L)).thenReturn(workspace());

        YliyunTrustedContextCrossValidator validator =
                new YliyunTrustedContextCrossValidator(userMapper, workspaceMapper, properties);
        YliyunRuntimeAuthException ex = assertThrows(
                YliyunRuntimeAuthException.class, () -> validator.validate(context(11)));
        assertEquals("CONTEXT_VERSION_MISMATCH", ex.getErrorCode());
    }

    private static McWorkspaceUserEntity mapping() {
        McWorkspaceUserEntity row = new McWorkspaceUserEntity();
        row.setWorkspaceId(10L);
        row.setUserId(20L);
        row.setYliyunTenantId("1001");
        row.setYliyunUserId("2001");
        row.setAppKey("mateclaw_ai_assistant");
        row.setConfigVersion(12);
        row.setDeleted(0);
        return row;
    }

    private static WorkspaceEntity workspace() {
        WorkspaceEntity row = new WorkspaceEntity();
        row.setId(10L);
        row.setSlug("goal-main");
        row.setDeleted(0);
        return row;
    }

    private static YliyunTrustedContext context(long version) {
        long now = Instant.now().getEpochSecond();
        return new YliyunTrustedContext(
                1001, 2001, "ws_10", "GOAL", "GOAL_PROJECT", "3001",
                null, "run_x1", "550e8400-e29b-41d4-a716-446655440000",
                List.of("OWNER"), List.of("PROJECT_READ"), version,
                now - 1, now + 59);
    }
}
