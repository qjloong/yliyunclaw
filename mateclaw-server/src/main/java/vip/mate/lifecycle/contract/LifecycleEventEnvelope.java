package vip.mate.lifecycle.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Normalized event envelope that both harness events and hook events can be mapped
 * into (WP-5).
 *
 * <p>The envelope is intentionally minimal so that it can be produced from:
 * <ul>
 *   <li>{@code GraphEventPublisher} events (phase, plan_created, tool_call_started, etc.)</li>
 *   <li>{@code MateHookEvent} families (agent, tool, session, memory, wiki, channel, cron)</li>
 *   <li>Spring application events adapted into the hook bus</li>
 * </ul>
 *
 * @param eventId      a unique event identifier (UUID or ULID)
 * @param category     the canonical lifecycle category
 * @param eventName    the original event name (preserved for backward compatibility)
 * @param timestamp    when the event occurred
 * @param agentId      the agent id if applicable
 * @param conversationId the conversation id if applicable
 * @param workspaceId  the workspace id if applicable
 * @param payload      event-specific key/value payload (e.g., toolName, stepIndex, status)
 * @param sourceSystem the originating system ("harness", "hook", "spring", "memory")
 * @author MateClaw Team
 */
public record LifecycleEventEnvelope(String eventId,
                                      LifecycleEventCategory category,
                                      String eventName,
                                      Instant timestamp,
                                      Long agentId,
                                      String conversationId,
                                      Long workspaceId,
                                      Map<String, Object> payload,
                                      String sourceSystem) {

    public LifecycleEventEnvelope {
        payload = payload != null ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload)) : Collections.emptyMap();
        if (timestamp == null) timestamp = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventId;
        private LifecycleEventCategory category;
        private String eventName;
        private Instant timestamp = Instant.now();
        private Long agentId;
        private String conversationId;
        private Long workspaceId;
        private Map<String, Object> payload = Collections.emptyMap();
        private String sourceSystem;

        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder category(LifecycleEventCategory category) { this.category = category; return this; }
        public Builder eventName(String eventName) { this.eventName = eventName; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder agentId(Long agentId) { this.agentId = agentId; return this; }
        public Builder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        public Builder workspaceId(Long workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder payload(Map<String, Object> payload) { this.payload = payload; return this; }
        public Builder sourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; return this; }

        public LifecycleEventEnvelope build() {
            return new LifecycleEventEnvelope(eventId, category, eventName, timestamp,
                    agentId, conversationId, workspaceId, payload, sourceSystem);
        }
    }
}
