package vip.mate.plugin.api.agent;

/**
 * Augment the agent's system prompt with business-specific rules.
 * All registered beans are automatically applied during agent build.
 */
public interface AgentPromptAugmenter {
    boolean supports(AgentContext context);
    String augmentSystemPrompt(AgentContext context);
}
