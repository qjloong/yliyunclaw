package vip.mate.memory.governance;

import org.springframework.stereotype.Component;
import vip.mate.memory.contract.MemoryGovernanceContract;
import vip.mate.memory.contract.MemoryOperation;
import vip.mate.memory.contract.MemorySurfaceType;

/**
 * WP-3 implementation of {@link MemoryGovernanceContract}.
 *
 * <p>This filter centralizes the implicit trust assumptions that currently exist
 * across memory services and tools. It makes the rules testable and adjustable
 * without changing the underlying storage mechanics.
 *
 * <p>Current rules (first-pass):
 * <ul>
 *   <li>Only {@code EMERGENCE_WRITER} and {@code SUMMARIZATION_WRITER} may perform
 *       {@code CONSOLIDATE}.</li>
 *   <li>{@code RECALL_SCORING} may only {@code READ} and {@code SCORE_RECALL};
 *       it may not {@code WRITE}, {@code SUMMARIZE}, or {@code DELETE}.</li>
 *   <li>{@code DIRECT_FILE_TOOL} and {@code UNIVERSAL_TOOL} may {@code READ} and
 *       {@code WRITE}, but writes must emit provenance.</li>
 *   <li>{@code LIFECYCLE_MEDIATOR} may only {@code READ}; it is not a memory authority.</li>
 *   <li>{@code BUILTIN_PROVIDER} may only {@code READ}.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see MemoryGovernanceContract
 */
@Component
public class MemoryGovernanceFilter implements MemoryGovernanceContract {

    @Override
    public boolean isAllowed(MemorySurfaceType surfaceType, MemoryOperation operation, String targetPath) {
        return switch (surfaceType) {
            case ORCHESTRATOR -> operation == MemoryOperation.READ;
            case BUILTIN_PROVIDER -> operation == MemoryOperation.READ;
            case LIFECYCLE_MEDIATOR -> operation == MemoryOperation.READ;
            case SUMMARIZATION_WRITER ->
                    operation == MemoryOperation.READ || operation == MemoryOperation.WRITE || operation == MemoryOperation.SUMMARIZE;
            case EMERGENCE_WRITER ->
                    operation == MemoryOperation.READ || operation == MemoryOperation.WRITE || operation == MemoryOperation.CONSOLIDATE;
            case DERIVED_SUMMARY_WRITER ->
                operation == MemoryOperation.READ || operation == MemoryOperation.WRITE;
            case RECALL_SCORING ->
                    operation == MemoryOperation.READ || operation == MemoryOperation.SCORE_RECALL;
            case DIRECT_FILE_TOOL, UNIVERSAL_TOOL ->
                    operation == MemoryOperation.READ || operation == MemoryOperation.WRITE;
                case FACT_MAINTENANCE, ARCHIVE_MAINTENANCE ->
                    operation == MemoryOperation.READ || operation == MemoryOperation.WRITE;
        };
    }

    @Override
    public boolean requiresProvenance(MemorySurfaceType surfaceType, MemoryOperation operation) {
        // Provenance is required for all WRITE, SUMMARIZE, CONSOLIDATE, and DELETE operations.
        if (operation == MemoryOperation.READ || operation == MemoryOperation.SCORE_RECALL) {
            return false;
        }
        // All mutating operations must emit provenance regardless of surface.
        return true;
    }

    @Override
    public TargetClass classifyTarget(String targetPath) {
        if (targetPath == null || targetPath.isBlank()) {
            return TargetClass.UNKNOWN;
        }
        String lower = targetPath.toLowerCase();
        if (lower.contains("soul")) {
            return TargetClass.DERIVED_SUMMARY;
        }
        if (lower.contains("daily") || lower.contains("note") || lower.contains("turn") || lower.contains("dream")) {
            return TargetClass.DAILY_NOTES;
        }
        if (lower.contains("memory") || lower.contains("lesson") || lower.contains("fact")
                || lower.contains("profile") || lower.contains("agents") || lower.contains("structured/")) {
            return TargetClass.LONG_TERM_CANONICAL;
        }
        if (lower.contains("metric") || lower.contains("recall") || lower.contains("score")) {
            return TargetClass.METRICS_ONLY;
        }
        return TargetClass.UNKNOWN;
    }
}
