package vip.mate.workspace.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.workspace.core.model.WorkspacePolicyEntity;
import vip.mate.workspace.core.repository.WorkspacePolicyMapper;

/**
 * 工作区策略服务（dev1 定制，MetaY）
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspacePolicyService {

    private final WorkspacePolicyMapper policyMapper;

    public WorkspacePolicyEntity getByWorkspaceId(Long workspaceId) {
        if (workspaceId == null) return null;
        LambdaQueryWrapper<WorkspacePolicyEntity> w = new LambdaQueryWrapper<>();
        w.eq(WorkspacePolicyEntity::getWorkspaceId, workspaceId)
                .eq(WorkspacePolicyEntity::getDeleted, 0)
                .last("LIMIT 1");
        return policyMapper.selectOne(w);
    }

    /** 保存或更新指定工作区的策略（按 workspaceId 唯一） */
    public WorkspacePolicyEntity saveOrUpdate(Long workspaceId, WorkspacePolicyEntity req) {
        WorkspacePolicyEntity existing = getByWorkspaceId(workspaceId);
        if (existing == null) {
            req.setWorkspaceId(workspaceId);
            req.setDeleted(0);
            policyMapper.insert(req);
            return req;
        }
        existing.setSandboxMode(req.getSandboxMode());
        existing.setApprovalPolicy(req.getApprovalPolicy());
        existing.setNetworkPolicy(req.getNetworkPolicy());
        existing.setAllowedActionsJson(req.getAllowedActionsJson());
        existing.setDeniedActionsJson(req.getDeniedActionsJson());
        existing.setRiskOverridesJson(req.getRiskOverridesJson());
        policyMapper.updateById(existing);
        return existing;
    }
}
