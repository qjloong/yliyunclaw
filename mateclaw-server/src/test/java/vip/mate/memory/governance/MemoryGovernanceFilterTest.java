package vip.mate.memory.governance;

import org.junit.jupiter.api.Test;
import vip.mate.memory.contract.MemoryGovernanceContract;
import vip.mate.memory.contract.MemoryOperation;
import vip.mate.memory.contract.MemorySurfaceType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryGovernanceFilterTest {

    private final MemoryGovernanceFilter filter = new MemoryGovernanceFilter();

    @Test
    void archiveAndFactMaintenanceAreAllowedToWrite() {
        assertTrue(filter.isAllowed(MemorySurfaceType.ARCHIVE_MAINTENANCE, MemoryOperation.WRITE, "memory/dreams/2026-05.md"));
        assertTrue(filter.isAllowed(MemorySurfaceType.FACT_MAINTENANCE, MemoryOperation.WRITE, "MEMORY.md"));
    }

    @Test
    void dreamsTargetsClassifyAsDailyNotes() {
        assertEquals(MemoryGovernanceContract.TargetClass.DAILY_NOTES,
                filter.classifyTarget("memory/dreams/2026-05.md"));
    }

    @Test
    void soulTargetsClassifyAsDerivedSummary() {
        assertEquals(MemoryGovernanceContract.TargetClass.DERIVED_SUMMARY,
                filter.classifyTarget("SOUL.md"));
    }
}