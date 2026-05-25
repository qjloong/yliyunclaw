package vip.mate.memory.contract;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Provenance metadata for a durable-memory write, addressing WP-0 ambiguity
 * {@code A10} (provenance unevenness across write paths).
 *
 * <p>Every surface that writes canonical memory should ideally produce one
 * {@code MemoryWriteProvenance} so that later audit and governance logic can
 * answer "who wrote this memory, when, and why?"
 *
 * @param surfaceType   the type of surface that performed the write
 * @param operation     the operation performed
 * @param agentId       the agent that owns the memory (may be null for workspace-global)
 * @param conversationId the conversation that triggered the write (may be null)
 * @param writtenAt     when the write occurred
 * @param contentHash   a hash or fingerprint of the written content for idempotency checks
 * @param metadata      surface-specific metadata (e.g., {"provider":"builtin", "file":"MEMORY.md"})
 * @author MateClaw Team
 */
public record MemoryWriteProvenance(MemorySurfaceType surfaceType,
                                     MemoryOperation operation,
                                     Long agentId,
                                     String conversationId,
                                     Instant writtenAt,
                                     String contentHash,
                                     Map<String, String> metadata) {

    public MemoryWriteProvenance {
        metadata = metadata != null ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(metadata)) : Collections.emptyMap();
        if (writtenAt == null) writtenAt = Instant.now();
    }

    public static MemoryWriteProvenance of(MemorySurfaceType surfaceType, MemoryOperation operation, Long agentId) {
        return new MemoryWriteProvenance(surfaceType, operation, agentId, null, Instant.now(), null, Collections.emptyMap());
    }
}
