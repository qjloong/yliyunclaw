package vip.mate.workspace.core.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import vip.mate.common.result.R;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;
import vip.mate.workspace.core.model.WorkspacePolicyEntity;
import vip.mate.workspace.core.model.WorkspaceProjectPermissionEntity;
import vip.mate.workspace.core.service.WorkspacePolicyService;
import vip.mate.workspace.core.service.WorkspaceProjectPermissionService;

import java.util.List;
import java.util.Map;

/**
 * 工作区策略与项目权限接口（dev1 定制，MetaY）
 *
 * @author MateClaw Team
 */
@Slf4j
@Tag(name = "工作区策略")
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspacePolicyController {

    private final WorkspacePolicyService policyService;
    private final WorkspaceProjectPermissionService projectPermissionService;

    @Operation(summary = "获取工作区策略")
    @GetMapping("/{id}/policy")
    @RequireWorkspaceRole("viewer")
    public R<WorkspacePolicyEntity> getPolicy(
            @PathVariable Long id,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        return R.ok(policyService.getByWorkspaceId(id));
    }

    @Operation(summary = "保存/更新工作区策略")
    @PutMapping("/{id}/policy")
    @RequireWorkspaceRole("admin")
    public R<WorkspacePolicyEntity> savePolicy(
            @PathVariable Long id,
            @RequestBody WorkspacePolicyEntity body,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        return R.ok(policyService.saveOrUpdate(id, body));
    }

    @Operation(summary = "列出工作区项目权限")
    @GetMapping("/{id}/project-permissions")
    @RequireWorkspaceRole("viewer")
    public R<List<WorkspaceProjectPermissionEntity>> listProjectPermissions(
            @PathVariable Long id,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        return R.ok(projectPermissionService.listByWorkspace(id));
    }

    @Operation(summary = "新增项目权限")
    @PostMapping("/{id}/project-permissions")
    @RequireWorkspaceRole("admin")
    public R<WorkspaceProjectPermissionEntity> addProjectPermission(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        Long memberUserId = body.get("memberUserId") != null
                ? Long.valueOf(String.valueOf(body.get("memberUserId"))) : null;
        String projectPath = (String) body.get("projectPath");
        String accessLevel = (String) body.getOrDefault("accessLevel", "READ");
        return R.ok(projectPermissionService.create(id, memberUserId, projectPath, accessLevel));
    }

    @Operation(summary = "删除项目权限")
    @DeleteMapping("/{id}/project-permissions/{permId}")
    @RequireWorkspaceRole("admin")
    public R<Void> deleteProjectPermission(
            @PathVariable Long id,
            @PathVariable Long permId,
            @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        projectPermissionService.delete(permId);
        return R.ok();
    }
}
