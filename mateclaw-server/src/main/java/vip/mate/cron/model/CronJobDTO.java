package vip.mate.cron.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务 DTO
 *
 * @author MateClaw Team
 */
@Data
public class CronJobDTO {

    private Long id;
    private String name;
    private String cronExpression;
    private String timezone;
    private Long agentId;
    /** 只读展示字段 */
    private String agentName;
    private String taskType;
    private String triggerMessage;
    private String requestBody;
    private String workingDirectory;
    private Boolean enabled;
    private LocalDateTime nextRunTime;
    private LocalDateTime lastRunTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 只读：Agent 所属 workspace 名称 */
    private String workspaceName;

    /** 只读：workspace 根目录 */
    private String workspaceBasePath;

    /** 只读：当前 cron 实际生效的 project 目录 */
    private String effectiveProjectPath;

    /** 只读：相对 workspace 根目录的 project 路径；根目录时为空 */
    private String projectRelativePath;

    /** 只读：是否直接运行在 workspace 根目录 */
    private Boolean usingWorkspaceRoot;

    /** 只读：当前 workspace 的 Project 权限模式 */
    private String projectPermissionMode;

    /** RFC-063r §2.9: originating channel binding (null = web-origin cron). */
    private Long channelId;

    /**
     * Read-only display name for the bound channel — populated by
     * {@code CronJobService.list()} via a batch lookup so the UI can show
     * "钉钉 / 飞书 / 微信" alongside the cron row without an extra request.
     */
    private String channelName;

    /** RFC-063r §2.9: delivery target detail (targetId / threadId / accountId). */
    private DeliveryConfig deliveryConfig;

    /**
     * RFC-063r §2.14: read-model field surfaced by CronJobMapper#selectListWithDeliveryStatus
     * (PR-3). One of NONE / PENDING / DELIVERED / NOT_DELIVERED, taken from
     * the most-recent run row. Out-only — never accepted on create/update.
     */
    private String lastDeliveryStatus;

    /** RFC-063r §2.14: out-only error detail for the most-recent delivery attempt. */
    private String lastDeliveryError;

    /** Read-only: latest execution summary state for the most recent run. */
    private String lastExecutionSummaryStatus;

    /** Read-only: latest execution summary detail for the most recent run. */
    private String lastExecutionSummaryText;

    public static CronJobDTO from(CronJobEntity entity) {
        CronJobDTO dto = new CronJobDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCronExpression(entity.getCronExpression());
        dto.setTimezone(entity.getTimezone());
        dto.setAgentId(entity.getAgentId());
        dto.setTaskType(entity.getTaskType());
        dto.setTriggerMessage(entity.getTriggerMessage());
        dto.setRequestBody(entity.getRequestBody());
        dto.setWorkingDirectory(entity.getWorkingDirectory());
        dto.setEnabled(entity.getEnabled());
        dto.setNextRunTime(entity.getNextRunTime());
        dto.setLastRunTime(entity.getLastRunTime());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setChannelId(entity.getChannelId());
        dto.setDeliveryConfig(entity.getDeliveryConfig());
        // RFC-063r §2.14: surface the latest-run delivery snapshot when the
        // entity was loaded via selectListWithDeliveryStatus / selectByIdWithDeliveryStatus.
        // Default "NONE" when no run has ever been recorded so the UI can
        // render a neutral badge instead of a blank cell.
        dto.setLastDeliveryStatus(entity.getLastDeliveryStatus() != null
                ? entity.getLastDeliveryStatus() : "NONE");
        dto.setLastDeliveryError(entity.getLastDeliveryError());
        dto.setLastExecutionSummaryStatus(entity.getLastExecutionSummaryStatus());
        dto.setLastExecutionSummaryText(entity.getLastExecutionSummaryText());
        return dto;
    }

    public static CronJobDTO from(CronJobEntity entity, String agentName) {
        CronJobDTO dto = from(entity);
        dto.setAgentName(agentName);
        return dto;
    }

    public CronJobEntity toEntity() {
        CronJobEntity entity = new CronJobEntity();
        entity.setId(this.id);
        entity.setName(this.name);
        entity.setCronExpression(this.cronExpression);
        entity.setTimezone(this.timezone);
        entity.setAgentId(this.agentId);
        entity.setTaskType(this.taskType);
        entity.setTriggerMessage(this.triggerMessage);
        entity.setRequestBody(this.requestBody);
        entity.setWorkingDirectory(this.workingDirectory);
        entity.setEnabled(this.enabled);
        entity.setChannelId(this.channelId);
        entity.setDeliveryConfig(this.deliveryConfig);
        return entity;
    }
}
