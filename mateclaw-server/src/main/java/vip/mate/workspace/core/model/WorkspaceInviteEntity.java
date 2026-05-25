package vip.mate.workspace.core.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mate_workspace_invite")
public class WorkspaceInviteEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long workspaceId;

    private String role;

    private Long invitedByUserId;

    private Integer maxUses;

    private Integer useCount;

    private String status;

    private LocalDateTime expiresAt;

    private Long lastAcceptedByUserId;

    private LocalDateTime lastAcceptedTime;

    @TableField(exist = false)
    private String inviterUsername;

    @TableField(exist = false)
    private String workspaceName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
