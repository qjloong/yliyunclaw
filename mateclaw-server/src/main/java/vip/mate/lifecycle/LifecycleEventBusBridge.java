package vip.mate.lifecycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.mate.agent.GraphEventPublisher;
import vip.mate.lifecycle.contract.EventNormalizationContract;
import vip.mate.lifecycle.contract.LifecycleEventEnvelope;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * WP-5 Spring-accessible bridge over the static {@link GraphEventPublisher}.
 *
 * <p>{@code GraphEventPublisher} is a pure static utility — it has no Spring context
 * access and cannot inject beans. This bridge is a {@code @Component} that wraps
 * {@link EventNormalizationContract} and exposes a normalize method for harness-side
 * callers that need to produce {@link LifecycleEventEnvelope} from {@link GraphEvent}s.
 *
 * <p>Usage from any Spring-managed service:
 * <pre>
 * LifecycleEventEnvelope envelope = lifecycleEventBusBridge.normalizeGraphEvent(graphEvent);
 * </pre>
 *
 * @author MateClaw Team
 * @see EventNormalizationContract
 * @see GraphEventPublisher
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LifecycleEventBusBridge {

    private final EventNormalizationContract eventNormalizer;

    /**
     * Normalize a harness {@link GraphEventPublisher.GraphEvent} into a
     * {@link LifecycleEventEnvelope}.
     *
     * <p>This is the canonical entry point for harness-side consumers (audit service,
     * observability pipeline, cross-system triggers) that want a unified event view
     * alongside hook events already normalized by {@link vip.mate.hook.HookDispatcher}.
     *
     * @param graphEvent the harness event to normalize
     * @return the normalized envelope; never null
     */
    public LifecycleEventEnvelope normalizeGraphEvent(GraphEventPublisher.GraphEvent graphEvent) {
        if (graphEvent == null || graphEvent.type() == null) {
            return emptyEnvelope("harness");
        }
        try {
            return eventNormalizer.normalizeHarnessEvent(graphEvent.type(), graphEvent.data());
        } catch (Exception e) {
            log.debug("[WP-5] Harness event normalization failed for {}: {}",
                    graphEvent.type(), e.getMessage());
            return LifecycleEventEnvelope.builder()
                    .eventId(UUID.randomUUID().toString())
                    .category(vip.mate.lifecycle.contract.LifecycleEventCategory.SYSTEM)
                    .eventName(graphEvent.type())
                    .timestamp(graphEvent.timestamp() > 0
                            ? java.time.Instant.ofEpochMilli(graphEvent.timestamp())
                            : java.time.Instant.now())
                    .payload(graphEvent.data() != null ? graphEvent.data() : Map.of())
                    .sourceSystem("harness")
                    .build();
        }
    }

    /**
     * Normalize a harness event by name and payload (convenience overload when a
     * {@link GraphEventPublisher.GraphEvent} record is not available).
     */
    public LifecycleEventEnvelope normalizeHarnessEvent(String harnessEventName, Map<String, Object> payload) {
        if (harnessEventName == null) {
            return emptyEnvelope("harness");
        }
        try {
            return eventNormalizer.normalizeHarnessEvent(harnessEventName, payload);
        } catch (Exception e) {
            log.debug("[WP-5] Harness event normalization failed for {}: {}",
                    harnessEventName, e.getMessage());
            return emptyEnvelope("harness");
        }
    }

    /**
     * Reverse-map: suggest a harness event name for a given canonical category.
     */
    public Optional<String> toHarnessEventName(vip.mate.lifecycle.contract.LifecycleEventCategory category,
                                               String eventName) {
        if (eventNormalizer == null) {
            return Optional.empty();
        }
        return eventNormalizer.toHarnessEventName(category, eventName);
    }

    private LifecycleEventEnvelope emptyEnvelope(String sourceSystem) {
        return LifecycleEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .sourceSystem(sourceSystem)
                .payload(Map.of())
                .build();
    }
}
