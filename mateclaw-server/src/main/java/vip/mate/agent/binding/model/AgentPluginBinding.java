package vip.mate.agent.binding.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mate_agent_plugin")
public class AgentPluginBinding {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long agentId;
    private String pluginKey;
    private String capabilityPackId;
    private String stage;
    private String subject;
    private Boolean enabled;
    private String configJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    private Integer deleted;
}
