package vip.mate.workspace.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.exception.MateClawException;
import vip.mate.workspace.core.model.ProjectChangeSummaryItem;
import vip.mate.workspace.core.model.ProjectInsightSummary;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.model.WorkspaceInviteEntity;
import vip.mate.workspace.core.model.WorkspaceMemberEntity;
import vip.mate.workspace.core.model.WorkspacePolicy;
import vip.mate.workspace.core.repository.WorkspaceInviteMapper;
import vip.mate.workspace.core.repository.WorkspaceMapper;
import vip.mate.workspace.core.repository.WorkspaceMemberMapper;

import java.time.Duration;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 工作区业务服务
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    public static final String PROJECT_PERMISSION_MODE_LIMITED = "limited";
    public static final String PROJECT_PERMISSION_MODE_FULL = "full";
    private static final String SETTINGS_KEY_PROJECT_PERMISSION_MODE = "projectPermissionMode";
    private static final String SETTINGS_KEY_WORKSPACE_POLICY = "workspacePolicy";
    private static final int MATERIAL_INDEX_MAX_FILES = 120;
    private static final int MATERIAL_INDEX_MAX_DEPTH = 4;

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final WorkspaceInviteMapper inviteMapper;
    private final ObjectMapper objectMapper;
    private final AuthService authService;

    /** 默认工作区 slug */
    public static final String DEFAULT_SLUG = "default";

    /** 成员资格缓存：key = "workspaceId:userId"，value = role string（null 表示非成员） */
    private final Cache<String, String> membershipCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1000)
            .build();

    private final Cache<String, CachedProjectInsight> projectInsightCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(256)
            .build();

    // ==================== 工作区 CRUD ====================

    public List<WorkspaceEntity> listAll() {
        return hydrateWorkspaceSettings(workspaceMapper.selectList(
                new LambdaQueryWrapper<WorkspaceEntity>().orderByAsc(WorkspaceEntity::getCreateTime)));
    }

    /**
     * 查询用户可见的工作区列表（用户是其成员的所有工作区）
     */
    public List<WorkspaceEntity> listByUserId(Long userId) {
        List<WorkspaceMemberEntity> memberships = memberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getUserId, userId));
        if (memberships.isEmpty()) {
            // 至少返回默认工作区
            WorkspaceEntity defaultWs = getBySlug(DEFAULT_SLUG);
            return defaultWs != null ? List.of(defaultWs) : List.of();
        }
        List<Long> wsIds = memberships.stream().map(WorkspaceMemberEntity::getWorkspaceId).toList();
        return hydrateWorkspaceSettings(workspaceMapper.selectBatchIds(wsIds));
    }

    public WorkspaceEntity getById(Long id) {
        WorkspaceEntity entity = workspaceMapper.selectById(id);
        if (entity == null) {
            throw new MateClawException("err.workspace.not_found", "工作区不存在: " + id);
        }
        return hydrateWorkspaceSettings(entity);
    }

    public WorkspaceEntity getBySlug(String slug) {
        return hydrateWorkspaceSettings(workspaceMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceEntity>()
                        .eq(WorkspaceEntity::getSlug, slug)));
    }

    @Transactional
    public WorkspaceEntity create(WorkspaceEntity entity, Long creatorUserId) {
        // 验证 slug 唯一
        if (getBySlug(entity.getSlug()) != null) {
            throw new MateClawException("err.workspace.slug_exists", "工作区标识已存在: " + entity.getSlug());
        }
        entity.setWorkspacePolicy(normalizeWorkspacePolicy(entity.getWorkspacePolicy()));
        entity.setSettingsJson(mergeSettingsJson(null, entity.getSettingsJson(), entity.getProjectPermissionMode(), entity.getWorkspacePolicy()));
        entity.setProjectPermissionMode(normalizeProjectPermissionMode(entity.getProjectPermissionMode()));
        entity.setOwnerId(creatorUserId);
        workspaceMapper.insert(entity);

        // 创建者自动成为 owner
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(entity.getId());
        member.setUserId(creatorUserId);
        member.setRole("owner");
        memberMapper.insert(member);

        log.info("Created workspace: {} (slug={}, owner={})", entity.getName(), entity.getSlug(), creatorUserId);
        evictProjectInsightCache(entity.getId());
        return hydrateWorkspaceSettings(entity);
    }

    @Transactional
    public WorkspaceEntity pickDirectoryAndCreate(Long creatorUserId) {
        Path selectedDirectory = chooseLocalDirectory();
        if (selectedDirectory == null) {
            return null;
        }

        String normalizedBasePath = selectedDirectory.toAbsolutePath().normalize().toString();
        WorkspaceEntity existingWorkspace = listByUserId(creatorUserId).stream()
                .filter(workspace -> sameBasePath(workspace.getBasePath(), normalizedBasePath))
                .findFirst()
                .orElse(null);
        if (existingWorkspace != null) {
            return existingWorkspace;
        }

        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setName(buildWorkspaceName(selectedDirectory));
        entity.setSlug(generateUniqueSlug(entity.getName()));
        entity.setBasePath(normalizedBasePath);
        entity.setProjectPermissionMode(PROJECT_PERMISSION_MODE_LIMITED);
        return create(entity, creatorUserId);
    }

    public WorkspaceEntity update(WorkspaceEntity entity) {
        WorkspaceEntity existing = getById(entity.getId());
        // slug 为 null 时保留原值，不做修改
        if (entity.getSlug() == null) {
            entity.setSlug(existing.getSlug());
        }
        // 不允许修改默认工作区的 slug
        if (DEFAULT_SLUG.equals(existing.getSlug()) && !DEFAULT_SLUG.equals(entity.getSlug())) {
            throw new MateClawException("err.workspace.cannot_modify_default", "不能修改默认工作区的标识");
        }
        // 验证 slug 唯一性（如果修改了 slug）
        if (!entity.getSlug().equals(existing.getSlug())) {
            if (getBySlug(entity.getSlug()) != null) {
                throw new MateClawException("err.workspace.slug_exists", "工作区标识已存在: " + entity.getSlug());
            }
        }
        String effectiveProjectPermissionMode = entity.getProjectPermissionMode() != null
            ? entity.getProjectPermissionMode()
            : existing.getProjectPermissionMode();
        entity.setWorkspacePolicy(normalizeWorkspacePolicy(entity.getWorkspacePolicy() != null
            ? entity.getWorkspacePolicy()
            : existing.getWorkspacePolicy()));
        entity.setSettingsJson(mergeSettingsJson(existing.getSettingsJson(), entity.getSettingsJson(), effectiveProjectPermissionMode, entity.getWorkspacePolicy()));
        entity.setProjectPermissionMode(normalizeProjectPermissionMode(effectiveProjectPermissionMode));
        workspaceMapper.updateById(entity);
        evictProjectInsightCache(entity.getId());
        return hydrateWorkspaceSettings(entity);
    }

    public String resolveProjectPermissionMode(Long workspaceId) {
        if (workspaceId == null) {
            return PROJECT_PERMISSION_MODE_LIMITED;
        }
        try {
            WorkspaceEntity workspace = getById(workspaceId);
            return normalizeProjectPermissionMode(workspace.getProjectPermissionMode());
        } catch (Exception e) {
            log.debug("Failed to resolve project permission mode for workspace {}: {}", workspaceId, e.getMessage());
            return PROJECT_PERMISSION_MODE_LIMITED;
        }
    }

    public boolean isProjectFullAccess(Long workspaceId) {
        return PROJECT_PERMISSION_MODE_FULL.equals(resolveProjectPermissionMode(workspaceId));
    }

    public WorkspacePolicy resolveWorkspacePolicy(Long workspaceId) {
        if (workspaceId == null) {
            return normalizeWorkspacePolicy(null);
        }
        try {
            WorkspaceEntity workspace = getById(workspaceId);
            return normalizeWorkspacePolicy(workspace.getWorkspacePolicy());
        } catch (Exception e) {
            log.debug("Failed to resolve workspace policy for workspace {}: {}", workspaceId, e.getMessage());
            return normalizeWorkspacePolicy(null);
        }
    }

    public Map<String, Object> listDirectories(Long workspaceId, String requestedPath) {
        WorkspaceEntity workspace = getById(workspaceId);
        String basePath = workspace.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new MateClawException("err.workspace.base_path_missing", "当前工作区未配置活动目录");
        }

        Path root = Paths.get(basePath).toAbsolutePath().normalize();
        Path current = resolveBrowsableDirectory(root, requestedPath);
        String relativePath = relativizePath(root, current);

        List<Map<String, Object>> entries;
        try (Stream<Path> stream = Files.list(current)) {
            entries = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(path -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", path.getFileName().toString());
                        item.put("path", path.toString());
                        item.put("relativePath", relativizePath(root, path));
                        return item;
                    })
                    .toList();
        } catch (IOException e) {
            throw new MateClawException("err.workspace.directory_list_failed", "读取目录失败: " + e.getMessage());
        }

        Path parent = current.equals(root) ? null : current.getParent();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rootPath", root.toString());
        payload.put("currentPath", current.toString());
        payload.put("relativePath", relativePath);
        payload.put("canGoUp", parent != null && parent.startsWith(root));
        payload.put("parentPath", parent != null && parent.startsWith(root) ? parent.toString() : null);
        payload.put("entries", entries);
        return payload;
    }

    public ProjectInsightSummary getProjectInsight(Long workspaceId, String requestedPath) {
        WorkspaceEntity workspace = getById(workspaceId);
        String basePath = workspace.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new MateClawException("err.workspace.base_path_missing", "当前工作区未配置活动目录");
        }

        Path root = Paths.get(basePath).toAbsolutePath().normalize();
        Path requestedDir = resolveBrowsableDirectory(root, requestedPath);
        ProjectRootResolution resolution = locateProjectRoot(root, requestedDir);
        Path projectDir = resolution.projectRoot();
        Path gitRoot = findGitRoot(projectDir, root);
        String cacheKey = workspaceId + ":" + projectDir.toString().toLowerCase(Locale.ROOT);
        String fingerprint = buildProjectStructureFingerprint(projectDir, resolution.markers(), gitRoot);
        CachedProjectInsight cached = projectInsightCache.getIfPresent(cacheKey);
        if (cached == null || !Objects.equals(cached.fingerprint(), fingerprint)) {
            cached = buildCachedProjectInsight(root, projectDir, resolution, gitRoot, fingerprint);
            projectInsightCache.put(cacheKey, cached);
        }
        return materializeProjectInsight(cached, requestedDir, resolution, gitRoot);
    }

    public Path resolveProjectDirectory(Long workspaceId, String requestedPath) {
        WorkspaceEntity workspace = getById(workspaceId);
        String basePath = workspace.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new MateClawException("err.workspace.base_path_missing", "当前工作区未配置活动目录");
        }
        Path root = Paths.get(basePath).toAbsolutePath().normalize();
        return resolveBrowsableDirectory(root, requestedPath);
    }

    public void delete(Long id) {
        WorkspaceEntity existing = getById(id);
        if (DEFAULT_SLUG.equals(existing.getSlug())) {
            throw new MateClawException("err.workspace.cannot_delete_default", "不能删除默认工作区");
        }
        workspaceMapper.deleteById(id);
        evictProjectInsightCache(id);
        log.info("Deleted workspace: {} (id={})", existing.getName(), id);
    }

    // ==================== 成员管理 ====================

    public List<WorkspaceMemberEntity> listMembers(Long workspaceId) {
        return memberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                        .orderByAsc(WorkspaceMemberEntity::getCreateTime));
    }

    public WorkspaceMemberEntity getMembership(Long workspaceId, Long userId) {
        return memberMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                        .eq(WorkspaceMemberEntity::getUserId, userId));
    }

    @Transactional
    public WorkspaceMemberEntity addMember(Long workspaceId, Long userId, String role) {
        // 验证工作区存在
        getById(workspaceId);
        // 检查是否已是成员
        WorkspaceMemberEntity existing = getMembership(workspaceId, userId);
        if (existing != null) {
            throw new MateClawException("err.workspace.member_exists", "用户已经是该工作区的成员");
        }
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role != null ? role : "member");
        memberMapper.insert(member);
        evictMembershipCache(workspaceId, userId);
        log.info("Added member to workspace: userId={}, workspaceId={}, role={}", userId, workspaceId, member.getRole());
        return member;
    }

    public WorkspaceMemberEntity updateMemberRole(Long workspaceId, Long userId, String role) {
        WorkspaceMemberEntity member = getMembership(workspaceId, userId);
        if (member == null) {
            throw new MateClawException("err.workspace.not_member", "用户不是该工作区的成员");
        }
        if ("owner".equals(member.getRole())) {
            throw new MateClawException("err.workspace.cannot_modify_owner", "不能修改工作区拥有者的角色");
        }
        member.setRole(role);
        memberMapper.updateById(member);
        evictMembershipCache(workspaceId, userId);
        return member;
    }

    public void removeMember(Long workspaceId, Long userId) {
        WorkspaceMemberEntity member = getMembership(workspaceId, userId);
        if (member == null) {
            throw new MateClawException("err.workspace.not_member", "用户不是该工作区的成员");
        }
        if ("owner".equals(member.getRole())) {
            throw new MateClawException("err.workspace.cannot_remove_owner", "不能移除工作区拥有者");
        }
        memberMapper.deleteById(member.getId());
        evictMembershipCache(workspaceId, userId);
        log.info("Removed member from workspace: userId={}, workspaceId={}", userId, workspaceId);
    }

    public Map<String, Object> createInviteLink(Long workspaceId, Long inviterUserId, String role) {
        WorkspaceEntity workspace = getById(workspaceId);
        String normalizedRole = normalizeInviteRole(role);
        long expirationMs = authService.getWorkspaceInviteExpiration();
        Long inviteId = IdWorker.getId();
        WorkspaceInviteEntity invite = new WorkspaceInviteEntity();
        invite.setId(inviteId);
        invite.setWorkspaceId(workspaceId);
        invite.setRole(normalizedRole);
        invite.setInvitedByUserId(inviterUserId);
        invite.setMaxUses(1);
        invite.setUseCount(0);
        invite.setStatus("active");
        invite.setExpiresAt(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(System.currentTimeMillis() + expirationMs),
                ZoneId.systemDefault()));
        invite.setDeleted(0);
        inviteMapper.insert(invite);

        String token = authService.generateWorkspaceInviteToken(
                inviteId,
                workspaceId,
                normalizedRole,
                inviterUserId,
                expirationMs);
        UserEntity inviter = authService.findById(inviterUserId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", inviteId);
        payload.put("token", token);
        payload.put("workspaceId", workspace.getId());
        payload.put("workspaceName", workspace.getName());
        payload.put("role", normalizedRole);
        payload.put("maxUses", invite.getMaxUses());
        payload.put("useCount", invite.getUseCount());
        payload.put("status", invite.getStatus());
        payload.put("inviterUsername", inviter != null ? inviter.getUsername() : null);
        payload.put("expiresAt", invite.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        return payload;
    }

    public List<WorkspaceInviteEntity> listInvites(Long workspaceId) {
        WorkspaceEntity workspace = getById(workspaceId);
        List<WorkspaceInviteEntity> invites = inviteMapper.selectList(new LambdaQueryWrapper<WorkspaceInviteEntity>()
                .eq(WorkspaceInviteEntity::getWorkspaceId, workspaceId)
                .eq(WorkspaceInviteEntity::getDeleted, 0)
                .orderByDesc(WorkspaceInviteEntity::getCreateTime));
        for (WorkspaceInviteEntity invite : invites) {
            UserEntity inviter = authService.findById(invite.getInvitedByUserId());
            invite.setInviterUsername(inviter != null ? inviter.getUsername() : null);
            invite.setWorkspaceName(workspace != null ? workspace.getName() : null);
        }
        return invites;
    }

    public void revokeInvite(Long workspaceId, Long inviteId) {
        WorkspaceInviteEntity invite = inviteMapper.selectById(inviteId);
        if (invite == null || invite.getDeleted() != null && invite.getDeleted() != 0 || !workspaceId.equals(invite.getWorkspaceId())) {
            throw new MateClawException("err.workspace.invite_invalid", 404, "Workspace invite not found");
        }
        invite.setStatus("revoked");
        inviteMapper.updateById(invite);
    }

    public Map<String, Object> previewInvite(String token, Long userId) {
        Claims claims = parseWorkspaceInviteClaims(token);
        Long workspaceId = readInviteWorkspaceId(claims);
        WorkspaceEntity workspace = getById(workspaceId);
        WorkspaceMemberEntity existing = userId != null ? getMembership(workspaceId, userId) : null;
        if (existing != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("workspaceId", workspace.getId());
            payload.put("workspaceName", workspace.getName());
            payload.put("role", existing.getRole());
            payload.put("alreadyMember", true);
            payload.put("currentRole", existing.getRole());
            payload.put("status", "already_member");
            return payload;
        }

        WorkspaceInviteEntity invite = requireUsableInvite(claims);
        String role = normalizeInviteRole(invite.getRole());
        Long inviterUserId = invite.getInvitedByUserId();
        UserEntity inviter = inviterUserId != null ? authService.findById(inviterUserId) : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", invite.getId());
        payload.put("workspaceId", workspace.getId());
        payload.put("workspaceName", workspace.getName());
        payload.put("role", role);
        payload.put("alreadyMember", false);
        payload.put("currentRole", null);
        payload.put("maxUses", invite.getMaxUses());
        payload.put("useCount", invite.getUseCount());
        payload.put("status", invite.getStatus());
        payload.put("inviterUsername", inviter != null ? inviter.getUsername() : null);
        payload.put("expiresAt", invite.getExpiresAt() != null ? invite.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null);
        return payload;
    }

    @Transactional
    public Map<String, Object> acceptInvite(String token, Long userId) {
        Claims claims = parseWorkspaceInviteClaims(token);
        Long workspaceId = readInviteWorkspaceId(claims);
        WorkspaceEntity workspace = getById(workspaceId);
        WorkspaceMemberEntity existing = getMembership(workspaceId, userId);
        WorkspaceMemberEntity effectiveMember = existing;
        String status;

        if (existing != null) {
            status = "owner".equals(existing.getRole()) ? "already_owner" : "already_member";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("workspaceId", workspace.getId());
            payload.put("workspaceName", workspace.getName());
            payload.put("role", existing.getRole());
            payload.put("status", status);
            payload.put("alreadyMember", true);
            return payload;
        }

        WorkspaceInviteEntity invite = requireUsableInvite(claims);
        String role = normalizeInviteRole(invite.getRole());
        effectiveMember = addMember(workspaceId, userId, role);
        status = "joined";

        int nextUseCount = (invite.getUseCount() != null ? invite.getUseCount() : 0) + 1;
        invite.setUseCount(nextUseCount);
        invite.setLastAcceptedByUserId(userId);
        invite.setLastAcceptedTime(LocalDateTime.now());
        if (nextUseCount >= (invite.getMaxUses() != null ? invite.getMaxUses() : 1)) {
            invite.setStatus("used");
        }
        inviteMapper.updateById(invite);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inviteId", invite.getId());
        payload.put("workspaceId", workspace.getId());
        payload.put("workspaceName", workspace.getName());
        payload.put("role", effectiveMember != null ? effectiveMember.getRole() : role);
        payload.put("status", status);
        return payload;
    }

    // ==================== 权限检查 ====================

    /**
     * 检查用户是否有指定工作区的最低角色权限
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @param minRole     最低角色要求：owner > admin > member > viewer
     * @return true 如果用户有足够权限
     */
    public boolean hasPermission(Long workspaceId, Long userId, String minRole) {
        WorkspaceMemberEntity member = getMembership(workspaceId, userId);
        if (member == null) {
            return false;
        }
        return roleLevel(member.getRole()) >= roleLevel(minRole);
    }

    /**
     * 断言用户有指定权限，否则抛异常
     */
    public void requirePermission(Long workspaceId, Long userId, String minRole) {
        if (!hasPermission(workspaceId, userId, minRole)) {
            throw new MateClawException("err.workspace.insufficient_permission", 403, "权限不足：需要 " + minRole + " 或更高角色");
        }
    }

    /**
     * 带缓存的权限检查（拦截器高频调用，避免每次请求查库）
     */
    public boolean hasPermissionCached(Long workspaceId, Long userId, String minRole) {
        String cacheKey = workspaceId + ":" + userId;
        String role = membershipCache.get(cacheKey, k -> {
            WorkspaceMemberEntity member = getMembership(workspaceId, userId);
            return member != null ? member.getRole() : "";
        });
        if (role == null || role.isEmpty()) {
            return false;
        }
        return roleLevel(role) >= roleLevel(minRole);
    }

    public String getMembershipRoleCached(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return null;
        }
        String cacheKey = workspaceId + ":" + userId;
        String role = membershipCache.get(cacheKey, k -> {
            WorkspaceMemberEntity member = getMembership(workspaceId, userId);
            return member != null ? member.getRole() : "";
        });
        return role == null || role.isBlank() ? null : role;
    }

    /**
     * 清除指定 workspace + user 的成员资格缓存（成员变更时调用）
     */
    public void evictMembershipCache(Long workspaceId, Long userId) {
        membershipCache.invalidate(workspaceId + ":" + userId);
    }

    public void evictProjectInsightCache(Long workspaceId) {
        if (workspaceId == null) {
            return;
        }
        String prefix = workspaceId + ":";
        projectInsightCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private int roleLevel(String role) {
        return switch (role) {
            case "owner" -> 4;
            case "admin" -> 3;
            case "member" -> 2;
            case "viewer" -> 1;
            default -> 0;
        };
    }

    private Claims parseWorkspaceInviteClaims(String token) {
        Claims claims = authService.parseWorkspaceInviteTokenClaims(token);
        if (claims == null) {
            throw new MateClawException("err.workspace.invite_invalid", 400, "工作区邀请无效或已过期");
        }
        return claims;
    }

    private Long readInviteWorkspaceId(Claims claims) {
        Object raw = claims.get("workspaceId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String value && !value.isBlank()) {
            return Long.valueOf(value);
        }
        throw new MateClawException("err.workspace.invite_invalid", 400, "工作区邀请缺少工作区信息");
    }

    private Long readInviteUserId(Claims claims) {
        Object raw = claims.get("invitedBy");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String value && !value.isBlank()) {
            return Long.valueOf(value);
        }
        return null;
    }

    private Long readInviteId(Claims claims) {
        Object raw = claims.get("inviteId");
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String value && !value.isBlank()) {
            return Long.valueOf(value);
        }
        throw new MateClawException("err.workspace.invite_invalid", 400, "Workspace invite record is missing");
    }

    private WorkspaceInviteEntity requireUsableInvite(Claims claims) {
        Long inviteId = readInviteId(claims);
        Long workspaceId = readInviteWorkspaceId(claims);
        WorkspaceInviteEntity invite = inviteMapper.selectById(inviteId);
        if (invite == null
                || (invite.getDeleted() != null && invite.getDeleted() != 0)
                || !workspaceId.equals(invite.getWorkspaceId())) {
            throw new MateClawException("err.workspace.invite_invalid", 400, "Workspace invite is invalid");
        }
        if (!"active".equals(invite.getStatus())) {
            throw new MateClawException("err.workspace.invite_invalid", 400, "Workspace invite is no longer active");
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus("expired");
            inviteMapper.updateById(invite);
            throw new MateClawException("err.workspace.invite_invalid", 400, "Workspace invite has expired");
        }
        int maxUses = invite.getMaxUses() != null ? invite.getMaxUses() : 1;
        int useCount = invite.getUseCount() != null ? invite.getUseCount() : 0;
        if (useCount >= maxUses) {
            invite.setStatus("used");
            inviteMapper.updateById(invite);
            throw new MateClawException("err.workspace.invite_invalid", 400, "Workspace invite has already been used");
        }
        return invite;
    }

    private String normalizeInviteRole(String role) {
        String normalized = role == null ? "member" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "admin", "member", "viewer" -> normalized;
            case "", "owner" -> throw new MateClawException("err.workspace.invite_role_invalid", 400, "工作区邀请角色无效");
            default -> throw new MateClawException("err.workspace.invite_role_invalid", 400, "工作区邀请角色无效: " + role);
        };
    }

    private Path chooseLocalDirectory() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new MateClawException("err.workspace.directory_picker_unavailable", "当前运行环境不支持本地目录选择器");
        }

        AtomicReference<File> selectedRef = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // ignore look and feel issues
                }
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select Workspace Folder");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setMultiSelectionEnabled(false);
                chooser.setAcceptAllFileFilterUsed(false);
                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    selectedRef.set(chooser.getSelectedFile());
                }
            });
        } catch (Exception e) {
            throw new MateClawException("err.workspace.directory_picker_failed", "打开本地目录选择器失败: " + e.getMessage());
        }

        File selectedFile = selectedRef.get();
        if (selectedFile == null) {
            return null;
        }
        return selectedFile.toPath().toAbsolutePath().normalize();
    }

    private boolean sameBasePath(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return false;
        }
        try {
            return Paths.get(left).toAbsolutePath().normalize().toString()
                    .equalsIgnoreCase(Paths.get(right).toAbsolutePath().normalize().toString());
        } catch (Exception e) {
            return left.trim().equalsIgnoreCase(right.trim());
        }
    }

    private String buildWorkspaceName(Path selectedDirectory) {
        Path fileName = selectedDirectory.getFileName();
        if (fileName != null) {
            return fileName.toString();
        }
        String normalized = selectedDirectory.toAbsolutePath().normalize().toString();
        return normalized.replace(':', '_').replace('\\', '_').replace('/', '_');
    }

    private String generateUniqueSlug(String baseName) {
        String baseSlug = slugify(baseName);
        String candidate = baseSlug;
        int suffix = 2;
        while (getBySlug(candidate) != null) {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "workspace";
        }
        String slug = value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "workspace" : slug;
    }

    private Path resolveBrowsableDirectory(Path root, String requestedPath) {
        Path candidate;
        String trimmed = requestedPath != null ? requestedPath.trim() : "";
        if (trimmed.isEmpty()) {
            candidate = root;
        } else {
            Path raw = Paths.get(trimmed);
            candidate = raw.isAbsolute() ? raw.toAbsolutePath().normalize() : root.resolve(raw).normalize().toAbsolutePath();
        }

        if (!candidate.startsWith(root)) {
            throw new MateClawException("err.workspace.directory_out_of_bounds", "目录必须位于当前工作区活动目录内");
        }
        if (!Files.exists(candidate)) {
            throw new MateClawException("err.workspace.directory_missing", "目录不存在: " + candidate);
        }
        if (!Files.isDirectory(candidate)) {
            throw new MateClawException("err.workspace.directory_not_dir", "目标不是目录: " + candidate);
        }

        try {
            Path realRoot = Files.exists(root) ? root.toRealPath() : root;
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot)) {
                throw new MateClawException("err.workspace.directory_symlink_escape", "目录通过符号链接越过了工作区边界");
            }
            return realCandidate;
        } catch (IOException e) {
            log.debug("Failed to resolve workspace browse path: {}", candidate, e);
            return candidate;
        }
    }

    private CachedProjectInsight buildCachedProjectInsight(Path workspaceRoot,
                                                           Path projectDir,
                                                           ProjectRootResolution resolution,
                                                           Path gitRoot,
                                                           String fingerprint) {
        String scannedAt = Instant.now().toString();
        ProjectInsightSummary summary = buildProjectInsightSnapshot(workspaceRoot, projectDir, resolution, gitRoot, scannedAt);
        return new CachedProjectInsight(summary, fingerprint, scannedAt);
    }

    private ProjectInsightSummary materializeProjectInsight(CachedProjectInsight cached,
                                                            Path requestedDir,
                                                            ProjectRootResolution resolution,
                                                            Path gitRoot) {
        ProjectInsightSummary summary = copyProjectInsightSummary(cached.summary());
        summary.setWorkingDirectoryPath(requestedDir.toString());
        summary.setWorkingDirectoryRelativePath(relativizePath(resolution.projectRoot(), requestedDir));
        summary.setLocatorType(resolution.locatorType());
        summary.setLocatorMarkers(resolution.markers());
        summary.setLastScannedAt(cached.scannedAt());
        if (gitRoot != null) {
            List<ProjectChangeSummaryItem> changedFiles = loadGitProjectSnapshot(gitRoot, resolution.projectRoot());
            summary.setChangedFiles(changedFiles);
            summary.setChangedFileCount(changedFiles.size());
        } else {
            summary.setChangedFiles(List.of());
            summary.setChangedFileCount(0);
        }
        return summary;
    }

    private ProjectInsightSummary buildProjectInsightSnapshot(Path workspaceRoot,
                                                              Path projectDir,
                                                              ProjectRootResolution resolution,
                                                              Path gitRoot,
                                                              String scannedAt) {
        ProjectInsightSummary summary = new ProjectInsightSummary();
        summary.setProjectName(resolveProjectName(projectDir));
        summary.setRootPath(projectDir.toString());
        summary.setRelativePath(relativizePath(workspaceRoot, projectDir));
        summary.setLocatorType(resolution.locatorType());
        summary.setLocatorMarkers(resolution.markers());
        summary.setLastScannedAt(scannedAt);

        List<Path> entries;
        try (Stream<Path> stream = Files.list(projectDir)) {
            entries = stream.sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).toList();
        } catch (IOException e) {
            throw new MateClawException("err.workspace.project_insight_failed", "读取项目目录失败: " + e.getMessage());
        }

        Set<String> lowerNames = entries.stream()
                .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        summary.setKeyFiles(resolveKeyFiles(entries));
        summary.setModuleHints(resolveModuleHints(entries));
        summary.setMaterialIndexHints(resolveMaterialIndexHints(projectDir));
        summary.setStackHints(resolveStackHints(projectDir, lowerNames));
        summary.setPackageManager(resolvePackageManager(lowerNames));
        summary.setBuildSystem(resolveBuildSystem(lowerNames));
        summary.setCommandHints(resolveCommandHints(projectDir, lowerNames, summary.getPackageManager(), summary.getBuildSystem()));
        if (gitRoot != null) {
            summary.setGitRootPath(gitRoot.toString());
            summary.setGitRootRelativePath(relativizePath(projectDir, gitRoot));
        } else {
            summary.setChangedFiles(List.of());
            summary.setChangedFileCount(0);
        }
        return summary;
    }

    private ProjectInsightSummary copyProjectInsightSummary(ProjectInsightSummary source) {
        ProjectInsightSummary copy = new ProjectInsightSummary();
        copy.setProjectName(source.getProjectName());
        copy.setRootPath(source.getRootPath());
        copy.setRelativePath(source.getRelativePath());
        copy.setWorkingDirectoryPath(source.getWorkingDirectoryPath());
        copy.setWorkingDirectoryRelativePath(source.getWorkingDirectoryRelativePath());
        copy.setLocatorType(source.getLocatorType());
        copy.setLocatorMarkers(source.getLocatorMarkers());
        copy.setStackHints(source.getStackHints());
        copy.setKeyFiles(source.getKeyFiles());
        copy.setModuleHints(source.getModuleHints());
        copy.setMaterialIndexHints(source.getMaterialIndexHints());
        copy.setCommandHints(source.getCommandHints());
        copy.setPackageManager(source.getPackageManager());
        copy.setBuildSystem(source.getBuildSystem());
        copy.setGitRootPath(source.getGitRootPath());
        copy.setGitRootRelativePath(source.getGitRootRelativePath());
        copy.setChangedFileCount(source.getChangedFileCount());
        copy.setChangedFiles(source.getChangedFiles());
        copy.setLastScannedAt(source.getLastScannedAt());
        return copy;
    }

    private String buildProjectStructureFingerprint(Path projectDir, List<String> locatorMarkers, Path gitRoot) {
        LinkedHashSet<Path> observedPaths = new LinkedHashSet<>();
        observedPaths.add(projectDir);
        if (locatorMarkers != null) {
            locatorMarkers.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(marker -> !marker.isEmpty())
                    .map(projectDir::resolve)
                    .forEach(observedPaths::add);
        }
        List.of(
                "AGENTS.md",
                "README.md",
                "README_zh.md",
                "PROJECT_CACHE.md",
                "package.json",
                "pnpm-lock.yaml",
                "pom.xml",
                "build.gradle",
                "settings.gradle",
                "Dockerfile",
                "docker-compose.yml",
                "tsconfig.json",
                "vite.config.ts",
                "vite.config.js",
                "pyproject.toml",
                "requirements.txt",
                "Cargo.toml",
                "go.mod",
                "src",
                "docs"
        ).stream().map(projectDir::resolve).forEach(observedPaths::add);
        if (gitRoot != null) {
            Path gitMarker = gitRoot.resolve(".git");
            observedPaths.add(gitMarker);
            if (Files.isDirectory(gitMarker)) {
                observedPaths.add(gitMarker.resolve("HEAD"));
                observedPaths.add(gitMarker.resolve("index"));
            }
        }
        return observedPaths.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .map(this::buildPathFingerprint)
                .collect(Collectors.joining("|"));
    }

    private String buildPathFingerprint(Path path) {
        try {
            if (!Files.exists(path)) {
                return path + "#missing";
            }
            boolean directory = Files.isDirectory(path);
            long modifiedAt = Files.getLastModifiedTime(path).toMillis();
            long size = directory ? -1L : Files.size(path);
            return path + "#" + modifiedAt + "#" + size + "#" + directory;
        } catch (IOException e) {
            return path + "#error";
        }
    }

    private ProjectRootResolution locateProjectRoot(Path workspaceRoot, Path requestedDir) {
        Path current = requestedDir;
        ProjectRootResolution gitFallback = null;
        while (current != null && current.startsWith(workspaceRoot)) {
            List<String> markers = detectProjectMarkers(current);
            if (!markers.isEmpty()) {
                return new ProjectRootResolution(current, markers.contains(".git") && markers.size() == 1 ? "git" : "manifest", markers);
            }
            if (Files.exists(current.resolve(".git")) && gitFallback == null) {
                gitFallback = new ProjectRootResolution(current, "git", List.of(".git"));
            }
            if (current.equals(workspaceRoot)) {
                break;
            }
            current = current.getParent();
        }
        if (gitFallback != null) {
            return gitFallback;
        }
        return new ProjectRootResolution(requestedDir, "directory", List.of());
    }

    private List<String> detectProjectMarkers(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        List<String> candidates = List.of(
                "package.json",
                "pom.xml",
                "build.gradle",
                "settings.gradle",
                "pyproject.toml",
                "requirements.txt",
                "Cargo.toml",
                "go.mod",
                ".git"
        );
        List<String> markers = new ArrayList<>();
        for (String candidate : candidates) {
            if (Files.exists(directory.resolve(candidate))) {
                markers.add(candidate);
            }
        }
        return List.copyOf(markers);
    }

    private String resolveProjectName(Path projectDir) {
        if (projectDir == null) {
            return "";
        }
        Path fileName = projectDir.getFileName();
        return fileName != null ? fileName.toString() : projectDir.toString();
    }

    private List<String> resolveKeyFiles(List<Path> entries) {
        List<String> priority = List.of(
                "AGENTS.md",
                "README.md",
                "README_zh.md",
                "PROJECT_CACHE.md",
                "package.json",
                "pnpm-lock.yaml",
                "pom.xml",
                "build.gradle",
                "settings.gradle",
                "Dockerfile",
                "docker-compose.yml",
                "docs",
                "src"
        );
        Set<String> existing = entries.stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return priority.stream().filter(existing::contains).toList();
    }

    private List<String> resolveModuleHints(List<Path> entries) {
        return entries.stream()
                .filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .filter(name -> !name.startsWith("."))
                .filter(name -> !Set.of("node_modules", "target", "dist", "build", "logs", "data", "tmp").contains(name.toLowerCase(Locale.ROOT)))
                .limit(8)
                .toList();
    }

    private List<String> resolveMaterialIndexHints(Path projectDir) {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            return List.of();
        }
        LinkedHashMap<String, Integer> topLevelCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> extensionCounts = new LinkedHashMap<>();
        List<String> sampleFiles = new ArrayList<>();
        int scanned = 0;
        boolean truncated = false;

        try (Stream<Path> stream = Files.walk(projectDir, MATERIAL_INDEX_MAX_DEPTH)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnoredProjectPath(projectDir, path))
                    .sorted(Comparator.comparing(path -> projectDir.relativize(path).toString().toLowerCase(Locale.ROOT)))
                    .limit(MATERIAL_INDEX_MAX_FILES + 1L)
                    .toList();
            truncated = files.size() > MATERIAL_INDEX_MAX_FILES;
            for (Path file : files.stream().limit(MATERIAL_INDEX_MAX_FILES).toList()) {
                scanned++;
                String relative = normalizeProjectRelativePath(projectDir, file);
                String top = topLevelSegment(relative);
                if (!top.isBlank()) {
                    topLevelCounts.merge(top, 1, Integer::sum);
                }
                String extension = extensionOf(file);
                extensionCounts.merge(extension != null ? extension : "(no extension)", 1, Integer::sum);
                if (sampleFiles.size() < 10 && isLikelyMaterialFile(relative, extension)) {
                    sampleFiles.add(relative);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to build material index hints for {}: {}", projectDir, e.getMessage());
            return List.of();
        }

        List<String> hints = new ArrayList<>();
        hints.add("Cached directory index: scanned " + scanned + " files"
                + (truncated ? " (truncated, ask to refresh or narrow path for deeper scan)" : "")
                + ". Reuse this before calling list_directory/index_directory_materials again.");
        if (!topLevelCounts.isEmpty()) {
            hints.add("Top folders/files: " + summarizeCounts(topLevelCounts, 8));
        }
        if (!extensionCounts.isEmpty()) {
            hints.add("File types: " + summarizeCounts(extensionCounts, 8));
        }
        if (!sampleFiles.isEmpty()) {
            hints.add("Likely material files: " + String.join(", ", sampleFiles));
        }
        hints.add("Refresh policy: do not rescan broad directories unless the user asks to refresh/sync, the cache is missing, or a specific file/subfolder must be inspected.");
        return List.copyOf(hints);
    }

    private boolean isIgnoredProjectPath(Path projectDir, Path path) {
        String normalized = normalizeProjectRelativePath(projectDir, path).toLowerCase(Locale.ROOT);
        return normalized.startsWith(".git/")
                || normalized.startsWith("node_modules/")
                || normalized.startsWith("target/")
                || normalized.startsWith("dist/")
                || normalized.startsWith("build/")
                || normalized.startsWith("logs/")
                || normalized.startsWith("data/")
                || normalized.startsWith("tmp/")
                || normalized.contains("/node_modules/")
                || normalized.contains("/target/")
                || normalized.contains("/dist/")
                || normalized.contains("/build/");
    }

    private String normalizeProjectRelativePath(Path projectDir, Path path) {
        if (projectDir == null || path == null) {
            return "";
        }
        return projectDir.relativize(path).toString().replace('\\', '/');
    }

    private String topLevelSegment(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        int slash = relativePath.indexOf('/');
        return slash >= 0 ? relativePath.substring(0, slash) : relativePath;
    }

    private String extensionOf(Path file) {
        if (file == null || file.getFileName() == null) {
            return null;
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) {
            return null;
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private boolean isLikelyMaterialFile(String relativePath, String extension) {
        String lower = relativePath != null ? relativePath.toLowerCase(Locale.ROOT) : "";
        if (lower.contains("readme") || lower.contains("教材") || lower.contains("资料")
                || lower.contains("素材") || lower.contains("题") || lower.contains("exam")
                || lower.contains("doc") || lower.contains("wiki")) {
            return true;
        }
        return Set.of(".md", ".txt", ".pdf", ".doc", ".docx").contains(extension);
    }

    private String summarizeCounts(LinkedHashMap<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .limit(limit)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private List<String> resolveStackHints(Path projectDir, Set<String> lowerNames) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (lowerNames.contains("pom.xml") || lowerNames.contains("build.gradle") || lowerNames.contains("settings.gradle")) {
            hints.add("Java");
        }
        if (lowerNames.contains("package.json")) {
            hints.add("Node.js");
        }
        if (lowerNames.contains("vite.config.ts") || lowerNames.contains("vite.config.js")) {
            hints.add("Vite");
        }
        if (lowerNames.contains("tsconfig.json")) {
            hints.add("TypeScript");
        }
        if (lowerNames.contains("dockerfile") || lowerNames.contains("docker-compose.yml")) {
            hints.add("Docker");
        }
        if (lowerNames.contains("electron") || lowerNames.contains("electron-builder.yml") || lowerNames.contains("builder-effective-config.yaml")) {
            hints.add("Electron");
        }
        if (lowerNames.contains("requirements.txt") || lowerNames.contains("pyproject.toml")) {
            hints.add("Python");
        }

        Path srcDir = projectDir.resolve("src");
        if (Files.isDirectory(srcDir)) {
            if (Files.exists(srcDir.resolve("App.vue")) || Files.exists(srcDir.resolve("main.ts"))) {
                hints.add("Vue 3");
            }
            if (Files.isDirectory(srcDir.resolve("main/java"))) {
                hints.add("Spring Boot");
            }
        }
        return List.copyOf(hints);
    }

    private String resolvePackageManager(Set<String> lowerNames) {
        if (lowerNames.contains("pnpm-lock.yaml")) return "pnpm";
        if (lowerNames.contains("yarn.lock")) return "yarn";
        if (lowerNames.contains("package-lock.json")) return "npm";
        if (lowerNames.contains("package.json")) return "npm";
        return "";
    }

    private String resolveBuildSystem(Set<String> lowerNames) {
        if (lowerNames.contains("pom.xml")) return "maven";
        if (lowerNames.contains("build.gradle") || lowerNames.contains("settings.gradle")) return "gradle";
        if (lowerNames.contains("package.json")) return "node";
        if (lowerNames.contains("pyproject.toml") || lowerNames.contains("requirements.txt")) return "python";
        return "";
    }

    private List<String> resolveCommandHints(Path projectDir, Set<String> lowerNames, String packageManager, String buildSystem) {
        LinkedHashSet<String> commands = new LinkedHashSet<>();
        if (lowerNames.contains("package.json")) {
            commands.addAll(resolveNodeScriptCommands(projectDir.resolve("package.json"), packageManager));
        }
        if (commands.isEmpty()) {
            if ("maven".equals(buildSystem)) {
                commands.add("mvn test");
                commands.add("mvn package");
            } else if ("gradle".equals(buildSystem)) {
                commands.add("./gradlew test");
                commands.add("./gradlew build");
            } else if ("python".equals(buildSystem)) {
                commands.add("pytest");
            }
        }
        return commands.stream().limit(6).toList();
    }

    private List<String> resolveNodeScriptCommands(Path packageJsonPath, String packageManager) {
        if (!Files.exists(packageJsonPath)) {
            return List.of();
        }
        try {
            Map<String, Object> packageJson = objectMapper.readValue(packageJsonPath.toFile(), new TypeReference<Map<String, Object>>() {});
            Object scriptsValue = packageJson.get("scripts");
            if (!(scriptsValue instanceof Map<?, ?> scripts)) {
                return List.of();
            }
            String runner = "yarn".equals(packageManager)
                    ? "yarn"
                    : "pnpm".equals(packageManager)
                    ? "pnpm"
                    : "npm run";
            List<String> priority = List.of("dev", "start", "build", "test", "lint", "typecheck");
            LinkedHashSet<String> commands = new LinkedHashSet<>();
            for (String scriptName : priority) {
                if (scripts.containsKey(scriptName)) {
                    commands.add(("yarn".equals(packageManager) ? "yarn " : "pnpm".equals(packageManager) ? "pnpm " : "npm run ") + scriptName);
                }
            }
            return List.copyOf(commands);
        } catch (Exception e) {
            log.debug("Failed to parse package.json scripts for {}: {}", packageJsonPath, e.getMessage());
            return List.of();
        }
    }

    private String relativizePath(Path root, Path path) {
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedPath = path.toAbsolutePath().normalize();
            if (normalizedRoot.equals(normalizedPath)) {
                return "";
            }
            return normalizedRoot.relativize(normalizedPath).toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toString().replace('\\', '/');
        }
    }

    private Path findGitRoot(Path start, Path workspaceRoot) {
        Path current = start;
        while (current != null && current.startsWith(workspaceRoot)) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (current.equals(workspaceRoot)) {
                break;
            }
            current = current.getParent();
        }
        return null;
    }

    private List<ProjectChangeSummaryItem> loadGitProjectSnapshot(Path gitRoot, Path projectDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "-C", gitRoot.toString(), "status", "--porcelain=1", "--untracked-files=all");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isBlank()) {
                return List.of();
            }

            String prefix = normalizeGitPath(gitRoot.relativize(projectDir));
            List<ProjectChangeSummaryItem> changedFiles = new ArrayList<>();
            for (String rawLine : output.split("\\r?\\n")) {
                if (rawLine == null || rawLine.isBlank() || rawLine.length() < 4) {
                    continue;
                }
                String status = rawLine.substring(0, 2);
                String payload = rawLine.substring(3).trim();
                if (payload.isBlank()) {
                    continue;
                }

                String primaryPath = payload;
                String secondaryPath = null;
                if (payload.contains(" -> ")) {
                    String[] renameParts = payload.split(" -> ", 2);
                    primaryPath = renameParts[0].trim();
                    secondaryPath = renameParts[1].trim();
                }

                String scopedPath = selectScopedPath(prefix, primaryPath, secondaryPath);
                if (scopedPath == null) {
                    continue;
                }

                ProjectChangeSummaryItem item = new ProjectChangeSummaryItem();
                item.setPath(scopedPath);
                item.setChangeType(mapGitChangeType(status, secondaryPath != null));
                changedFiles.add(item);
            }

            changedFiles.sort(Comparator.comparing(ProjectChangeSummaryItem::getPath, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(changedFiles);
        } catch (Exception e) {
            log.debug("Failed to load git status snapshot from {}: {}", gitRoot, e.getMessage());
            return List.of();
        }
    }

    private String selectScopedPath(String prefix, String primaryPath, String secondaryPath) {
        String normalizedPrimary = normalizeGitPath(primaryPath);
        String normalizedSecondary = normalizeGitPath(secondaryPath);
        if (isWithinProject(prefix, normalizedSecondary)) {
            return stripProjectPrefix(prefix, normalizedSecondary);
        }
        if (isWithinProject(prefix, normalizedPrimary)) {
            return stripProjectPrefix(prefix, normalizedPrimary);
        }
        return null;
    }

    private boolean isWithinProject(String prefix, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        return prefix.isBlank() || Objects.equals(prefix, candidate) || candidate.startsWith(prefix + "/");
    }

    private String stripProjectPrefix(String prefix, String candidate) {
        if (candidate == null) {
            return "";
        }
        if (prefix.isBlank()) {
            return candidate;
        }
        if (Objects.equals(prefix, candidate)) {
            return ".";
        }
        return candidate.substring(prefix.length() + 1);
    }

    private String normalizeGitPath(Path path) {
        return path == null ? "" : normalizeGitPath(path.toString());
    }

    private String normalizeGitPath(String path) {
        return path == null ? "" : path.replace('\\', '/').trim();
    }

    private String mapGitChangeType(String status, boolean renamed) {
        if (status == null) {
            return "modified";
        }
        if (status.contains("?")) {
            return "untracked";
        }
        if (renamed || status.indexOf('R') >= 0) {
            return "renamed";
        }
        if (status.indexOf('D') >= 0) {
            return "deleted";
        }
        if (status.indexOf('A') >= 0) {
            return "added";
        }
        return "modified";
    }

    private record ProjectRootResolution(Path projectRoot, String locatorType, List<String> markers) {}

    private record CachedProjectInsight(ProjectInsightSummary summary, String fingerprint, String scannedAt) {}

    private List<WorkspaceEntity> hydrateWorkspaceSettings(List<WorkspaceEntity> workspaces) {
        return workspaces == null ? List.of() : workspaces.stream().map(this::hydrateWorkspaceSettings).toList();
    }

    private WorkspaceEntity hydrateWorkspaceSettings(WorkspaceEntity entity) {
        if (entity == null) {
            return null;
        }
        entity.setProjectPermissionMode(extractProjectPermissionMode(entity.getSettingsJson()));
        entity.setWorkspacePolicy(extractWorkspacePolicy(entity.getSettingsJson()));
        return entity;
    }

    private String extractProjectPermissionMode(String settingsJson) {
        Map<String, Object> settings = readSettingsMap(settingsJson);
        Object value = settings.get(SETTINGS_KEY_PROJECT_PERMISSION_MODE);
        return normalizeProjectPermissionMode(value != null ? String.valueOf(value) : null);
    }

    private WorkspacePolicy extractWorkspacePolicy(String settingsJson) {
        Map<String, Object> settings = readSettingsMap(settingsJson);
        Object value = settings.get(SETTINGS_KEY_WORKSPACE_POLICY);
        if (value == null) {
            return normalizeWorkspacePolicy(null);
        }
        try {
            return normalizeWorkspacePolicy(objectMapper.convertValue(value, WorkspacePolicy.class));
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse workspace policy: {}", e.getMessage());
            return normalizeWorkspacePolicy(null);
        }
    }

    private String mergeSettingsJson(String existingJson, String incomingJson, String projectPermissionMode) {
        return mergeSettingsJson(existingJson, incomingJson, projectPermissionMode, null);
    }

    private String mergeSettingsJson(String existingJson, String incomingJson, String projectPermissionMode,
                                     WorkspacePolicy workspacePolicy) {
        Map<String, Object> merged = new LinkedHashMap<>(readSettingsMap(existingJson));
        merged.putAll(readSettingsMap(incomingJson));
        merged.put(SETTINGS_KEY_PROJECT_PERMISSION_MODE, normalizeProjectPermissionMode(projectPermissionMode));
        WorkspacePolicy normalizedWorkspacePolicy = workspacePolicy != null
                ? normalizeWorkspacePolicy(workspacePolicy)
                : normalizeWorkspacePolicy(extractWorkspacePolicyFromMerged(merged));
        merged.put(SETTINGS_KEY_WORKSPACE_POLICY, normalizedWorkspacePolicy);
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            throw new MateClawException("err.workspace.settings_invalid", "工作区配置保存失败: " + e.getMessage());
        }
    }

    private WorkspacePolicy extractWorkspacePolicyFromMerged(Map<String, Object> merged) {
        Object value = merged.get(SETTINGS_KEY_WORKSPACE_POLICY);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, WorkspacePolicy.class);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to convert merged workspace policy: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> readSettingsMap(String settingsJson) {
        if (settingsJson == null || settingsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(settingsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse workspace settingsJson: {}", e.getMessage());
            return Map.of();
        }
    }

    private String normalizeProjectPermissionMode(String mode) {
        if (PROJECT_PERMISSION_MODE_FULL.equalsIgnoreCase(mode)) {
            return PROJECT_PERMISSION_MODE_FULL;
        }
        return PROJECT_PERMISSION_MODE_LIMITED;
    }

    private WorkspacePolicy normalizeWorkspacePolicy(WorkspacePolicy policy) {
        WorkspacePolicy normalized = new WorkspacePolicy();
        normalized.setSandboxMode(normalizeSandboxMode(policy != null ? policy.getSandboxMode() : null));
        normalized.setApprovalPolicy(normalizeApprovalPolicy(policy != null ? policy.getApprovalPolicy() : null));
        normalized.setNetworkPolicy(normalizeNetworkPolicy(policy != null ? policy.getNetworkPolicy() : null));
        normalized.setAllowedPaths(normalizePathList(policy != null ? policy.getAllowedPaths() : null));
        normalized.setDeniedPaths(normalizePathList(policy != null ? policy.getDeniedPaths() : null));
        normalized.setRiskOverrides(normalizeRiskOverrides(policy != null ? policy.getRiskOverrides() : null));
        return normalized;
    }

    private List<String> normalizePathList(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        return paths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Map<String, String> normalizeRiskOverrides(Map<String, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        overrides.forEach((toolName, value) -> {
            if (toolName == null || toolName.isBlank() || value == null || value.isBlank()) {
                return;
            }
            normalized.put(toolName.trim(), value.trim().toLowerCase(Locale.ROOT));
        });
        return Map.copyOf(normalized);
    }

    private String normalizeSandboxMode(String mode) {
        if (WorkspacePolicy.SANDBOX_READ_ONLY.equalsIgnoreCase(mode)) {
            return WorkspacePolicy.SANDBOX_READ_ONLY;
        }
        if (WorkspacePolicy.SANDBOX_FULL_ACCESS.equalsIgnoreCase(mode)) {
            return WorkspacePolicy.SANDBOX_FULL_ACCESS;
        }
        return WorkspacePolicy.SANDBOX_WORKSPACE_WRITE;
    }

    private String normalizeApprovalPolicy(String policy) {
        if (WorkspacePolicy.APPROVAL_STRICT.equalsIgnoreCase(policy)) {
            return WorkspacePolicy.APPROVAL_STRICT;
        }
        return WorkspacePolicy.APPROVAL_DEFAULT;
    }

    private String normalizeNetworkPolicy(String policy) {
        if (WorkspacePolicy.NETWORK_DISABLED.equalsIgnoreCase(policy)) {
            return WorkspacePolicy.NETWORK_DISABLED;
        }
        if (WorkspacePolicy.NETWORK_RESTRICTED.equalsIgnoreCase(policy)) {
            return WorkspacePolicy.NETWORK_RESTRICTED;
        }
        return WorkspacePolicy.NETWORK_INHERIT;
    }
}
