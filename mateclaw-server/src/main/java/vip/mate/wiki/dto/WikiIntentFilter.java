package vip.mate.wiki.dto;

import java.util.List;

/**
 * Intent-driven filter for wiki retrieval, used to boost pages / chunks
 * that match a specific business module or preferred material types.
 * <p>
 * When bound to a business-domain KB, the retriever uses this filter to
 * downgrade generic KB results and promote business-relevant materials.
 *
 * @param businessModule           e.g., classical_chinese, classic_reading, ancient_poetry
 * @param preferredMaterialTypes   ordered list of material type IDs to boost
 * @param requiredStructureFields  optional structure fields that must be present (not yet enforced)
 */
public record WikiIntentFilter(
        String businessModule,
        List<String> preferredMaterialTypes,
        List<String> requiredStructureFields
) {

    public static WikiIntentFilter ofBusinessModule(String businessModule) {
        return new WikiIntentFilter(businessModule, null, null);
    }

    public boolean hasFilter() {
        return businessModule != null && !businessModule.isBlank()
                || preferredMaterialTypes != null && !preferredMaterialTypes.isEmpty();
    }
}
