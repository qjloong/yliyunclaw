package vip.mate.llm.workspace;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workspace-owned model list, defaults and runtime overrides.
 */
@Data
@TableName("mate_workspace_model_config")
public class WorkspaceModelConfigEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long workspaceId;
    private String name;
    private String provider;
    private String modelName;
    private String description;
    private Double temperature;
    private Integer maxTokens;
    private Integer maxInputTokens;
    private Integer requestTimeoutSeconds;
    private Double topP;
    private Boolean enableSearch;
    private String searchStrategy;
    private Boolean builtin;
    private Boolean enabled;
    private Boolean isDefault;
    private String modelType;
    private String modalities;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
