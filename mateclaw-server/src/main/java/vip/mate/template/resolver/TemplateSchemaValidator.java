package vip.mate.template.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.template.contract.TemplateApplicationContract;
import vip.mate.template.contract.TemplateFieldClassification;
import vip.mate.template.contract.TemplateSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * WP-6 validator that checks whether a built-in template adheres to the current
 * {@link vip.mate.template.contract.TemplateSection} contract.
 *
 * <p>Validation rules (first-pass):
 * <ul>
 *   <li>Unknown fields are noted but do not fail validation (forward compatibility).</li>
 *   <li>Fields that are known but classified as {@code IDENTITY_METADATA} with no consumers
 *       are flagged as potentially misplaced.</li>
 *   <li>Recommended fields for a runtime contract template:
 *       {@code runtime}, {@code defaultWorkspacePolicy}, {@code contextSources}.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see TemplateMetadataResolver
 */
@Component
@RequiredArgsConstructor
public class TemplateSchemaValidator {

    private final TemplateMetadataResolver metadataResolver;

    private static final Set<String> RECOMMENDED_RUNTIME_FIELDS = Set.of(
            "runtime", "defaultWorkspacePolicy", "contextSources"
    );

    /**
     * Validate a template against the current section contract.
     *
     * @param templateId the built-in template id
     * @return validation result with notes, warnings, and recommended field gaps
     */
    public TemplateApplicationContract.ValidationResult validate(String templateId) {
        List<TemplateFieldClassification> fields = metadataResolver.classifyTemplateFields(templateId);
        if (fields.isEmpty()) {
            return new TemplateApplicationContract.ValidationResult(false,
                    List.of("template not found: " + templateId), List.of(), List.of());
        }

        List<String> unknownFields = new ArrayList<>();
        List<String> misplacedFields = new ArrayList<>();
        List<String> missingRecommended = new ArrayList<>();

        Set<String> presentFieldNames = new java.util.HashSet<>();
        for (TemplateFieldClassification f : fields) {
            presentFieldNames.add(f.fieldName());
            if (f.section() == TemplateSection.IDENTITY_METADATA && f.consumers().isEmpty()) {
                unknownFields.add(f.fieldName());
            }
        }

        for (String recommended : RECOMMENDED_RUNTIME_FIELDS) {
            if (!presentFieldNames.contains(recommended)) {
                missingRecommended.add(recommended);
            }
        }

        boolean valid = unknownFields.isEmpty() && misplacedFields.isEmpty();
        return new TemplateApplicationContract.ValidationResult(valid, unknownFields, misplacedFields, missingRecommended);
    }
}
