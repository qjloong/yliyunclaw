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
import vip.mate.workspace.core.model.WorkspaceMemberEntity;
import vip.mate.workspace.core.repository.WorkspaceMapper;
import vip.mate.workspace.core.repository.WorkspaceMemberMapper;

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
    private final WorkspaceMemberMapper workspaceMemberMapper;

    @Value("${mateclaw.auth.yliyun.default-role:user}")
    private String defaultRole;

    /** 默认工作区 slug */
    private static final String DEFAULT_WORKSPACE_SLUG = "default";

    /**
     * 根据 yliyun 用户 ID 查找或创建 MateClaw 用户映射。
     *
     * @param yliyunUserId   Yliyun 用户 ID
     * @param yliyunTenantId Yliyun 租户 ID（可为 null）
     * @param nickname       Yliyun 用户昵称（可为 null）
     * @return 映射的 MateClaw 用户实体
     */
    @Transactional
    public UserEntity findOrCreateUser(String yliyunUserId, String yliyunTenantId, String nickname) {
        // 1. 查询已有映射
        McWorkspaceUserEntity mapping = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<McWorkspaceUserEntity>()
                        .eq(McWorkspaceUserEntity::getYliyunUserId, yliyunUserId));

        if (mapping != null) {
            UserEntity user = userMapper.selectById(mapping.getUserId());
            if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
                throw new MateClawException("err.auth.yliyun.user_disabled", 403, "关联用户已停用或不存在");
            }
            log.debug("[Yliyun] Found existing mapping: yliyunUserId={} → mateUserId={}", yliyunUserId, user.getId());
            return user;
        }

        // 2. 创建新用户
        UserEntity newUser = createMateClawUser(yliyunUserId, nickname);

        // 3. 获取默认工作区
        WorkspaceEntity workspace = getOrCreateDefaultWorkspace();

        // 4. 创建映射记录
        McWorkspaceUserEntity mappingEntity = new McWorkspaceUserEntity();
        mappingEntity.setWorkspaceId(workspace.getId());
        mappingEntity.setUserId(newUser.getId());
        mappingEntity.setRole("member");
        mappingEntity.setYliyunUserId(yliyunUserId);
        mappingEntity.setYliyunTenantId(yliyunTenantId);
        mappingEntity.setDeleted(0);

        try {
            workspaceUserMapper.insert(mappingEntity);
        } catch (DuplicateKeyException e) {
            // 并发幂等：回滚刚建的用户孤儿，重查已有映射
            log.warn("[Yliyun] Concurrent mapping creation detected for yliyunUserId={}, "
                    + "rolling back orphan user {}", yliyunUserId, newUser.getId());
            userMapper.deleteById(newUser.getId());
            McWorkspaceUserEntity existing = workspaceUserMapper.selectOne(
                    new LambdaQueryWrapper<McWorkspaceUserEntity>()
                            .eq(McWorkspaceUserEntity::getYliyunUserId, yliyunUserId));
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

        // 5. 添加到默认工作区成员
        addToDefaultWorkspaceIfNotMember(workspace.getId(), newUser.getId());

        log.info("[Yliyun] Created new user mapping: yliyunUserId={}, mateUserId={}, workspaceId={}",
                yliyunUserId, newUser.getId(), workspace.getId());
        return newUser;
    }

    /**
     * 创建 MateClaw 用户（无密码，仅 Yliyun 登录）。
     */
    private UserEntity createMateClawUser(String yliyunUserId, String nickname) {
        UserEntity user = new UserEntity();
        user.setUsername("yliyun_" + yliyunUserId);
        user.setPassword(null);
        user.setNickname(nickname != null ? nickname : yliyunUserId);
        user.setRole(defaultRole);
        user.setEnabled(true);
        userMapper.insert(user);
        return user;
    }

    /**
     * 获取或创建默认工作区。
     */
    private WorkspaceEntity getOrCreateDefaultWorkspace() {
        WorkspaceEntity ws = workspaceMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceEntity>()
                        .eq(WorkspaceEntity::getSlug, DEFAULT_WORKSPACE_SLUG));
        if (ws != null) {
            return ws;
        }
        // 首次部署时创建默认工作区
        ws = new WorkspaceEntity();
        ws.setName("默认工作区");
        ws.setSlug(DEFAULT_WORKSPACE_SLUG);
        ws.setDescription("Yliyun 默认工作区");
        ws.setDeleted(0);
        workspaceMapper.insert(ws);
        log.info("[Yliyun] Created default workspace: id={}", ws.getId());
        return ws;
    }

    /**
     * 将用户添加到默认工作区成员（如果还不是成员）。
     */
    private void addToDefaultWorkspaceIfNotMember(Long workspaceId, Long userId) {
        WorkspaceMemberEntity existing = workspaceMemberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMemberEntity::getUserId, userId));
        if (existing != null) {
            return;
        }
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole("member");
        member.setDeleted(0);
        workspaceMemberMapper.insert(member);
        log.debug("[Yliyun] Added user {} as member of workspace {}", userId, workspaceId);
    }
}
