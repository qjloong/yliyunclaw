package vip.mate.context.contract;

import vip.mate.agent.context.ChatOrigin;

import java.util.List;

/**
 * Contract for the context assembly lifecycle: source selection, budgeting,
 * compaction, injection, and fallback (WP-4).
 *
 * <p>This contract does not replace {@link vip.mate.agent.context.ContextRouterService}
 * or {@link vip.mate.agent.context.ConversationWindowManager}. It defines the
 * canonical vocabulary and state boundaries that those components should converge
 * toward over time.
 *
 * <p>A context block is the unit of assembly. Each block carries:
 * <ul>
 *   <li>text content (the evidence to inject)</li>
 *   <li>{@link ContextProvenance} (where it came from)</li>
 *   <li>{@link ContextBudgetClass} (how its space is managed)</li>
 *   <li>{@link ContextCompactionLevel} (how much it has been reduced)</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see ContextSourceProvider
 */
public interface ContextLifecycleContract {

    /**
     * Assemble the full context payload for a single turn.
     *
     * @param agentId        the agent id
     * @param conversationId the conversation id
     * @param userQuery      the current user message
     * @param origin         workspace origin information (may be null)
     * @return an ordered list of context blocks; earlier blocks are higher priority
     */
    List<ContextBlock> assembleContext(Long agentId, String conversationId, String userQuery,
                                        ChatOrigin origin);

    /**
     * Apply compaction to a list of blocks under the given total token budget.
     *
     * @param blocks       the blocks to compact
     * @param tokenBudget  the maximum tokens allowed for these blocks
     * @return the compacted blocks; some may be removed or replaced with summaries
     */
    List<ContextBlock> compactBlocks(List<ContextBlock> blocks, int tokenBudget);

    /**
     * A single unit of context carrying content, provenance, budget class, and compaction state.
     */
    record ContextBlock(String content,
                         ContextProvenance provenance,
                         ContextBudgetClass budgetClass,
                         ContextCompactionLevel compactionLevel) {

        public ContextBlock {
            if (compactionLevel == null) compactionLevel = ContextCompactionLevel.FULL;
        }

        public static ContextBlock of(String content, ContextProvenance provenance,
                                       ContextBudgetClass budgetClass) {
            return new ContextBlock(content, provenance, budgetClass, ContextCompactionLevel.FULL);
        }
    }

}
