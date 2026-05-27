package vip.mate.teacher.model;

import java.util.List;

/**
 * Active/default Teacher skill binding view.
 */
public record TeacherSkillBindingView(
        List<String> activeSkillIds,
        List<String> defaultSkillIds,
        List<TeacherSkillDefinition> activeSkills,
        boolean overridden
) {
}
