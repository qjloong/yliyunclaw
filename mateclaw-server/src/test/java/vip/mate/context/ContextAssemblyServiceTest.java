package vip.mate.context;

import org.junit.jupiter.api.Test;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.context.contract.ContextBudgetClass;
import vip.mate.context.contract.ContextLifecycleContract;
import vip.mate.context.contract.ContextProvenance;
import vip.mate.context.contract.ContextSourceType;
import vip.mate.context.provider.ContextSourceProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WP-4 regression tests for {@link ContextAssemblyService}.
 */
class ContextAssemblyServiceTest {

    @Test
    void assembleContext_respectsProviderPriorityInsteadOfEnumOrdinal() {
        ContextSourceProvider lowPriorityToolResult = provider(
                ContextSourceType.TOOL_RESULT,
                50,
                "tool-result");
        ContextSourceProvider highPriorityDirective = provider(
                ContextSourceType.DIRECTIVE,
                0,
                "directive");

        ContextAssemblyService service = new ContextAssemblyService(List.of(lowPriorityToolResult, highPriorityDirective));

        List<ContextLifecycleContract.ContextBlock> blocks = service.assembleContext(
                1L,
                "conv-1",
                "explain",
                ChatOrigin.web("conv-1", "tester", 99L, "D:/workspace"));

        assertEquals(2, blocks.size());
        assertEquals(ContextSourceType.DIRECTIVE, blocks.get(0).provenance().sourceType());
        assertEquals("directive", blocks.get(0).content());
        assertEquals(ContextSourceType.TOOL_RESULT, blocks.get(1).provenance().sourceType());
        assertEquals("tool-result", blocks.get(1).content());
    }

    private static ContextSourceProvider provider(ContextSourceType sourceType, int priority, String content) {
        return new ContextSourceProvider() {
            @Override
            public ContextSourceType getSourceType() {
                return sourceType;
            }

            @Override
            public List<ContextLifecycleContract.ContextBlock> produceBlocks(Long agentId, String conversationId,
                                                                             String userQuery, Long workspaceId) {
                return List.of(ContextLifecycleContract.ContextBlock.of(
                        content,
                        ContextProvenance.of(sourceType, content, 64),
                        ContextBudgetClass.ROUTER_HINT));
            }

            @Override
            public int getPriority() {
                return priority;
            }
        };
    }
}