package vip.mate.workspace.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.workspace.core.model.WorkspaceProjectPermissionEntity;
import vip.mate.workspace.core.repository.WorkspaceProjectPermissionMapper;

import java.util.List;

/**
 * 工作区项目级访问权限服务（dev1 定制，MetaY）
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceProjectPermissionService {

    private final WorkspaceProjectPermissionMapper mapper;

    public List<WorkspaceProjectPermissionEntity> listByWorkspace(Long workspaceId) {
        LambdaQueryWrapper<WorkspaceProjectPermissionEntity> w = new LambdaQueryWrapper<>();
        w.eq(WorkspaceProjectPermissionEntity::getWorkspaceId, workspaceId)
                .eq(WorkspaceProjectPermissionEntity::getDeleted, 0)
                .orderByAsc(WorkspaceProjectPermissionEntity::getProjectPath);
        return mapper.selectList(w);
    }

    public WorkspaceProjectPermissionEntity create(Long workspaceId, Long memberUserId, String projectPath, String accessLevel) {
        WorkspaceProjectPermissionEntity e = new WorkspaceProjectPermissionEntity();
        e.setWorkspaceId(workspaceId);
        e.setMemberUserId(memberUserId);
        e.setProjectPath(projectPath);
        e.setAccessLevel(accessLevel);
        e.setDeleted(0);
        mapper.insert(e);
        return e;
    }

    public void delete(Long id) {
        WorkspaceProjectPermissionEntity e = new WorkspaceProjectPermissionEntity();
        e.setId(id);
        e.setDeleted(1);
        mapper.updateById(e);
    }
}
