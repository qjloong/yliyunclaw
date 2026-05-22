package vip.mate.lifecycle.normalizer;

import org.springframework.stereotype.Component;
import vip.mate.lifecycle.contract.EventNormalizationContract;
import vip.mate.lifecycle.contract.LifecycleEventCategory;
import vip.mate.lifecycle.contract.LifecycleEventEnvelope;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * WP-5 implementation of {@link EventNormalizationContract}.
 *
 * <p>This component maps the existing event vocabularies from
 * {@code GraphEventPublisher} and {@code MateHookEvent} into the canonical
 * {@link LifecycleEventEnvelope} model. It is a pure mapping layer with no side
 * effects and no dependency on the event publishers themselves.
 *
 * <p>Mapping table (first-pass):
 * <table>
 *   <tr><th>Source</th><th>Original Name</th><th>Category</th></tr>
 *   <tr><td>harness</td><td>phase</td><td>PHASE</td></tr>
 *   <tr><td>harness</td><td>plan_created</td><td>STEP</td></tr>
 *   <tr><td>harness</td><td>plan_step_started</td><td>STEP</td></tr>
 *   <tr><td>harness</td><td>plan_step_completed</td><td>STEP</td></tr>
 *   <tr><td>harness</td><td>tool_call_started</td><td>TOOL</td></tr>
 *   <tr><td>harness</td><td>tool_call_completed</td><td>TOOL</td></tr>
 *   <tr><td>harness</td><td>tool_approval_requested</td><td>APPROVAL</td></tr>
 *   <tr><td>harness</td><td>tool_direct_result</td><td>TOOL</td></tr>
 *   <tr><td>harness</td><td>perf_summary</td><td>RUN</td></tr>
 *   <tr><td>hook</td><td>agent.*</td><td>RUN</td></tr>
 *   <tr><td>hook</td><td>tool.*</td><td>TOOL</td></tr>
 *   <tr><td>hook</td><td>session.*</td><td>SESSION</td></tr>
 *   <tr><td>hook</td><td>memory.*</td><td>MEMORY</td></tr>
 *   <tr><td>hook</td><td>wiki.*</td><td>CONTEXT</td></tr>
 *   <tr><td>hook</td><td>channel.*</td><td>SYSTEM</td></tr>
 *   <tr><td>hook</td><td>cron.*</td><td>SYSTEM</td></tr>
 * </table>
 *
 * @author MateClaw Team
 * @see EventNormalizationContract
 */
@Component
public class LifecycleEventNormalizer implements EventNormalizationContract {

    @Override
    public LifecycleEventEnvelope normalizeHarnessEvent(String harnessEventName,
                                                         Map<String, Object> payload) {
        LifecycleEventCategory category = mapHarnessEventToCategory(harnessEventName);
        return LifecycleEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .category(category)
                .eventName(harnessEventName)
                .payload(payload != null ? payload : Map.of())
                .sourceSystem("harness")
                .build();
    }

    @Override
    public LifecycleEventEnvelope normalizeHookEvent(String hookFamily, String hookType,
                                                      Map<String, Object> payload) {
        LifecycleEventCategory category = mapHookFamilyToCategory(hookFamily);
        String eventName = hookFamily + "." + hookType;
        return LifecycleEventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .category(category)
                .eventName(eventName)
                .payload(payload != null ? payload : Map.of())
                .sourceSystem("hook")
                .build();
    }

    @Override
    public Optional<String> toHarnessEventName(LifecycleEventCategory category, String eventName) {
        return switch (category) {
            case PHASE -> Optional.of("phase");
            case STEP -> Optional.of("plan_step_started");
            case TOOL -> Optional.of("tool_call_started");
            case APPROVAL -> Optional.of("tool_approval_requested");
            case RUN -> Optional.of("perf_summary");
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<String> toHookEventFamily(LifecycleEventCategory category, String eventName) {
        return switch (category) {
            case RUN -> Optional.of("agent");
            case TOOL -> Optional.of("tool");
            case SESSION -> Optional.of("session");
            case MEMORY -> Optional.of("memory");
            case CONTEXT -> Optional.of("wiki");
            case SYSTEM -> Optional.of("channel");
            default -> Optional.empty();
        };
    }

    private LifecycleEventCategory mapHarnessEventToCategory(String eventName) {
        return switch (eventName) {
            case "phase" -> LifecycleEventCategory.PHASE;
            case "plan_created", "plan_step_started", "plan_step_completed" -> LifecycleEventCategory.STEP;
            case "tool_call_started", "tool_call_completed", "tool_direct_result" -> LifecycleEventCategory.TOOL;
            case "tool_approval_requested" -> LifecycleEventCategory.APPROVAL;
            case "perf_summary" -> LifecycleEventCategory.RUN;
            default -> LifecycleEventCategory.SYSTEM;
        };
    }

    private LifecycleEventCategory mapHookFamilyToCategory(String family) {
        return switch (family) {
            case "agent" -> LifecycleEventCategory.RUN;
            case "tool" -> LifecycleEventCategory.TOOL;
            case "session" -> LifecycleEventCategory.SESSION;
            case "memory" -> LifecycleEventCategory.MEMORY;
            case "wiki" -> LifecycleEventCategory.CONTEXT;
            case "channel", "cron" -> LifecycleEventCategory.SYSTEM;
            default -> LifecycleEventCategory.SYSTEM;
        };
    }
}
