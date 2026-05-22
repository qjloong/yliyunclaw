package vip.mate.context.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.context.contract.ContextBudgetClass;
import vip.mate.context.contract.ContextLifecycleContract;
import vip.mate.context.contract.ContextProvenance;
import vip.mate.context.contract.ContextSourceType;
import vip.mate.workspace.document.WorkspaceFileService;
import vip.mate.workspace.document.model.WorkspaceFileEntity;

import java.util.List;

/**
 * WP-4 provider that surfaces enabled workspace memory files as context blocks.
 *
 * <p>This provider reuses {@link WorkspaceFileService} but limits output to files
 * that are explicitly enabled ({@code enabled = true}) and produces one block per file
 * with {@link ContextProvenance}.
 *
 * @author MateClaw Team
 */
@Component
@RequiredArgsConstructor
public class MemoryContextProvider implements ContextSourceProvider {

    private final WorkspaceFileService workspaceFileService;

    @Override
    public ContextSourceType getSourceType() {
        return ContextSourceType.WORKSPACE_MEMORY;
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public List<ContextLifecycleContract.ContextBlock> produceBlocks(Long agentId, String conversationId,
                                                                      String userQuery, Long workspaceId) {
        if (agentId == null) {
            return List.of();
        }
        try {
            List<WorkspaceFileEntity> files = workspaceFileService.listFiles(agentId);
            return files.stream()
                    .filter(f -> Boolean.TRUE.equals(f.getEnabled()))
                    .limit(6)
                    .map(f -> {
                        ContextProvenance provenance = ContextProvenance.of(
                                ContextSourceType.WORKSPACE_MEMORY,
                                f.getFilename(),
                                600);
                        String content = f.getContent();
                        if (content == null) content = "";
                        // Budget-aware truncation hint
                        if (content.length() > 1200) {
                            content = content.substring(0, 1200) + "\n... [truncated for context budget]";
                        }
                        return ContextLifecycleContract.ContextBlock.of(
                                "[" + f.getFilename() + "]\n" + content,
                                provenance, ContextBudgetClass.MEMORY_BLOCK);
                    })
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
