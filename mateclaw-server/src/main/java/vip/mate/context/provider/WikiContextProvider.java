package vip.mate.context.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.context.contract.ContextBudgetClass;
import vip.mate.context.contract.ContextLifecycleContract;
import vip.mate.context.contract.ContextProvenance;
import vip.mate.context.contract.ContextSourceType;
import vip.mate.wiki.model.WikiKnowledgeBaseEntity;
import vip.mate.wiki.service.WikiKnowledgeBaseService;

import java.util.List;

/**
 * WP-4 provider that surfaces wiki / knowledge-base route hints as context blocks.
 *
 * <p>This provider reuses {@link WikiKnowledgeBaseService} and produces lightweight
 * route hints (not full grounding text) so that the LLM knows which KBs are available
 * without exceeding the router hint budget.
 *
 * @author MateClaw Team
 */
@Component
@RequiredArgsConstructor
public class WikiContextProvider implements ContextSourceProvider {

    private final WikiKnowledgeBaseService wikiKnowledgeBaseService;

    @Override
    public ContextSourceType getSourceType() {
        return ContextSourceType.WIKI;
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public List<ContextLifecycleContract.ContextBlock> produceBlocks(Long agentId, String conversationId,
                                                                      String userQuery, Long workspaceId) {
        if (agentId == null) {
            return List.of();
        }
        try {
            List<WikiKnowledgeBaseEntity> kbs = wikiKnowledgeBaseService.listByAgentId(agentId);
            if (kbs.isEmpty()) {
                return List.of();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Available knowledge bases:\n");
            for (WikiKnowledgeBaseEntity kb : kbs.stream().limit(4).toList()) {
                String label = kb.getExternalKey() != null && !kb.getExternalKey().isBlank()
                        ? kb.getName() + " [" + kb.getExternalKey() + "]"
                        : kb.getName();
                sb.append("- ").append(label).append("\n");
            }

            ContextProvenance provenance = ContextProvenance.of(
                    ContextSourceType.WIKI,
                    "agent:" + agentId,
                    400);
            return List.of(ContextLifecycleContract.ContextBlock.of(
                    sb.toString().trim(), provenance, ContextBudgetClass.ROUTER_HINT));
        } catch (Exception e) {
            return List.of();
        }
    }
}
