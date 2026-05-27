package vip.mate.teacher.model;

import java.util.List;
import java.util.Map;

/**
 * Canonical structured payload for Teacher Agent exam-generation results.
 *
 * <p>The model may still emit Markdown as a fallback, but v2-capable turns
 * should prefer this shape so UI rendering, copy, export, and Harness scoring
 * do not depend on fragile heading parsing.</p>
 */
public record TeacherExamResultV2(
        String type,
        String module,
        Map<String, Object> paper,
        List<Map<String, Object>> questions,
        List<Map<String, Object>> answers,
        List<Map<String, Object>> scoringRubric,
        List<Map<String, Object>> sources,
        List<Map<String, Object>> internalReview,
        Map<String, Object> exportOptions
) {
    public static final String TYPE = "teacher_exam_result_v2";
}
