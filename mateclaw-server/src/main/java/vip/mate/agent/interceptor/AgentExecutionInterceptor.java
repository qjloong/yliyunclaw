package vip.mate.agent.interceptor;

import reactor.core.publisher.Flux;
import vip.mate.agent.AgentService;
import vip.mate.plugin.api.agent.AgentContext;

import java.util.Map;
import java.util.Optional;

/**
 * Intercept agent execution before the plan-execute / ReAct graph starts.
 * All registered beans are auto-injected at runtime.
 *
 * @author MateClaw Team
 */
public interface AgentExecutionInterceptor {
    boolean supports(AgentContext context);

    default Optional<Flux<AgentService.StreamDelta>> beforeExecution(
            String userMessage, String conversationId, AgentContext context) {
        return Optional.empty();
    }

    default void enrichInitialState(Map<String, Object> inputs, AgentContext context) {
    }

    default String transformMessage(String userMessage, String conversationId, AgentContext context) {
        return userMessage;
    }
}
