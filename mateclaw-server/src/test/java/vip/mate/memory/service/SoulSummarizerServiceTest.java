package vip.mate.memory.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import vip.mate.agent.AgentGraphBuilder;
import vip.mate.llm.model.ModelConfigEntity;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.memory.MemoryProperties;
import vip.mate.memory.contract.MemoryOperation;
import vip.mate.memory.contract.MemorySurfaceType;
import vip.mate.memory.contract.MemoryWriteProvenance;
import vip.mate.memory.event.MemoryWriteEvent;
import vip.mate.memory.governance.MemoryWriteProvenancePublisher;
import vip.mate.workspace.document.WorkspaceFileService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoulSummarizerServiceTest {

    @Test
    void onMemoryWrite_ignoresSoulSelfWriteEventsToAvoidRecursion() {
        WorkspaceFileService workspaceFileService = mock(WorkspaceFileService.class);
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        AgentGraphBuilder agentGraphBuilder = mock(AgentGraphBuilder.class);
        MemoryWriteProvenancePublisher provenancePublisher = mock(MemoryWriteProvenancePublisher.class);
        MemoryProperties properties = new MemoryProperties();
        properties.setSoulUpdateInterval(1);

        SoulSummarizerService service = spy(new SoulSummarizerService(
                workspaceFileService,
                modelConfigService,
                agentGraphBuilder,
                properties,
                provenancePublisher));

        MemoryWriteEvent event = new MemoryWriteEvent(
                9L,
                "SOUL.md",
                "refresh-soul",
                "updated soul",
                new MemoryWriteProvenance(
                        MemorySurfaceType.DERIVED_SUMMARY_WRITER,
                        MemoryOperation.WRITE,
                        9L,
                        null,
                        null,
                        null,
                        Map.of("writer", "SoulSummarizerService")));

        service.onMemoryWrite(event);

        verify(service, never()).updateSoul(anyLong());
    }

    @Test
    void updateSoul_publishesGovernedProvenanceForSoulWrites() {
        WorkspaceFileService workspaceFileService = mock(WorkspaceFileService.class);
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        AgentGraphBuilder agentGraphBuilder = mock(AgentGraphBuilder.class);
        MemoryWriteProvenancePublisher provenancePublisher = mock(MemoryWriteProvenancePublisher.class);
        MemoryProperties properties = new MemoryProperties();
        SoulSummarizerService service = new SoulSummarizerService(
                workspaceFileService,
                modelConfigService,
                agentGraphBuilder,
                properties,
                provenancePublisher);

        ModelConfigEntity model = mock(ModelConfigEntity.class);
        ChatModel chatModel = mock(ChatModel.class);
        String generatedSoul = "This is a regenerated SOUL.md summary that is comfortably longer than fifty characters.";
        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage(generatedSoul), ChatGenerationMetadata.NULL)));

        when(modelConfigService.getDefaultModel()).thenReturn(model);
        when(agentGraphBuilder.buildRuntimeChatModel(model)).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        service.updateSoul(3L);

        verify(workspaceFileService).saveFile(3L, "SOUL.md", generatedSoul);
        verify(provenancePublisher).publishRequired(eq(3L), eq(null),
                eq(MemorySurfaceType.DERIVED_SUMMARY_WRITER), eq(MemoryOperation.WRITE),
                eq("SOUL.md"), eq("refresh-soul"), eq(generatedSoul),
                eq(Map.of("writer", "SoulSummarizerService")));
    }
}