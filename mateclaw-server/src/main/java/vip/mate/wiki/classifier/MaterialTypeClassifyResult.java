package vip.mate.wiki.classifier;

/**
 * Result of automatic material type classification and structure extraction.
 *
 * @param materialType      classified material type (e.g., textbook_latest, classic_manuscript)
 * @param confidence        HIGH / MEDIUM / LOW
 * @param materialMetadataJson extracted structure fields as normalized JSON string
 * @param reason            human-readable classification reason
 */
public record MaterialTypeClassifyResult(
        String materialType,
        String confidence,
        String materialMetadataJson,
        String reason
) {
}
