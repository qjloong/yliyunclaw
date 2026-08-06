package vip.mate.auth.yliyun.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import vip.mate.auth.yliyun.McWorkspaceUserEntity;
import vip.mate.auth.yliyun.repository.McWorkspaceUserMapper;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.repository.WorkspaceMapper;

import java.util.List;
import java.util.Objects;

/** Cross-checks signed cloud identity with MateClaw workspace membership. */
@Service
public class YliyunTrustedContextCrossValidator {

    private final McWorkspaceUserMapper workspaceUserMapper;
    private final WorkspaceMapper workspaceMapper;
    private final YliyunRuntimeSecurityProperties properties;

    public YliyunTrustedContextCrossValidator(
            McWorkspaceUserMapper workspaceUserMapper,
            WorkspaceMapper workspaceMapper,
            YliyunRuntimeSecurityProperties properties) {
        this.workspaceUserMapper = workspaceUserMapper;
        this.workspaceMapper = workspaceMapper;
        this.properties = properties;
    }

    public McWorkspaceUserEntity validate(YliyunTrustedContext context) {
        List<McWorkspaceUserEntity> rows = workspaceUserMapper.selectList(
                new LambdaQueryWrapper<McWorkspaceUserEntity>()
                        .eq(McWorkspaceUserEntity::getYliyunTenantId,
                                String.valueOf(context.tenantId()))
                        .eq(McWorkspaceUserEntity::getYliyunUserId,
                                String.valueOf(context.userId()))
                        .eq(McWorkspaceUserEntity::getDeleted, 0));
        if (rows.isEmpty()) {
            throw contextRejected("BINDING_NOT_FOUND",
                    "An active cloud user binding was not found");
        }
        if (rows.size() > 1) {
            throw new YliyunRuntimeAuthException(
                    "BINDING_DUPLICATE", 409,
                    "Multiple active cloud user bindings were found",
                    "auth.binding_validation",
                    "Remove duplicate active cloud user bindings", false);
        }

        McWorkspaceUserEntity mapping = rows.getFirst();
        WorkspaceEntity workspace = workspaceMapper.selectById(mapping.getWorkspaceId());
        if (workspace == null || Integer.valueOf(1).equals(workspace.getDeleted())) {
            throw contextRejected("BINDING_NOT_FOUND", "The mapped workspace is unavailable");
        }

        String byId = "ws_" + workspace.getId();
        String bySlug = workspace.getSlug() == null ? null : "ws_" + workspace.getSlug();
        if (!context.workspaceId().equals(byId)
                && !context.workspaceId().equals(bySlug)) {
            throw contextRejected("CONTEXT_REVOKED",
                    "Trusted Context workspace does not match the user binding");
        }

        if (mapping.getConfigVersion() == null
                || mapping.getConfigVersion().longValue() != context.sourceVersion()) {
            throw new YliyunRuntimeAuthException(
                    "CONTEXT_VERSION_MISMATCH", 401,
                    "Trusted Context sourceVersion is no longer current",
                    "auth.binding_validation",
                    "Regenerate Trusted Context using the current application configuration", false);
        }

        if (mapping.getAppKey() == null || mapping.getAppKey().isBlank()) {
            throw contextRejected("CONTEXT_REVOKED", "The application entitlement is missing");
        }
        String expectedAppKey = properties.getAppEntitlements().get(context.appCode());
        if (expectedAppKey != null && !expectedAppKey.isBlank()
                && !Objects.equals(expectedAppKey, mapping.getAppKey())) {
            throw contextRejected("CONTEXT_REVOKED",
                    "The application entitlement does not match appCode");
        }
        return mapping;
    }

    private static YliyunRuntimeAuthException contextRejected(String code, String message) {
        return new YliyunRuntimeAuthException(
                code, 403, message,
                "auth.binding_validation",
                "Refresh the cloud application binding and issue a new context", false);
    }
}
