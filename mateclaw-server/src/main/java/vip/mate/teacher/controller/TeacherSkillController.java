package vip.mate.teacher.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;
import vip.mate.system.service.SystemSettingService;
import vip.mate.teacher.model.TeacherSkillBindingView;
import vip.mate.teacher.model.TeacherSkillDefinition;
import vip.mate.teacher.service.TeacherSkillDefinitionService;

import java.util.List;

/**
 * Teacher skill definition read API.
 */
@Tag(name = "Teacher Skills")
@RestController
@RequestMapping("/api/v1/teacher/skills")
@RequiredArgsConstructor
public class TeacherSkillController {

    private final ObjectMapper objectMapper;
    private final SystemSettingService systemSettingService;

    @PostConstruct
    public void loadBindingOverride() {
        String stored = systemSettingService.getString(TeacherSkillDefinitionService.DEFAULT_BINDING_SETTING_KEY, "");
        if (stored == null || stored.isBlank()) {
            return;
        }
        try {
            List<String> ids = objectMapper.readValue(stored, new TypeReference<List<String>>() {});
            TeacherSkillDefinitionService.setActiveBindings(ids);
        } catch (Exception ignored) {
            // Invalid override should not block startup.
        }
    }

    @Operation(summary = "List built-in Teacher skill definitions")
    @GetMapping
    public R<List<TeacherSkillDefinition>> list() {
        return R.ok(TeacherSkillDefinitionService.listBuiltInSkills());
    }

    @Operation(summary = "Get one built-in Teacher skill definition")
    @GetMapping("/{id}")
    public R<TeacherSkillDefinition> get(@PathVariable String id) {
        TeacherSkillDefinition skill = TeacherSkillDefinitionService.getBuiltInSkill(id);
        if (skill == null) {
            throw new MateClawException(404, "Teacher skill not found: " + id);
        }
        return R.ok(skill);
    }

    @Operation(summary = "Get active Teacher skill bindings")
    @GetMapping("/bindings")
    public R<TeacherSkillBindingView> bindings() {
        return R.ok(bindingView());
    }

    @Operation(summary = "Save active Teacher skill bindings")
    @PutMapping("/bindings")
    public R<TeacherSkillBindingView> saveBindings(@RequestBody BindingRequest request) {
        try {
            List<String> ids = TeacherSkillDefinitionService.validateBindings(request != null ? request.getSkillIds() : null);
            TeacherSkillDefinitionService.setActiveBindings(ids);
            systemSettingService.saveString(
                    TeacherSkillDefinitionService.DEFAULT_BINDING_SETTING_KEY,
                    objectMapper.writeValueAsString(ids),
                    "Teacher default skill bindings"
            );
            return R.ok(bindingView());
        } catch (MateClawException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new MateClawException(400, e.getMessage());
        } catch (Exception e) {
            throw new MateClawException(500, "Failed to save Teacher skill bindings: " + e.getMessage());
        }
    }

    @Operation(summary = "Reset active Teacher skill bindings")
    @DeleteMapping("/bindings")
    public R<TeacherSkillBindingView> resetBindings() {
        TeacherSkillDefinitionService.resetActiveBindings();
        systemSettingService.saveString(
                TeacherSkillDefinitionService.DEFAULT_BINDING_SETTING_KEY,
                "",
                "Teacher default skill bindings reset"
        );
        return R.ok(bindingView());
    }

    private TeacherSkillBindingView bindingView() {
        return new TeacherSkillBindingView(
                TeacherSkillDefinitionService.activeBindings(),
                TeacherSkillDefinitionService.defaultBindings(),
                TeacherSkillDefinitionService.activeSkillDefinitions(),
                TeacherSkillDefinitionService.hasBindingOverride()
        );
    }

    @Data
    public static class BindingRequest {
        private List<String> skillIds;
    }
}
