package vip.mate.workspace.core.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作区策略实体（dev1 定制，MetaY）
 *
 * <p>承载沙箱模式、审批策略、网络策略以及允许/拒绝的动作与风险覆盖，
 * 由工具护栏在运行时读取并收紧裁决（策略缺失时回退既有默认逻辑）。
 *
 * @author MateClaw Team
 */
@Data
@TableName("mate_workspace_policy")
public class WorkspacePolicyEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作区 ID（唯一） */
    @TableField(value = "workspace_id")
    private Long workspaceId;

    /** 沙箱模式：OFF / ENFORCED / WARN */
    @TableField(value = "sandbox_mode")
    private String sandboxMode;

    /** 审批策略：NONE / AUTO / ALWAYS */
    @TableField(value = "approval_policy")
    private String approvalPolicy;

    /** 网络策略：ALLOW / DENY_EXTERNAL / SANDBOX_ONLY */
    @TableField(value = "network_policy")
    private String networkPolicy;

    /** 允许的动作 JSON 数组（工具名 / 动作标识） */
    @TableField(value = "allowed_actions_json")
    private String allowedActionsJson;

    /** 拒绝的动作 JSON 数组 */
    @TableField(value = "denied_actions_json")
    private String deniedActionsJson;

    /** 风险覆盖 JSON（severity -> decision） */
    @TableField(value = "risk_overrides_json")
    private String riskOverridesJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
