package vip.mate.context.provider;

import vip.mate.context.contract.ContextLifecycleContract;
import vip.mate.context.contract.ContextSourceType;

import java.util.List;

/**
 * Provider interface for a single context-source type (WP-4).
 *
 * <p>Each provider is responsible for producing {@link ContextLifecycleContract.ContextBlock}
 * instances of one {@link ContextSourceType}. The {@link ContextLifecycleContract} aggregator
 * collects blocks from all registered providers, sorts them by priority, and applies
 * budgeting/compaction.
 *
 * <p>Provider constraints:
 * <ul>
 *   <li>Must not throw; failures should return an empty list or a block with error metadata.</li>
 *   <li>Must set {@link vip.mate.context.contract.ContextProvenance#sourceType()} correctly.</li>
 *   <li>Should respect budget hints when available, but the aggregator owns final enforcement.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see ContextLifecycleContract
 */
public interface ContextSourceProvider {

    /**
     * The source type this provider produces.
     */
    ContextSourceType getSourceType();

    /**
     * Produce context blocks for the given turn.
     *
     * @param agentId        the agent id
     * @param conversationId the conversation id
     * @param userQuery      the current user message (may be null)
     * @param workspaceId    the workspace id
     * @return zero or more context blocks
     */
    List<ContextLifecycleContract.ContextBlock> produceBlocks(Long agentId, String conversationId,
                                                               String userQuery, Long workspaceId);

    /**
     * Priority for ordering among providers. Lower values = higher priority.
     * Recommended defaults:
     * <ul>
     *   <li>DIRECTIVE: 0</li>
     *   <li>PROJECT_CACHE: 10</li>
     *   <li>WORKSPACE_MEMORY: 20</li>
     *   <li>WIKI: 30</li>
     *   <li>SESSION_SEARCH: 40</li>
     *   <li>MEMORY_PREFETCH: 50</li>
     *   <li>ATTACHMENT: 60</li>
     *   <li>TOOL_RESULT: 70</li>
     *   <li>CONVERSATION_HISTORY: 80</li>
     * </ul>
     */
    default int getPriority() {
        return 100;
    }
}
