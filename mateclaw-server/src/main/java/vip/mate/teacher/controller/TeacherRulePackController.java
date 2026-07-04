package vip.mate.teacher.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.exception.MateClawException;
import vip.mate.teacher.model.TeacherRulePack;
import vip.mate.teacher.model.TeacherRulePackView;
import vip.mate.teacher.service.TeacherRulePackService;
import vip.mate.system.service.SystemSettingService;

import java.util.List;

/**
 * Teacher rule pack API.
 *
 * <p>Supports viewing built-in rules and saving custom overrides.
 * Custom config takes precedence over built-in defaults.</p>
 */
@Tag(name = "Teacher Rule Packs")
@RestController
@RequestMapping("/api/v1/teacher/rule-packs")
@RequiredArgsConstructor
public class TeacherRulePackController {

    private final ObjectMapper objectMapper;
    private final SystemSettingService systemSettingService;

    @PostConstruct
    public void loadOverrides() {
        for (TeacherRulePack pack : TeacherRulePackService.listBuiltInRulePacks()) {
            String stored = systemSettingService.getString(settingKey(pack.id()), "");
            if (stored == null || stored.isBlank()) {
                continue;
            }
            try {
                TeacherRulePack override = objectMapper.readValue(stored, TeacherRulePack.class);
                TeacherRulePackService.putOverride(override);
            } catch (Exception ignored) {
                // Invalid override should not prevent application startup.
            }
        }
    }

    @Operation(summary = "List built-in Teacher rule packs")
    @GetMapping
    public R<List<TeacherRulePack>> list() {
        return R.ok(TeacherRulePackService.listBuiltInRulePacks());
    }

    @Operation(summary = "Get one built-in Teacher rule pack")
    @GetMapping("/{id}")
    public R<TeacherRulePack> get(@PathVariable String id) {
        TeacherRulePack pack = TeacherRulePackService.getBuiltInRulePack(id);
        if (pack == null) {
            throw new MateClawException(404, "Teacher rule pack not found: " + id);
        }
        return R.ok(pack);
    }

    @Operation(summary = "Get one Teacher rule pack with custom config state")
    @GetMapping("/{id}/view")
    public R<TeacherRulePackView> view(@PathVariable String id,
                                       @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        TeacherRulePack builtIn = TeacherRulePackService.builtInRulePack(id);
        if (builtIn == null) {
            throw new MateClawException(404, "Teacher rule pack not found: " + id);
        }
        loadWorkspaceOverrideIfPresent(id, workspaceId);
        boolean overridden = TeacherRulePackService.hasWorkspaceOverride(id, workspaceId)
                || TeacherRulePackService.hasOverride(id);
        return R.ok(new TeacherRulePackView(
                TeacherRulePackService.effectiveRulePack(id, workspaceId),
                overridden,
                builtIn
        ));
    }

    @Operation(summary = "Save Teacher rule pack override")
    @PutMapping("/{id}")
    public R<TeacherRulePackView> saveOverride(@PathVariable String id,
                                               @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
                                               @RequestParam(value = "scope", defaultValue = "workspace") String scope,
                                               @RequestBody TeacherRulePack pack) {
        assertWritableEndpoint();
        TeacherRulePack builtIn = TeacherRulePackService.builtInRulePack(id);
        if (builtIn == null) {
            throw new MateClawException(404, "Teacher rule pack not found: " + id);
        }
        if (pack == null || pack.id() == null || !id.equals(pack.id())) {
            throw new MateClawException(400, "Rule pack id mismatch");
        }
        validateRulePack(pack);
        try {
            String json = objectMapper.writeValueAsString(pack);
            if ("global".equalsIgnoreCase(scope) || workspaceId == null) {
                systemSettingService.saveRawValue(settingKey(id), json, "Teacher RulePack global override: " + id);
                TeacherRulePackService.putOverride(pack);
            } else {
                systemSettingService.saveRawValue(workspaceSettingKey(id, workspaceId), json,
                        "Teacher RulePack workspace override: " + workspaceId + "/" + id);
                TeacherRulePackService.putWorkspaceOverride(workspaceId, pack);
            }
            return view(id, workspaceId);
        } catch (MateClawException e) {
            throw e;
        } catch (Exception e) {
            throw new MateClawException(500, "Failed to save Teacher rule pack override: " + e.getMessage());
        }
    }

    @Operation(summary = "Clear Teacher rule pack override")
    @DeleteMapping("/{id}")
    public R<TeacherRulePackView> clearOverride(@PathVariable String id,
                                                @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
                                                @RequestParam(value = "scope", defaultValue = "workspace") String scope) {
        assertWritableEndpoint();
        if (TeacherRulePackService.builtInRulePack(id) == null) {
            throw new MateClawException(404, "Teacher rule pack not found: " + id);
        }
        if ("global".equalsIgnoreCase(scope) || workspaceId == null) {
            systemSettingService.saveRawValue(settingKey(id), "", "Teacher RulePack global override cleared: " + id);
            TeacherRulePackService.clearOverride(id);
        } else {
            systemSettingService.saveRawValue(workspaceSettingKey(id, workspaceId), "",
                    "Teacher RulePack workspace override cleared: " + workspaceId + "/" + id);
            TeacherRulePackService.clearWorkspaceOverride(id, workspaceId);
        }
        return view(id, workspaceId);
    }

    private static String settingKey(String id) {
        return TeacherRulePackService.OVERRIDE_SETTING_PREFIX + id;
    }

    private static String workspaceSettingKey(String id, Long workspaceId) {
        return TeacherRulePackService.WORKSPACE_OVERRIDE_SETTING_PREFIX + workspaceId + "." + id;
    }

    private void loadWorkspaceOverrideIfPresent(String id, Long workspaceId) {
        if (workspaceId == null || TeacherRulePackService.hasWorkspaceOverride(id, workspaceId)) {
            return;
        }
        String stored = systemSettingService.getString(workspaceSettingKey(id, workspaceId), "");
        if (stored == null || stored.isBlank()) {
            return;
        }
        try {
            TeacherRulePack override = objectMapper.readValue(stored, TeacherRulePack.class);
            TeacherRulePackService.putWorkspaceOverride(workspaceId, override);
        } catch (Exception ignored) {
            // Invalid workspace override should not block normal reads.
        }
    }

    private static void validateRulePack(TeacherRulePack pack) {
        if (pack.module() == null || pack.module().isBlank()
                || pack.stage() == null || pack.stage().isBlank()
                || pack.subject() == null || pack.subject().isBlank()
                || pack.name() == null || pack.name().isBlank()
                || pack.version() == null || pack.version().isBlank()) {
            throw new MateClawException(400, "Rule pack stage, subject, module, name and version are required");
        }
        if (pack.questionTypeRules() == null || pack.questionTypeRules().isEmpty()) {
            throw new MateClawException(400, "Rule pack must contain question type rules");
        }
        if (pack.hardRules() == null || pack.hardRules().isEmpty()) {
            throw new MateClawException(400, "Rule pack must contain hard rules");
        }
    }

    private static void assertWritableEndpoint() {
        // Actual role protection is configured in SecurityConfig. This method
        // keeps writable endpoints explicit in code.
    }
}
