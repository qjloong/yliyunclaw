package vip.mate.context.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.context.contract.ContextBudgetClass;
import vip.mate.context.contract.ContextLifecycleContract;
import vip.mate.context.contract.ContextProvenance;
import vip.mate.context.contract.ContextSourceType;
import vip.mate.memory.search.SessionSearchResult;
import vip.mate.memory.search.SessionSearchService;

import java.util.List;

/**
 * WP-4 provider that surfaces recent session recall hints as context blocks.
 *
 * <p>This provider reuses {@link SessionSearchService} and produces lightweight
 * session hints so that the LLM can reference prior conversations without
 * full history injection.
 *
 * @author MateClaw Team
 */
@Component
@RequiredArgsConstructor
public class SessionContextProvider implements ContextSourceProvider {

    private final SessionSearchService sessionSearchService;

    @Override
    public ContextSourceType getSourceType() {
        return ContextSourceType.SESSION_SEARCH;
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public List<ContextLifecycleContract.ContextBlock> produceBlocks(Long agentId, String conversationId,
                                                                      String userQuery, Long workspaceId) {
        if (agentId == null || userQuery == null || userQuery.isBlank()) {
            return List.of();
        }
        try {
            List<SessionSearchResult> results = sessionSearchService.search(agentId, conversationId, userQuery, 3);
            if (results.isEmpty()) {
                return List.of();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Recent relevant sessions:\n");
            for (SessionSearchResult r : results) {
                String title = r.title() != null ? r.title() : r.conversationId();
                String snippet = r.snippet() != null ? r.snippet() : "";
                if (snippet.length() > 120) {
                    snippet = snippet.substring(0, 120) + "...";
                }
                sb.append("- ").append(title).append(": ").append(snippet).append("\n");
            }

            ContextProvenance provenance = ContextProvenance.of(
                    ContextSourceType.SESSION_SEARCH,
                    "agent:" + agentId,
                    400);
            return List.of(ContextLifecycleContract.ContextBlock.of(
                    sb.toString().trim(), provenance, ContextBudgetClass.ROUTER_HINT));
        } catch (Exception e) {
            return List.of();
        }
    }
}
