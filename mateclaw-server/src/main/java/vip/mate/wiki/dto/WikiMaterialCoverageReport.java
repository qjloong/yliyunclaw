package vip.mate.wiki.dto;

import java.util.List;

/**
 * Material coverage report for a single business module.
 *
 * @param businessModule      the business module identifier
 * @param pageCount           number of pages tagged with this module
 * @param rawMaterialCount    number of raw materials contributing to this module
 * @param coverageScore       0.0 ~ 1.0 (1.0 = well-covered, 0.0 = missing)
 * @param missingIndicators   human-readable hints about what's missing
 */
public record WikiMaterialCoverageReport(
        String businessModule,
        int pageCount,
        int rawMaterialCount,
        double coverageScore,
        List<String> missingIndicators
) {
}
