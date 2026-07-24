package vip.mate.workspace.core.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作区项目级访问权限（dev1 定制，MetaY）
 *
 * <p>控制成员对某个相对项目路径（projectPath）的访问级别，
 * 不绕过工作区根目录边界（workspace roots）。
 *
 * @author MateClaw Team
 */
@Data
@TableName("mate_workspace_project_permission")
public class WorkspaceProjectPermissionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 工作区 ID */
    @TableField(value = "workspace_id")
    private Long workspaceId;

    /** 成员用户 ID */
    @TableField(value = "member_user_id")
    private Long memberUserId;

    /** 项目相对路径 */
    @TableField(value = "project_path")
    private String projectPath;

    /** 访问级别：READ / WRITE / ADMIN */
    @TableField(value = "access_level")
    private String accessLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
