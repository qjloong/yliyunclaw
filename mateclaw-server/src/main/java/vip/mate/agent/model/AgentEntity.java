package vip.mate.agent.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 配置实体
 *
 * @author MateClaw Team
 */
@Data
@TableName("mate_agent")
public class AgentEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Agent 名称 */
    private String name;

    /** Agent 描述 */
    private String description;

    /** Agent 类型：react / plan_execute */
    private String agentType;

    /** 系统提示词 */
    @TableField(value = "system_prompt", updateStrategy = FieldStrategy.ALWAYS)
    private String systemPrompt;

    /**
     * 保留但不再生效：运行时统一使用全局默认模型（ModelConfigService.getDefaultModel()）。
     * 该字段为历史残留，仅保留以避免数据库迁移。
     */
    @Deprecated
    private String modelName;

    /** 最大迭代次数 */
    private Integer maxIterations;

    /** 是否启用 */
    private Boolean enabled;

    /** 图标（emoji 或 URL） */
    private String icon;

    /** 标签（逗号分隔） */
    private String tags;

    /** Built-in/system template id used to create this agent, if any. */
    private String templateId;

    /** Template version captured at creation time. */
    private String templateVersion;

    /** Template category, for example coding or education. */
    private String templateCategory;

    /** Template domain, for example software_engineering. */
    private String templateDomain;

    /** Bound AgentProfile id from the template manifest. */
    private String profileId;

    /** Bound CapabilityPack id from the template manifest. */
    private String capabilityPackId;

    /** Full template manifest snapshot captured when the agent was created. */
    @TableField("template_metadata_json")
    private String templateMetadataJson;

    /** Optional JSON array of wiki knowledge base ids explicitly bound to this agent. */
    @TableField(value = "knowledge_base_ids_json", updateStrategy = FieldStrategy.ALWAYS)
    private String knowledgeBaseIdsJson;

    @TableField(value = "home_subtitle", updateStrategy = FieldStrategy.ALWAYS)
    private String homeSubtitle;

    @TableField(value = "home_quick_starts_json", updateStrategy = FieldStrategy.ALWAYS)
    private String homeQuickStartsJson;

    /** 所属工作区 ID（默认 1 = default） */
    private Long workspaceId;

    /** 默认思考深度：off / low / medium / high / max，null 表示跟随模型默认 */
    private String defaultThinkingLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Integer deleted;
}
