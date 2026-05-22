package vip.mate.memory.governance;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import vip.mate.memory.contract.MemoryOperation;
import vip.mate.memory.contract.MemorySurfaceType;
import vip.mate.memory.event.MemoryWriteEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemoryWriteProvenancePublisherTest {

    @Test
    void publishRequired_emitsEventWithStandardizedProvenance() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        MemoryWriteProvenancePublisher publisher = new MemoryWriteProvenancePublisher(
                eventPublisher,
                new MemoryGovernanceFilter());

        publisher.publishRequired(
                7L,
                "conv-42",
                MemorySurfaceType.UNIVERSAL_TOOL,
                MemoryOperation.WRITE,
                "MEMORY.md",
                "remember",
                "hello world",
                Map.of("writer", "UniversalMemoryTool", "source", "unit-test"));

        ArgumentCaptor<MemoryWriteEvent> captor = ArgumentCaptor.forClass(MemoryWriteEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        MemoryWriteEvent event = captor.getValue();
        assertEquals(7L, event.agentId());
        assertEquals("MEMORY.md", event.target());
        assertEquals("remember", event.action());
        assertEquals("hello world", event.content());
        assertNotNull(event.provenance());
        assertEquals(MemorySurfaceType.UNIVERSAL_TOOL, event.provenance().surfaceType());
        assertEquals(MemoryOperation.WRITE, event.provenance().operation());
        assertEquals("conv-42", event.provenance().conversationId());
        assertEquals("LONG_TERM_CANONICAL", event.provenance().metadata().get("targetClass"));
        assertEquals("UniversalMemoryTool", event.provenance().metadata().get("writer"));
        assertTrue(event.provenance().contentHash().length() >= 32);
    }
}