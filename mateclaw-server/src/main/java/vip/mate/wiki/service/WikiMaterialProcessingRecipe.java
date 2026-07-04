package vip.mate.wiki.service;

import java.util.List;

/**
 * Material-specific processing semantics layered on top of the generic Wiki pipeline.
 */
public record WikiMaterialProcessingRecipe(
        String materialType,
        String businessViewType,
        String canonicalEntityType,
        List<String> genericDimensions,
        List<String> businessSliceTypes,
        List<String> rulePackModules,
        List<String> relationSemantics,
        String processingGuidance
) {
}
