package vip.mate.context;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.context.contract.ContextCompactionLevel;
import vip.mate.context.contract.ContextLifecycleContract;
import vip.mate.context.provider.ContextSourceProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * WP-4 aggregator that collects {@link ContextLifecycleContract.ContextBlock} instances
 * from all registered {@link ContextSourceProvider}s, sorts them by priority, and
 * applies a coarse-grained character budget before returning the assembled payload.
 *
 * <p>This service is the first step toward a unified context lifecycle. It does not
 * yet replace {@link vip.mate.agent.context.ContextRouterService} or
 * {@link vip.mate.agent.context.ConversationWindowManager}; it provides a parallel
 * <strong>typed and provenance-aware</strong> assembly path that those components can
 * migrate toward incrementally.
 *
 * <p>Non-goals:
 * <ul>
 *   <li>Does not perform token-level window fitting (still owned by {@code ConversationWindowManager}).</li>
 *   <li>Does not replace memory prefetch injection (still owned by {@code MemoryLifecycleMediator}).</li>
 *   <li>Does not attach to the graph runtime yet.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see ContextLifecycleContract
 * @see ContextSourceProvider
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextAssemblyService implements ContextLifecycleContract {

    private static final int TOTAL_CHAR_BUDGET = 2400;

    private final List<ContextSourceProvider> providers;

    @Override
    public List<ContextBlock> assembleContext(Long agentId, String conversationId, String userQuery,
                                               ChatOrigin origin) {
        Long workspaceId = origin != null ? origin.workspaceId() : null;

        List<ContextBlock> allBlocks = new ArrayList<>();
        List<ContextSourceProvider> orderedProviders = providers.stream()
                .sorted(Comparator.comparingInt(ContextSourceProvider::getPriority)
                        .thenComparing(provider -> provider.getSourceType().name()))
                .toList();

        for (ContextSourceProvider provider : orderedProviders) {
            try {
                List<ContextBlock> blocks = provider.produceBlocks(agentId, conversationId, userQuery, workspaceId);
                if (blocks != null) {
                    allBlocks.addAll(blocks);
                }
            } catch (Exception e) {
                log.debug("[WP-4] Context provider {} failed (non-fatal): {}",
                        provider.getSourceType(), e.getMessage());
            }
        }

        return applyCharBudget(allBlocks, TOTAL_CHAR_BUDGET);
    }

    @Override
    public List<ContextBlock> compactBlocks(List<ContextBlock> blocks, int tokenBudget) {
        // WP-4 first pass: coarse truncation only.
        // Token-aware compaction and level transitions will be added in WP-4 second pass.
        int remaining = tokenBudget * 4; // rough chars-per-token estimate
        List<ContextBlock> result = new ArrayList<>();
        for (ContextBlock block : blocks) {
            if (remaining <= 0) break;
            String content = block.content();
            if (content.length() > remaining) {
                content = content.substring(0, remaining) + "\n...[truncated]";
                block = new ContextBlock(content, block.provenance(), block.budgetClass(),
                        ContextCompactionLevel.TRIM);
            }
            result.add(block);
            remaining -= content.length();
        }
        return result;
    }

    private List<ContextBlock> applyCharBudget(List<ContextBlock> blocks, int budget) {
        int remaining = budget;
        List<ContextBlock> result = new ArrayList<>();
        for (ContextBlock block : blocks) {
            if (remaining <= 0) break;
            String content = block.content();
            if (content.length() > remaining) {
                content = content.substring(0, remaining) + "\n...[truncated]";
                block = new ContextBlock(content, block.provenance(), block.budgetClass(),
                        ContextCompactionLevel.TRIM);
            }
            result.add(block);
            remaining -= content.length();
        }
        return result;
    }
}
