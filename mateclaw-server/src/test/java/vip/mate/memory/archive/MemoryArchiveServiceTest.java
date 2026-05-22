package vip.mate.memory.archive;

import org.junit.jupiter.api.Test;
import vip.mate.memory.MemoryProperties;
import vip.mate.memory.contract.MemoryOperation;
import vip.mate.memory.contract.MemorySurfaceType;
import vip.mate.memory.governance.MemoryWriteProvenancePublisher;
import vip.mate.workspace.document.WorkspaceFileService;
import vip.mate.workspace.document.model.WorkspaceFileEntity;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryArchiveServiceTest {

    @Test
    void archiveOldDreams_publishesGovernedProvenanceForArchiveFilesAndIndex() {
        WorkspaceFileService workspaceFileService = mock(WorkspaceFileService.class);
        MemoryWriteProvenancePublisher provenancePublisher = mock(MemoryWriteProvenancePublisher.class);
        MemoryProperties properties = new MemoryProperties();
        properties.getDream().setArchiveEnabled(true);
        properties.getDream().setArchiveKeepDays(30);

        MemoryArchiveService service = new MemoryArchiveService(workspaceFileService, properties, provenancePublisher);

        WorkspaceFileEntity dreams = new WorkspaceFileEntity();
        String oldDate = LocalDate.now().minusDays(60).toString();
        String keepDate = LocalDate.now().toString();
        dreams.setContent("# Dreaming 整合日记\n\n"
                + "## " + oldDate + " 10:00 Dreaming\nold section\n\n"
                + "## " + keepDate + " 10:00 Dreaming\nkeep section\n");

        when(workspaceFileService.getFile(1L, "DREAMS.md")).thenReturn(dreams);
        when(workspaceFileService.getFile(eq(1L), eq("memory/dreams/" + oldDate.substring(0, 7) + ".md")))
                .thenReturn(null);

        service.archiveOldDreams(1L);

        verify(workspaceFileService).saveFile(eq(1L), eq("memory/dreams/" + oldDate.substring(0, 7) + ".md"), any(String.class));
        verify(workspaceFileService).saveFile(eq(1L), eq("DREAMS.md"), any(String.class));
        verify(provenancePublisher).publishRequired(eq(1L), eq(null),
                eq(MemorySurfaceType.ARCHIVE_MAINTENANCE), eq(MemoryOperation.WRITE),
                eq("memory/dreams/" + oldDate.substring(0, 7) + ".md"), eq("archive-dreams-monthly"), any(String.class), any());
        verify(provenancePublisher).publishRequired(eq(1L), eq(null),
                eq(MemorySurfaceType.ARCHIVE_MAINTENANCE), eq(MemoryOperation.WRITE),
                eq("DREAMS.md"), eq("archive-dreams-index"), any(String.class), any());
    }
}