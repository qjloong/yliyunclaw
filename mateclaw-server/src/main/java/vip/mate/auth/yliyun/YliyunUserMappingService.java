package vip.mate.auth.yliyun;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.yliyun.repository.McWorkspaceUserMapper;
import vip.mate.auth.repository.UserMapper;
import vip.mate.exception.MateClawException;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.repository.WorkspaceMapper;
import vip.mate.workspace.core.service.WorkspaceService;

import java.util.List;
import java.util.Optional;

/**
 * Yliyun 用户映射服务
 * <p>
 * 负责在 mc_workspace_user 表中查询或创建 Yliyun 用户到 MateClaw 用户的映射。
 * 映射策略：
 * <ul>
 *   <li>已映射 → 直接返回对应的 MateClaw 用户</li>
 *   <li>未映射 → 创建 MateClaw 用户 + mc_workspace_user 映射记录 + 默认工作区成员</li>
 * </ul>
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YliyunUserMappingService {

    private final McWorkspaceUserMapper workspaceUserMapper;
    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceService workspaceService;
    private final YliyunAssistantProvisioningService assistantProvisioningService;

    @Value("${mateclaw.auth.yliyun.default-role:user}")
    private String defaultRole;

    /**
     * 根据 yliyun 用户 ID 查找或创建 MateClaw 用户映射。
     *
     * @param yliyunUserId   Yliyun 用户 ID
     * @param yliyunTenantId Yliyun 租户 ID（可为 null）
     * @param nickname       Yliyun 用户昵称（可为 null）
     * @return 映射的 MateClaw 用户实体
     */
    @Transactional
    public UserEntity findOrCreateUser(String yliyunUserId, String yliyunTenantId,
                                       String nickname, String account, String tenantName,
                                       Boolean tenantAdmin) {
        String normalizedUserId = requireIdentityPart(yliyunUserId, "userId");
        String normalizedTenantId = requireIdentityPart(yliyunTenantId, "tenantId");
        String displayName = cloudDisplayName(nickname, account, normalizedUserId);

        // 1. 查询已有映射
        McWorkspaceUserEntity mapping = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<McWorkspaceUserEntity>()
                        .eq(McWorkspaceUserEntity::getYliyunTenantId, normalizedTenantId)
                        .eq(McWorkspaceUserEntity::getYliyunUserId, normalizedUserId)
                        .eq(McWorkspaceUserEntity::getDeleted, 0));

        if (mapping != null) {
            UserEntity user = userMapper.selectById(mapping.getUserId());
            if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                throw new MateClawException("err.auth.yliyun.user_disabled", 403, "关联用户已停用或不存在");
            }
            log.debug("[Yliyun] Found existing mapping: tenantId={}, yliyunUserId={} → mateUserId={}",
                    normalizedTenantId, normalizedUserId, user.getId());
            syncUserDisplayName(user, displayName);
            syncWorkspaceName(mapping.getWorkspaceId(), normalizedTenantId, tenantName);
            reconcileWorkspaceRole(mapping, user.getId(), tenantAdmin);
            assistantProvisioningService.ensureAssistant(mapping.getWorkspaceId(), user.getId());
            return user;
        }

        // 2. 创建新用户
        UserEntity newUser = createMateClawUser(normalizedTenantId, normalizedUserId, displayName);

        // 3. 一个 Yliyun 租户映射到一个确定的 MateClaw Workspace。
        WorkspaceEntity workspace = getOrCreateTenantWorkspace(normalizedTenantId, tenantName);

        // 4. 创建映射记录
        McWorkspaceUserEntity mappingEntity = new McWorkspaceUserEntity();
        mappingEntity.setWorkspaceId(workspace.getId());
        mappingEntity.setUserId(newUser.getId());
        mappingEntity.setRole(initialWorkspaceRole(workspace, newUser.getId(), tenantAdmin));
        mappingEntity.setYliyunUserId(normalizedUserId);
        mappingEntity.setYliyunTenantId(normalizedTenantId);
        mappingEntity.setDeleted(0);

        try {
            workspaceUserMapper.insert(mappingEntity);
        } catch (DuplicateKeyException e) {
            // 并发幂等：回滚刚建的用户孤儿，重查已有映射
            log.warn("[Yliyun] Concurrent mapping creation detected for yliyunUserId={}, "
                    + "tenantId={}, rolling back orphan user {}", normalizedUserId,
                    normalizedTenantId, newUser.getId());
            userMapper.deleteById(newUser.getId());
            McWorkspaceUserEntity existing = workspaceUserMapper.selectOne(
                    new LambdaQueryWrapper<McWorkspaceUserEntity>()
                            .eq(McWorkspaceUserEntity::getYliyunTenantId, normalizedTenantId)
                            .eq(McWorkspaceUserEntity::getYliyunUserId, normalizedUserId)
                            .eq(McWorkspaceUserEntity::getDeleted, 0));
            if (existing != null) {
                return userMapper.selectById(existing.getUserId());
            }
            throw new MateClawException("err.auth.yliyun.concurrent_failed",
                    503, "并发创建用户映射失败，请重试");
        } catch (RuntimeException e) {
            if (newUser.getId() != null) {
                userMapper.deleteById(newUser.getId());
            }
            throw e;
        }

        // 5. 将签名 ticket 中的租户管理员身份映射到工作区角色。
        reconcileWorkspaceRole(mappingEntity, newUser.getId(), tenantAdmin);

        // 6. 租户首次登录即拥有可用的云盘智能助手，无需管理员手工套模板。
        assistantProvisioningService.ensureAssistant(workspace.getId(), newUser.getId());

        log.info("[Yliyun] Created new user mapping: tenantId={}, yliyunUserId={}, mateUserId={}, workspaceId={}",
                normalizedTenantId, normalizedUserId, newUser.getId(), workspace.getId());
        return newUser;
    }

    /**
     * Resolve the cloud identity behind an authenticated MateClaw user.
     * Multiple active rows are rejected as ambiguous rather than selecting an
     * arbitrary tenant and risking a cross-tenant tool call.
     */
    public Optional<McWorkspaceUserEntity> findMappingByMateUserId(Long mateUserId) {
        if (mateUserId == null) {
            return Optional.empty();
        }
        List<McWorkspaceUserEntity> mappings = workspaceUserMapper.selectList(
                new LambdaQueryWrapper<McWorkspaceUserEntity>()
                        .eq(McWorkspaceUserEntity::getUserId, mateUserId)
                        .eq(McWorkspaceUserEntity::getDeleted, 0));
        if (mappings.size() != 1) {
            if (mappings.size() > 1) {
                log.error("[Yliyun] Ambiguous MateClaw user mapping: mateUserId={}, rows={}",
                        mateUserId, mappings.size());
            }
            return Optional.empty();
        }
        McWorkspaceUserEntity mapping = mappings.getFirst();
        if (mapping.getYliyunUserId() == null || mapping.getYliyunTenantId() == null) {
            return Optional.empty();
        }
        return Optional.of(mapping);
    }

    public Optional<YliyunSessionIdentity> findSessionIdentity(Long mateUserId) {
        return findMappingByMateUserId(mateUserId).map(mapping -> {
            WorkspaceEntity workspace = workspaceMapper.selectById(mapping.getWorkspaceId());
            String tenantName = workspace != null ? workspace.getName() : null;
            return new YliyunSessionIdentity(
                    mapping.getYliyunUserId(),
                    mapping.getYliyunTenantId(),
                    tenantName);
        });
    }

    public record YliyunSessionIdentity(
            String cloudUserId,
            String cloudTenantId,
            String tenantName) {
    }

    /**
     * 创建 MateClaw 用户（无密码，仅 Yliyun 登录）。
     */
    private UserEntity createMateClawUser(String tenantId, String yliyunUserId, String displayName) {
        UserEntity user = new UserEntity();
        user.setUsername("yliyun_" + safeIdentifier(tenantId, 18)
                + "_" + safeIdentifier(yliyunUserId, 28));
        user.setPassword(null);
        user.setNickname(displayName);
        user.setRole(defaultRole);
        user.setEnabled(true);
        userMapper.insert(user);
        return user;
    }

    /**
     * 获取或创建租户专属工作区。
     */
    private WorkspaceEntity getOrCreateTenantWorkspace(String tenantId, String tenantName) {
        String slug = "yliyun-tenant-" + safeIdentifier(tenantId, 32);
        WorkspaceEntity ws = workspaceMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceEntity>()
                        .eq(WorkspaceEntity::getSlug, slug));
        if (ws != null) {
            syncWorkspaceName(ws.getId(), tenantId, tenantName);
            return ws;
        }
        // 首次遇到租户时创建隔离工作区。slug 唯一约束处理并发。
        ws = new WorkspaceEntity();
        ws.setName(firstNonBlank(tenantName, "一粒云租户 " + tenantId));
        ws.setSlug(slug);
        ws.setDescription("Yliyun tenant " + tenantId + " isolated workspace");
        ws.setDeleted(0);
        try {
            workspaceMapper.insert(ws);
            log.info("[Yliyun] Created tenant workspace: tenantId={}, workspaceId={}",
                    tenantId, ws.getId());
            return ws;
        } catch (DuplicateKeyException e) {
            return workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                    .eq(WorkspaceEntity::getSlug, slug));
        }
    }

    private void syncUserDisplayName(UserEntity user, String displayName) {
        if (displayName.equals(user.getNickname())) {
            return;
        }
        user.setNickname(displayName);
        userMapper.updateById(user);
    }

    private void syncWorkspaceName(Long workspaceId, String tenantId, String tenantName) {
        if (workspaceId == null || tenantName == null || tenantName.isBlank()) {
            return;
        }
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || tenantName.equals(workspace.getName())) {
            return;
        }
        workspace.setName(tenantName.trim());
        workspace.setDescription("Yliyun tenant " + tenantId + " isolated workspace");
        workspaceMapper.updateById(workspace);
    }

    private String initialWorkspaceRole(
            WorkspaceEntity workspace, Long userId, Boolean tenantAdmin) {
        if (!Boolean.TRUE.equals(tenantAdmin)) {
            return "member";
        }
        return workspace.getOwnerId() == null || workspace.getOwnerId().equals(userId)
                ? "owner" : "admin";
    }

    /**
     * Reconcile both membership tables from the signed cloud role on every SSO.
     * A missing claim is treated as a rolling-upgrade ticket: preserve the
     * existing mapped role instead of accidentally demoting an administrator.
     */
    private void reconcileWorkspaceRole(
            McWorkspaceUserEntity mapping, Long userId, Boolean tenantAdmin) {
        WorkspaceEntity workspace = workspaceMapper.selectById(mapping.getWorkspaceId());
        if (workspace == null) {
            throw new MateClawException("err.workspace.not_found", 404,
                    "关联工作区不存在: " + mapping.getWorkspaceId());
        }

        String desiredRole = mapping.getRole() == null ? "member" : mapping.getRole();
        if (tenantAdmin != null) {
            if (Boolean.TRUE.equals(tenantAdmin)) {
                if (workspace.getOwnerId() == null || workspace.getOwnerId().equals(userId)) {
                    workspaceService.assignFederatedOwner(workspace.getId(), userId, "admin");
                    desiredRole = "owner";
                } else {
                    desiredRole = "admin";
                }
            } else {
                if (workspace.getOwnerId() != null && workspace.getOwnerId().equals(userId)) {
                    McWorkspaceUserEntity successor = workspaceUserMapper.selectOne(
                            new LambdaQueryWrapper<McWorkspaceUserEntity>()
                                    .eq(McWorkspaceUserEntity::getWorkspaceId, workspace.getId())
                                    .ne(McWorkspaceUserEntity::getUserId, userId)
                                    .in(McWorkspaceUserEntity::getRole, "owner", "admin")
                                    .eq(McWorkspaceUserEntity::getDeleted, 0)
                                    .orderByAsc(McWorkspaceUserEntity::getCreateTime)
                                    .last("LIMIT 1"));
                    if (successor != null) {
                        successor.setRole("owner");
                        workspaceUserMapper.updateById(successor);
                        workspaceService.assignFederatedOwner(
                                workspace.getId(), successor.getUserId(), "member");
                    } else {
                        workspaceService.assignFederatedOwner(workspace.getId(), null, "member");
                    }
                }
                desiredRole = "member";
            }
        } else if ("owner".equals(desiredRole) && workspace.getOwnerId() == null) {
            workspaceService.assignFederatedOwner(workspace.getId(), userId, "admin");
        }

        if (!desiredRole.equals(mapping.getRole())) {
            mapping.setRole(desiredRole);
            workspaceUserMapper.updateById(mapping);
        }
        workspaceService.syncFederatedMemberRole(workspace.getId(), userId, desiredRole);
        log.info("[Yliyun] Reconciled workspace role: workspaceId={}, userId={}, role={}",
                workspace.getId(), userId, desiredRole);
    }

    private static String requireIdentityPart(String value, String field) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            throw new MateClawException("err.auth.yliyun.missing_" + field,
                    400, "ticket 中缺少 " + field);
        }
        return value.trim();
    }

    private static String safeIdentifier(String value, int maxLength) {
        String normalized = value.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        String hash = Integer.toUnsignedString(value.hashCode(), 36);
        int headLength = Math.max(1, maxLength - hash.length() - 1);
        return normalized.substring(0, headLength) + "_" + hash;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String cloudDisplayName(String nickname, String account, String fallback) {
        String normalizedNickname = firstNonBlank(nickname);
        String normalizedAccount = firstNonBlank(account);
        if (!normalizedNickname.isBlank() && !normalizedAccount.isBlank()
                && !normalizedNickname.equalsIgnoreCase(normalizedAccount)) {
            return normalizedNickname + " (" + normalizedAccount + ")";
        }
        return firstNonBlank(normalizedNickname, normalizedAccount, fallback);
    }
}
