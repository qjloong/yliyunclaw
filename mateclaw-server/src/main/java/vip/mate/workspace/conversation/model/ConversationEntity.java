package vip.mate.workspace.conversation.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体
 *
 * @author MateClaw Team
 */
@Data
@TableName("mate_conversation")
public class ConversationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话唯一标识（前端生成的UUID） */
    private String conversationId;

    /** 会话标题（取第一条消息前20字） */
    private String title;

    /** 关联的 Agent ID */
    private Long agentId;

    /** 创建用户 */
    private String username;

    /** 消息数量 */
    private Integer messageCount;

    /** 最后一条消息摘要 */
    @TableField(value = "last_message", updateStrategy = FieldStrategy.ALWAYS)
    private String lastMessage;

    /** 最后活跃时间 */
    private LocalDateTime lastActiveTime;

    /** 流状态：idle（空闲）/ running（生成中） */
    private String streamStatus;

    /** 所属工作区 ID（默认 1 = default） */
    private Long workspaceId;

    /**
     * 会话级工作目录覆盖；为空时回退到工作区 basePath。
     */
    @TableField(value = "working_directory", updateStrategy = FieldStrategy.ALWAYS)
    private String workingDirectory;

    /**
    * 会话级运行模式；default = 跟随 Agent 默认类型，plan/coding = 强制走 Plan-Execute。
     */
    @TableField(value = "runtime_mode", updateStrategy = FieldStrategy.ALWAYS)
    private String runtimeMode;

    /**
     * 会话级运行时 Provider 覆盖；为空时回退到系统默认模型。
     */
    @TableField(value = "runtime_provider_id", updateStrategy = FieldStrategy.ALWAYS)
    private String runtimeProviderId;

    /**
     * 会话级运行时模型覆盖；为空时回退到系统默认模型。
     */
    @TableField(value = "runtime_model_name", updateStrategy = FieldStrategy.ALWAYS)
    private String runtimeModelName;

    /** 父会话 ID（委派场景下，子会话记录其父会话的 conversationId） */
    private String parentConversationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
