package vip.mate.auth.yliyun;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 云盘工作区用户映射实体 (mc_workspace_user)
 * <p>
 * 关联 Yliyun 云盘用户与 MateClaw 工作区成员关系。
 * 通过 yliyun_user_id 查询可快速找到对应的 MateClaw 用户。
 *
 * @author MateClaw Team
 */
@Data
@TableName("mc_workspace_user")
public class McWorkspaceUserEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作区 ID */
    private Long workspaceId;

    /** MateClaw 用户 ID */
    private Long userId;

    /** 角色：owner / admin / member / viewer */
    private String role;

    /** Yliyun 用户 ID */
    private String yliyunUserId;

    /** Yliyun 租户 ID */
    private String yliyunTenantId;

    /** 最近一次云盘 SSO 验证通过的应用 entitlement。 */
    private String appKey;

    /** 云盘租户应用策略版本；MCP 调用时用于关闭/改权即时失效。 */
    private Integer configVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
