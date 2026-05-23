package vip.mate.context.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.context.contract.ContextBudgetClass;
import vip.mate.context.contract.ContextLifecycleContract;
import vip.mate.context.contract.ContextProvenance;
import vip.mate.context.contract.ContextSourceType;
import vip.mate.workspace.core.model.ProjectInsightSummary;
import vip.mate.workspace.core.service.WorkspaceService;

import java.util.ArrayList;
import java.util.List;

/**
 * WP-4 provider that surfaces project-cache hints (stack, key files, build system,
 * material index) as typed {@link ContextLifecycleContract.ContextBlock} instances.
 *
 * <p>This provider reuses {@link WorkspaceService#getProjectInsight(Long, String)}
 * but wraps the output with {@link ContextProvenance} so that the context lifecycle
 * can track freshness and budget.
 *
 * @author MateClaw Team
 */
@Component
@RequiredArgsConstructor
public class ProjectCacheContextProvider implements ContextSourceProvider {

    private final WorkspaceService workspaceService;

    @Override
    public ContextSourceType getSourceType() {
        return ContextSourceType.PROJECT_CACHE;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public List<ContextLifecycleContract.ContextBlock> produceBlocks(Long agentId, String conversationId,
                                                                      String userQuery, Long workspaceId) {
        if (workspaceId == null) {
            return List.of();
        }
        try {
            ProjectInsightSummary insight = workspaceService.getProjectInsight(workspaceId, null);
            if (insight == null) {
                return List.of();
            }

            List<ContextLifecycleContract.ContextBlock> blocks = new ArrayList<>();
            StringBuilder sb = new StringBuilder();

            if (insight.getStackHints() != null && !insight.getStackHints().isEmpty()) {
                sb.append("Stack: ").append(String.join(", ", insight.getStackHints())).append("\n");
            }
            if (insight.getBuildSystem() != null) {
                sb.append("Build: ").append(insight.getBuildSystem()).append("\n");
            }
            if (insight.getPackageManager() != null) {
                sb.append("Package: ").append(insight.getPackageManager()).append("\n");
            }
            if (insight.getKeyFiles() != null && !insight.getKeyFiles().isEmpty()) {
                sb.append("Key files: ").append(String.join(", ", insight.getKeyFiles())).append("\n");
            }
            if (insight.getMaterialIndexHints() != null && !insight.getMaterialIndexHints().isEmpty()) {
                sb.append("Material index: ").append(String.join(", ", insight.getMaterialIndexHints())).append("\n");
            }

            if (!sb.isEmpty()) {
                ContextProvenance provenance = ContextProvenance.of(
                        ContextSourceType.PROJECT_CACHE,
                        "workspace:" + workspaceId,
                        800);
                blocks.add(ContextLifecycleContract.ContextBlock.of(
                        sb.toString().trim(), provenance, ContextBudgetClass.ROUTER_HINT));
            }
            return blocks;
        } catch (Exception e) {
            return List.of();
        }
    }
}
