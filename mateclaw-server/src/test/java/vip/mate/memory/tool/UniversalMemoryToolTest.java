package vip.mate.memory.tool;

import org.junit.jupiter.api.Test;
import vip.mate.memory.contract.MemoryOperation;
import vip.mate.memory.contract.MemorySurfaceType;
import vip.mate.memory.governance.MemoryGovernanceFilter;
import vip.mate.memory.governance.MemoryWriteProvenancePublisher;
import vip.mate.workspace.document.WorkspaceFileService;
import vip.mate.workspace.document.model.WorkspaceFileEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniversalMemoryToolTest {

    @Test
    void remember_publishesGovernedProvenanceAfterSaving() {
        WorkspaceFileService workspaceFileService = mock(WorkspaceFileService.class);
        MemoryGovernanceFilter governanceFilter = mock(MemoryGovernanceFilter.class);
        MemoryWriteProvenancePublisher provenancePublisher = mock(MemoryWriteProvenancePublisher.class);
        UniversalMemoryTool tool = new UniversalMemoryTool(workspaceFileService, governanceFilter, provenancePublisher);

        WorkspaceFileEntity existing = new WorkspaceFileEntity();
        existing.setContent("## Recent Lessons\n- old");
        when(workspaceFileService.getFile(1L, "MEMORY.md")).thenReturn(existing);
        when(governanceFilter.isAllowed(MemorySurfaceType.UNIVERSAL_TOOL, MemoryOperation.WRITE, "MEMORY.md"))
                .thenReturn(true);

        String result = tool.remember(1L, "new lesson", "unit-test");

        verify(workspaceFileService).saveFile(eq(1L), eq("MEMORY.md"), any(String.class));
        verify(provenancePublisher).publishRequired(eq(1L), eq(null),
                eq(MemorySurfaceType.UNIVERSAL_TOOL), eq(MemoryOperation.WRITE), eq("MEMORY.md"),
                eq("remember"), any(String.class), any(Map.class));
        assertTrue(result.contains("success"));
    }
}