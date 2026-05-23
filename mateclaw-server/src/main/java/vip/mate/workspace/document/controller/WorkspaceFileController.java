package vip.mate.workspace.document.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import vip.mate.common.result.R;
import vip.mate.workspace.document.WorkspaceFileService;
import vip.mate.workspace.document.model.WorkspaceFileEntity;

import java.util.List;

/**
 * 工作区文件管理接口
 *
 * @author MateClaw Team
 */
@Tag(name = "工作区文件管理")
@RestController
@RequestMapping("/api/v1/agents/{agentId}/workspace")
@RequiredArgsConstructor
public class WorkspaceFileController {

    private final WorkspaceFileService workspaceFileService;

    /**
     * 列出 Agent 的所有工作区文件（不含内容）
     */
    @Operation(summary = "列出工作区文件")
    @GetMapping("/files")
    public R<List<WorkspaceFileEntity>> listFiles(@PathVariable Long agentId) {
        return R.ok(workspaceFileService.listFiles(agentId));
    }

    /**
     * 读取单个文件内容（支持子目录，如 memory/2026-04-03.md）
     */
    @Operation(summary = "读取工作区文件")
    @GetMapping("/files/**")
    public R<WorkspaceFileEntity> getFile(@PathVariable Long agentId, HttpServletRequest request) {
        String filename = extractFilename(request);
        WorkspaceFileEntity file = workspaceFileService.getFile(agentId, filename);
        if (file == null) {
            return R.fail("文件不存在: " + filename);
        }
        return R.ok(file);
    }

    /**
     * 创建或更新文件（支持子目录）
     */
    @Operation(summary = "保存工作区文件")
    @PutMapping("/files/**")
    public R<WorkspaceFileEntity> saveFile(@PathVariable Long agentId,
                                           HttpServletRequest httpRequest,
                                           @RequestBody SaveFileRequest body) {
        String filename = extractFilename(httpRequest);
        return R.ok(workspaceFileService.saveFile(agentId, filename, body.getContent()));
    }

    /**
     * 删除文件（支持子目录）
     */
    @Operation(summary = "删除工作区文件")
    @DeleteMapping("/files/**")
    public R<Void> deleteFile(@PathVariable Long agentId, HttpServletRequest request) {
        String filename = extractFilename(request);
        workspaceFileService.deleteFile(agentId, filename);
        return R.ok();
    }

    /**
     * 从请求路径中提取 /files/ 之后的文件名部分（支持含 / 的子目录路径）
     */
    private String extractFilename(HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        int filesIdx = fullPath.indexOf("/workspace/files/");
        return fullPath.substring(filesIdx + "/workspace/files/".length());
    }

    /**
     * 获取启用的系统提示文件列表（有序）
     */
    @Operation(summary = "获取系统提示文件列表")
    @GetMapping("/prompt-files")
    public R<List<String>> getPromptFiles(@PathVariable Long agentId) {
        return R.ok(workspaceFileService.getPromptFiles(agentId));
    }

    /**
     * 设置启用的系统提示文件列表（有序）
     */
    @Operation(summary = "设置系统提示文件列表")
    @PutMapping("/prompt-files")
    public R<Void> setPromptFiles(@PathVariable Long agentId,
                                   @RequestBody PromptFilesRequest request) {
        workspaceFileService.setPromptFiles(agentId, request.getFiles());
        return R.ok();
    }

    @Data
    static class SaveFileRequest {
        private String content;
    }

    @Data
    static class PromptFilesRequest {
        private List<String> files;
    }
}
