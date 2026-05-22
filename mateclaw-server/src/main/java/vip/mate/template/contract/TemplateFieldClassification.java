package vip.mate.template.contract;

import java.util.Collections;
import java.util.List;

/**
 * Classification of a single template field or field group, carrying its
 * {@link TemplateSection}, whether it affects runtime, whether it can tighten policy,
 * and whether it seeds workspace artifacts (WP-6).
 *
 * @param fieldName       the JSON field name or dotted path
 * @param section         the canonical section this field belongs to
 * @param affectsRuntime  true if the field influences agent or tool behavior at runtime
 * @param canTightenPolicy true if the field may carry safety authority (e.g., defaultWorkspacePolicy)
 * @param seedsWorkspace  true if the field causes files, KBs, or pages to be created
 * @param consumers       list of known consumer classes/files
 * @author MateClaw Team
 */
public record TemplateFieldClassification(String fieldName,
                                           TemplateSection section,
                                           boolean affectsRuntime,
                                           boolean canTightenPolicy,
                                           boolean seedsWorkspace,
                                           List<String> consumers) {

    public TemplateFieldClassification {
        consumers = consumers != null ? Collections.unmodifiableList(new java.util.ArrayList<>(consumers)) : Collections.emptyList();
    }
}
