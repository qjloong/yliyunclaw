package vip.mate.cron;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.repository.AgentMapper;
import vip.mate.channel.ChannelSessionStore;
import vip.mate.channel.model.ChannelSessionEntity;
import vip.mate.cron.model.CronJobEntity;
import vip.mate.cron.model.DeliveryConfig;

import java.util.List;

/**
 * Single source of truth for the {@code conversationId} a cron run writes to.
 * <p>
 * Cron used to write every run to a per-job orphan conversation
 * ({@code "cron_" + job.getId()}). Those rows existed in {@code mate_conversation}
 * but had no entry in any sidebar — the user had no way to reach them. The
 * delivery pipeline ({@code CronResultDelivery}) covered the IM case (push
 * back to DingTalk / Feishu / etc.) but Web-origin cron jobs ended up with
 * {@code delivery_status='NONE'} and silent results.
 * <p>
 * The new policy:
 * <ul>
 *   <li>Web-origin cron (no {@code channelId}) → {@code "tasks_" + workspaceId}.
 *       A single workspace-scoped conversation pre-seeded as "📋 定时任务"
 *       (V65 migration). All Web cron output lands here so the user has one
 *       reliable place to look.</li>
 *   <li>IM-bound cron with an existing channel session that matches the
 *       delivery target → the session's conversationId. This makes the cron
 *       output appear inline in the IM mirror conversation — when the user
 *       opens the channel in Web Console, they see cron history alongside
 *       chat history, no separate inbox to check.</li>
 *   <li>IM-bound cron without a matching session yet (e.g. first run before
 *       the user has interacted with the channel) → fall back to the old
 *       per-job {@code cron_<id>} conversation so push delivery still works
 *       and the run isn't lost.</li>
 * </ul>
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CronConversationResolver {

    private final ChannelSessionStore channelSessionStore;
    private final AgentMapper agentMapper;

    public String resolve(CronJobEntity job) {
        if (job == null) return "tasks_1";

        // IM-bound cron: try to thread output into the existing channel session
        // so the IM mirror in Web Console shows it inline with regular chat.
        if (job.getChannelId() != null) {
            String sessionConvId = findChannelSessionConvId(job);
            if (sessionConvId != null) return sessionConvId;
            // No session yet — keep the legacy per-job conversation so push
            // delivery to the IM still works and the run isn't dropped.
            return "cron_" + job.getId();
        }

        // Web-origin cron: unified per-workspace tasks conversation.
        Long ws = resolveWorkspaceId(job);
        return "tasks_" + ws;
    }

    private Long resolveWorkspaceId(CronJobEntity job) {
        if (job == null || job.getAgentId() == null) {
            return 1L;
        }
        try {
            AgentEntity agent = agentMapper.selectById(job.getAgentId());
            return agent != null && agent.getWorkspaceId() != null ? agent.getWorkspaceId() : 1L;
        } catch (Exception e) {
            log.debug("[CronConvResolver] workspace lookup failed for job {}: {}",
                    job.getId(), e.getMessage());
            return 1L;
        }
    }

    /**
     * Find the existing channel session for the cron's creator. Match
     * priority:
     * <ol>
     *   <li>{@code (channelId, dc.targetId)} — matches when the channel adapter
     *       uses the same stable identifier for both the stored session target
     *       and the cron delivery target (Slack / Discord / Telegram).</li>
     *   <li>If it misses, return null and fall back to {@code cron_<id>}.</li>
     * </ol>
     */
    private String findChannelSessionConvId(CronJobEntity job) {
        DeliveryConfig dc = job.getDeliveryConfig();
        if (dc == null) return null;
        try {
            List<ChannelSessionEntity> sessions = channelSessionStore.listByChannelId(job.getChannelId());
            if (sessions.isEmpty()) return null;

            // Match by persisted delivery target when the adapter uses a stable targetId.
            if (dc.targetId() != null && !dc.targetId().isBlank()) {
                return sessions.stream()
                        .filter(s -> dc.targetId().equals(s.getTargetId()))
                        .map(ChannelSessionEntity::getConversationId)
                        .findFirst()
                        .orElse(null);
            }
            return null;
        } catch (Exception e) {
            log.debug("[CronConvResolver] session lookup failed for job {}: {}",
                    job.getId(), e.getMessage());
            return null;
        }
    }

    /** Reused by header insertion to know whether we are in the unified tasks view. */
    public boolean isWebOriginTasksConv(String conversationId) {
        return conversationId != null && conversationId.startsWith("tasks_");
    }
}
