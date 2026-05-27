package vip.mate.teacher.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.teacher.model.TeacherImprovementDraft;
import vip.mate.teacher.service.TeacherImprovementDraftService;

import java.util.List;

@Tag(name = "Teacher Improvements")
@RestController
@RequestMapping("/api/v1/teacher/improvements")
@RequiredArgsConstructor
public class TeacherImprovementController {

    private final TeacherImprovementDraftService draftService;

    @Operation(summary = "List Teacher improvement drafts")
    @GetMapping
    public R<List<TeacherImprovementDraft>> list() {
        return R.ok(draftService.list());
    }

    @Operation(summary = "Create a Teacher improvement draft from a Harness run")
    @PostMapping
    public R<TeacherImprovementDraft> create(@RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
                                             @RequestBody CreateDraftRequest request) {
        return R.ok(draftService.createFromHarnessRun(
                request != null ? request.getHarnessRunId() : null,
                workspaceId,
                request != null ? request.getNote() : null
        ));
    }

    @Operation(summary = "Accept a Teacher improvement draft")
    @PostMapping("/{id}/accept")
    public R<TeacherImprovementDraft> accept(@PathVariable String id,
                                             @RequestHeader(value = "X-Workspace-Id", required = false) Long workspaceId,
                                             @RequestBody ReviewDraftRequest request) {
        return R.ok(draftService.accept(
                id,
                request != null ? request.getNote() : null,
                request != null && Boolean.TRUE.equals(request.getPublish()),
                request != null ? request.getScope() : "workspace",
                workspaceId
        ));
    }

    @Operation(summary = "Reject a Teacher improvement draft")
    @PostMapping("/{id}/reject")
    public R<TeacherImprovementDraft> reject(@PathVariable String id,
                                             @RequestBody ReviewDraftRequest request) {
        return R.ok(draftService.reject(id, request != null ? request.getNote() : null));
    }

    @Data
    public static class CreateDraftRequest {
        private String harnessRunId;
        private String note;
    }

    @Data
    public static class ReviewDraftRequest {
        private String note;
        private Boolean publish;
        private String scope;
    }
}
