package vip.mate.agent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vip.mate.agent.model.AgentEntity;
import vip.mate.agent.model.TemplateHealthDTO;
import vip.mate.agent.model.TemplateDTO;
import vip.mate.audit.service.AuditEventService;
import vip.mate.agent.service.TemplateService;
import vip.mate.common.result.R;
import vip.mate.setting.contract.EffectiveSettingsContract;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;

import java.util.List;
import java.util.Map;

/**
 * Agent 模板接口
 *
 * @author MateClaw Team
 */
@Tag(name = "Agent Templates")
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final AuditEventService auditEventService;
    /** WP-1: settings introspection — exposes effective settings with source provenance. */
    private final EffectiveSettingsContract settingResolutionAggregator;

    @Operation(summary = "获取模板列表")
    @GetMapping
    public R<List<TemplateDTO>> list() {
        return R.ok(templateService.listTemplates());
    }

    @Operation(summary = "获取模板在当前工作区的就绪状态")
    @GetMapping("/health")
    @RequireWorkspaceRole("viewer")
    public R<List<TemplateHealthDTO>> health(@RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        return R.ok(templateService.listTemplateHealth(workspaceId != null ? workspaceId : 1L));
    }

    @Operation(summary = "应用模板创建Agent")
    @PostMapping("/{id}/apply")
    @RequireWorkspaceRole("admin")
    public R<AgentEntity> apply(@PathVariable String id,
                                @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
                                Authentication auth) {
        AgentEntity created = templateService.applyTemplate(id, workspaceId != null ? workspaceId : 1L, auth != null ? auth.getName() : null);
        auditEventService.record("CREATE", "AGENT", String.valueOf(created.getId()), created.getName(), null);
        return R.ok(created);
    }

    @Operation(summary = "同步模板默认资源（补缺失文件与知识库种子页）")
    @PostMapping("/{id}/sync-default-files")
    @RequireWorkspaceRole("admin")
    public R<java.util.Map<String, Integer>> syncDefaultFiles(@PathVariable String id,
                                                              @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        return R.ok(templateService.syncMissingDefaultFiles(id, workspaceId != null ? workspaceId : 1L));
    }

    /**
     * WP-1: Settings introspection debug endpoint.
     * Returns the full effective-settings map for a given scope with per-field source provenance,
     * answering "why is this setting X and not Y?".
     */
    @Operation(summary = "Settings introspection — effective settings with source provenance")
    @GetMapping("/settings/introspection")
    public R<Map<String, EffectiveSettingsContract.ResolutionResult<?>>> introspectSettings(
            @RequestParam(defaultValue = "global") String scope) {
        return R.ok(settingResolutionAggregator.resolveAll(scope));
    }

    /**
     * WP-1: enrich template health with WP-6 schema validation.
     */
    @Operation(summary = "Template health with schema validation (WP-1 + WP-6)")
    @GetMapping("/{id}/health")
    @RequireWorkspaceRole("viewer")
    public R<TemplateHealthDTO> templateHealth(@PathVariable String id,
                                               @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId) {
        return R.ok(templateService.enrichTemplateHealth(id, workspaceId != null ? workspaceId : 1L));
    }
}
