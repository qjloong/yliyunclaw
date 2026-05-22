package vip.mate.memory.governance;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import vip.mate.memory.contract.MemoryOperation;
import vip.mate.memory.contract.MemorySurfaceType;
import vip.mate.memory.contract.MemoryWriteProvenance;
import vip.mate.memory.event.MemoryWriteEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WP-3 publisher that closes the provenance loop for governed memory writes.
 *
 * <p>It centralizes three things that were previously inconsistent across writers:
 * <ul>
 *   <li>governance check for the target surface/operation</li>
 *   <li>standardized {@link MemoryWriteProvenance} creation</li>
 *   <li>{@link MemoryWriteEvent} emission with provenance attached</li>
 * </ul>
 *
 * <p>This keeps the event payload backward compatible while making provenance
 * mandatory wherever {@link MemoryGovernanceFilter#requiresProvenance(MemorySurfaceType, MemoryOperation)}
 * says it is required.
 */
@Component
public class MemoryWriteProvenancePublisher {

    private static final int EVENT_CONTENT_LIMIT = 2000;

    private final ApplicationEventPublisher eventPublisher;
    private final MemoryGovernanceFilter governanceFilter;

    public MemoryWriteProvenancePublisher(ApplicationEventPublisher eventPublisher,
                                          MemoryGovernanceFilter governanceFilter) {
        this.eventPublisher = eventPublisher;
        this.governanceFilter = governanceFilter;
    }

    public MemoryWriteProvenance publishRequired(Long agentId,
                                                 String conversationId,
                                                 MemorySurfaceType surfaceType,
                                                 MemoryOperation operation,
                                                 String target,
                                                 String action,
                                                 String writtenContent,
                                                 Map<String, String> metadata) {
        if (!governanceFilter.isAllowed(surfaceType, operation, target)) {
            throw new IllegalStateException("Memory write not allowed: surface=" + surfaceType
                    + ", operation=" + operation + ", target=" + target);
        }

        if (!governanceFilter.requiresProvenance(surfaceType, operation)) {
            return null;
        }

        Map<String, String> normalizedMetadata = new LinkedHashMap<>();
        if (metadata != null) {
            normalizedMetadata.putAll(metadata);
        }
        if (action != null && !action.isBlank()) {
            normalizedMetadata.putIfAbsent("action", action);
        }
        if (target != null && !target.isBlank()) {
            normalizedMetadata.putIfAbsent("targetClass", governanceFilter.classifyTarget(target).name());
        }

        MemoryWriteProvenance provenance = new MemoryWriteProvenance(
                surfaceType,
                operation,
                agentId,
                conversationId,
                null,
                sha256Hex(writtenContent),
                normalizedMetadata);

        eventPublisher.publishEvent(new MemoryWriteEvent(
                agentId,
                target,
                action,
                truncateForEvent(writtenContent),
                provenance));
        return provenance;
    }

    private static String truncateForEvent(String content) {
        if (content == null) {
            return null;
        }
        if (content.length() <= EVENT_CONTENT_LIMIT) {
            return content;
        }
        return content.substring(0, EVENT_CONTENT_LIMIT) + "\n...[truncated]";
    }

    private static String sha256Hex(String content) {
        if (content == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}