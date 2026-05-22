package vip.mate.lifecycle.contract;

/**
 * Canonical categories for lifecycle events, unifying the vocabularies used by
 * {@link vip.mate.agent.GraphEventPublisher} (harness events) and
 * {@link vip.mate.hook.event.MateHookEvent} (hook events) (WP-5).
 *
 * <p>Each category corresponds to one subsystem state machine:
 * <ul>
 *   <li>{@code RUN} — a harness run starts, progresses, or ends</li>
 *   <li>{@code PHASE} — a high-level phase transition (planning, reasoning, summarizing)</li>
 *   <li>{@code STEP} — a plan step starts or completes</li>
 *   <li>{@code TOOL} — a tool call is requested, started, completed, or blocked</li>
 *   <li>{@code APPROVAL} — an approval is requested, granted, or denied</li>
 *   <li>{@code SESSION} — a conversation session starts or ends</li>
 *   <li>{@code MEMORY} — a memory write, summarize, or consolidate event</li>
 *   <li>{@code CONTEXT} — a context assembly or compaction event</li>
 *   <li>{@code HOOK} — a hook action is dispatched or completes</li>
 *   <li>{@code SYSTEM} — low-level system events (startup, health, config change)</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see LifecycleEventEnvelope
 * @see EventNormalizationContract
 */
public enum LifecycleEventCategory {

    RUN,
    PHASE,
    STEP,
    TOOL,
    APPROVAL,
    SESSION,
    MEMORY,
    CONTEXT,
    HOOK,
    SYSTEM
}
