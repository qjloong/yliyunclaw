package vip.mate.lifecycle.contract;

/**
 * Contract for normalizing heterogeneous event sources into one canonical
 * {@link LifecycleEventEnvelope} stream (WP-5).
 *
 * <p>This contract does not replace {@code GraphEventPublisher} or
 * {@code HookDispatcher}. It defines a <strong>mapping layer</strong> that
 * consumers can use when they need a unified event view (e.g., audit, diagnostics,
 * observability, cross-system triggers).
 *
 * @author MateClaw Team
 * @see LifecycleEventEnvelope
 * @see LifecycleEventCategory
 */
public interface EventNormalizationContract {

    /**
     * Map a harness event name to its canonical envelope.
     *
     * @param harnessEventName the event name emitted by {@code GraphEventPublisher}
     * @param payload          the event payload
     * @return the normalized envelope; never null
     */
    LifecycleEventEnvelope normalizeHarnessEvent(String harnessEventName,
                                                  java.util.Map<String, Object> payload);

    /**
     * Map a hook event family and type to its canonical envelope.
     *
     * @param hookFamily the hook event family (e.g., "agent", "tool", "session")
     * @param hookType   the specific hook type within the family
     * @param payload    the event payload
     * @return the normalized envelope; never null
     */
    LifecycleEventEnvelope normalizeHookEvent(String hookFamily, String hookType,
                                               java.util.Map<String, Object> payload);

    /**
     * Reverse map: given a canonical category and name, suggest the harness event name
     * that would produce it. Returns empty if no direct counterpart exists.
     */
    java.util.Optional<String> toHarnessEventName(LifecycleEventCategory category, String eventName);

    /**
     * Reverse map: given a canonical category and name, suggest the hook event family/type
     * that would produce it. Returns empty if no direct counterpart exists.
     */
    java.util.Optional<String> toHookEventFamily(LifecycleEventCategory category, String eventName);
}
