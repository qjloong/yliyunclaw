package vip.mate.workspace.core.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.service.AuthService;
import vip.mate.agent.context.ContextRouterService;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;
import vip.mate.workspace.core.model.ContextRouterSummary;
import vip.mate.workspace.core.model.ProjectInsightSummary;
import vip.mate.workspace.core.model.WorkspaceEntity;
import vip.mate.workspace.core.model.WorkspaceInviteEntity;
import vip.mate.workspace.core.model.WorkspaceMemberEntity;
import vip.mate.workspace.core.service.WorkspaceService;

import java.util.List;
import java.util.Map;

/**
 * 工作区管理接口
 *
 * @author MateClaw Team
 */
@Tag(name = "工作区管理")
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final AuthService authService;
    private final ContextRouterService contextRouterService;

    // ==================== 工作区 CRUD ====================

    @Operation(summary = "获取当前用户的工作区列表")
    @GetMapping
    public R<List<WorkspaceEntity>> list(Authentication auth) {
        Long userId = resolveUserId(auth);
        return R.ok(workspaceService.listByUserId(userId));
    }

    @Operation(summary = "获取工作区详情")
    @GetMapping("/{id}")
    public R<WorkspaceEntity> get(@PathVariable Long id) {
        return R.ok(workspaceService.getById(id));
    }

    @Operation(summary = "浏览工作区目录")
    @GetMapping("/{id}/directories")
    public R<Map<String, Object>> listDirectories(@PathVariable Long id,
                                                  @RequestParam(value = "path", required = false) String path,
                                                  Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "viewer");
        return R.ok(workspaceService.listDirectories(id, path));
    }

    @Operation(summary = "获取项目理解缓存摘要")
    @GetMapping("/{id}/project-insight")
    public R<ProjectInsightSummary> getProjectInsight(@PathVariable Long id,
                                                      @RequestParam(value = "path", required = false) String path,
                                                      Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "viewer");
        return R.ok(workspaceService.getProjectInsight(id, path));
    }

    @Operation(summary = "获取统一上下文路由摘要")
    @GetMapping("/{id}/context-router")
    public R<ContextRouterSummary> getContextRouter(@PathVariable Long id,
                                                    @RequestParam(value = "agentId", required = false) Long agentId,
                                                    @RequestParam(value = "conversationId", required = false) String conversationId,
                                                    @RequestParam(value = "path", required = false) String path,
                                                    Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "viewer");
        return R.ok(contextRouterService.summarize(id, agentId, conversationId, path));
    }

    @Operation(summary = "创建工作区")
    @PostMapping
    public R<WorkspaceEntity> create(@RequestBody WorkspaceEntity entity, Authentication auth) {
        Long userId = resolveUserId(auth);
        return R.ok(workspaceService.create(entity, userId));
    }

    @Operation(summary = "选择本地目录并创建工作区")
    @PostMapping("/pick-and-create")
    public R<WorkspaceEntity> pickAndCreate(Authentication auth) {
        Long userId = resolveUserId(auth);
        return R.ok(workspaceService.pickDirectoryAndCreate(userId));
    }

    @Operation(summary = "更新工作区")
    @PutMapping("/{id}")
    public R<WorkspaceEntity> update(@PathVariable Long id, @RequestBody WorkspaceEntity entity, Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "admin");
        entity.setId(id);
        return R.ok(workspaceService.update(entity));
    }

    @Operation(summary = "删除工作区")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "owner");
        workspaceService.delete(id);
        return R.ok();
    }

    // ==================== 成员管理 ====================

    @Operation(summary = "获取工作区成员列表")
    @GetMapping("/{id}/members")
    public R<List<WorkspaceMemberEntity>> listMembers(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "viewer");
        List<WorkspaceMemberEntity> members = workspaceService.listMembers(id);
        // 填充用户名/昵称
        for (WorkspaceMemberEntity m : members) {
            UserEntity user = authService.findById(m.getUserId());
            if (user != null) {
                m.setUsername(user.getUsername());
                m.setNickname(user.getNickname());
            }
        }
        return R.ok(members);
    }

    @Operation(summary = "添加工作区成员")
    @PostMapping("/{id}/members")
    public R<WorkspaceMemberEntity> addMember(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body,
                                               Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "admin");

        Long targetUserId;
        if (body.containsKey("username")) {
            String username = body.get("username").toString().trim();
            String password = body.containsKey("password") && body.get("password") != null
                    ? body.get("password").toString().trim() : null;
            UserEntity target = authService.findByUsername(username);
            if (target == null) {
                // User does not exist — create account (password required)
                if (password == null || password.isBlank()) {
                    throw new MateClawException("err.workspace.user_not_found",
                            "User not found: " + username + ". Provide a password to create the account.");
                }
                UserEntity newUser = new UserEntity();
                newUser.setUsername(username);
                newUser.setPassword(password);
                newUser.setNickname(body.containsKey("nickname")
                        ? body.get("nickname").toString() : username);
                target = authService.createUser(newUser);
            } else if (password != null && !password.isBlank()) {
                // User exists AND admin provided a password — reset it.
                // This fixes the case where an admin removes a member, re-adds
                // them with a new password, but the stale password blocks login.
                authService.resetPassword(target.getId(), password);
            }
            targetUserId = target.getId();
        } else {
            targetUserId = Long.valueOf(body.get("userId").toString());
        }
        String role = body.containsKey("role") ? body.get("role").toString() : "member";
        return R.ok(workspaceService.addMember(id, targetUserId, role));
    }

    @Operation(summary = "更新成员角色")
    @PutMapping("/{id}/members/{memberId}")
    public R<WorkspaceMemberEntity> updateMemberRole(@PathVariable Long id,
                                                      @PathVariable Long memberId,
                                                      @RequestBody Map<String, String> body,
                                                      Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "admin");
        return R.ok(workspaceService.updateMemberRole(id, memberId, body.get("role")));
    }

    @Operation(summary = "移除工作区成员")
    @DeleteMapping("/{id}/members/{memberId}")
    public R<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId, Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "admin");
        workspaceService.removeMember(id, memberId);
        return R.ok();
    }

    // ==================== 工具方法 ====================

    @Operation(summary = "创建工作区邀请链接")
    @PostMapping("/{id}/invite-links")
    public R<Map<String, Object>> createInviteLink(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body,
                                                   Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "admin");
        String role = body.containsKey("role") && body.get("role") != null ? body.get("role").toString() : "member";
        return R.ok(workspaceService.createInviteLink(id, userId, role));
    }

    @Operation(summary = "获取工作区邀请列表")
    @GetMapping("/{id}/invites")
    public R<List<WorkspaceInviteEntity>> listInvites(@PathVariable Long id, Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "admin");
        return R.ok(workspaceService.listInvites(id));
    }

    @Operation(summary = "撤销工作区邀请")
    @DeleteMapping("/{id}/invites/{inviteId}")
    public R<Void> revokeInvite(@PathVariable Long id, @PathVariable Long inviteId, Authentication auth) {
        Long userId = resolveUserId(auth);
        workspaceService.requirePermission(id, userId, "admin");
        workspaceService.revokeInvite(id, inviteId);
        return R.ok();
    }

    @Operation(summary = "预览工作区邀请")
    @GetMapping("/invites/{token}")
    public R<Map<String, Object>> previewInvite(@PathVariable String token, Authentication auth) {
        Long userId = resolveUserId(auth);
        return R.ok(workspaceService.previewInvite(token, userId));
    }

    @Operation(summary = "接受工作区邀请")
    @PostMapping("/invites/{token}/accept")
    public R<Map<String, Object>> acceptInvite(@PathVariable String token, Authentication auth) {
        Long userId = resolveUserId(auth);
        return R.ok(workspaceService.acceptInvite(token, userId));
    }

    private Long resolveUserId(Authentication auth) {
        String username = auth.getName();
        UserEntity user = authService.findByUsername(username);
        if (user == null) {
            throw new MateClawException("用户不存在: " + username);
        }
        return user.getId();
    }
}
