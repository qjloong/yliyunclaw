package vip.mate.tool.builtin;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import vip.mate.tool.document.HtmlExportService;
import vip.mate.tool.guard.WorkspacePathGuard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HtmlRenderTool {

    private final HtmlExportService exportService;

    @Tool(description = """
        Render a new .html file from Markdown text and return a download/preview URL.
        Use for creating NEW HTML documents: reports, printable pages, structured reading materials.

        Prefer this tool over write_file when the target output is HTML, especially for long content,
        because it avoids max_tokens truncation while emitting large inline HTML strings.

        If outputPath is omitted, the tool saves to ./output/<workspace-name>/<filename>.html under
        the current project/workspace when available, and returns a temporary backend download URL.
        """)
    public String renderHtml(
            @ToolParam(description = "Document content in Markdown format") String markdown,
            @ToolParam(description = "Output filename without extension, e.g. 'exam-report'") String filename,
            @ToolParam(description = "Optional HTML title", required = false) String title,
            @ToolParam(description = "Optional output path. If omitted, defaults to ./output/<workspace-name>/<filename>.html under the current project/workspace when available.", required = false) String outputPath,
            @Nullable ToolContext ctx) {
        if (markdown == null || markdown.isBlank()) {
            return errorJson("markdown is blank");
        }
        try {
            Path persistTarget = resolvePersistTarget(filename, outputPath, ctx);
            HtmlExportService.ExportedHtml exported = exportService.exportMarkdown(markdown, filename, title, persistTarget, ctx);
            return successJson(exported);
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (Exception e) {
            log.error("[HtmlRenderTool] renderHtml failed for {}: {}", filename, e.getMessage(), e);
            return errorJson("render failed — " + e.getMessage());
        }
    }

    @Tool(description = """
        Render a .html file from a markdown FILE on disk and return a download/preview URL.
        Use this instead of renderHtml when the markdown body is large (>5 KB), so the LLM does not
        need to repeat the full content as tool arguments.
        """)
    public String renderHtmlFromFile(
            @ToolParam(description = "Absolute or workspace-relative path to a markdown file") String filePath,
            @ToolParam(description = "Output filename without extension, e.g. 'exam-report'") String filename,
            @ToolParam(description = "Optional HTML title", required = false) String title,
            @ToolParam(description = "Optional output path. If omitted, defaults to ./output/<workspace-name>/<filename>.html under the current project/workspace when available.", required = false) String outputPath,
            @Nullable ToolContext ctx) {
        if (filePath == null || filePath.isBlank()) {
            return errorJson("filePath parameter is empty");
        }
        try {
            Path resolved = WorkspacePathGuard.validatePath(filePath, ctx);
            if (!Files.exists(resolved) || !Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
                return errorJson("file not found or unreadable: " + resolved);
            }
            String markdown = Files.readString(resolved, StandardCharsets.UTF_8);
            if (markdown.isBlank()) {
                return errorJson("markdown file is blank: " + resolved);
            }
            Path persistTarget = resolvePersistTarget(filename, outputPath, ctx);
            HtmlExportService.ExportedHtml exported = exportService.exportMarkdown(markdown, filename, title, persistTarget, ctx);
            return successJson(exported, Map.of("sourceFile", resolved.toString()));
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (Exception e) {
            log.error("[HtmlRenderTool] renderHtmlFromFile failed for {}: {}", filePath, e.getMessage(), e);
            return errorJson("render failed — " + e.getMessage());
        }
    }

    @Tool(description = """
        Render a .html file by concatenating MULTIPLE markdown files in order and return a download/preview URL.
        Use when a long report is split into chapters or sections across several markdown files.
        """)
    public String renderHtmlFromFiles(
            @ToolParam(description = "List of markdown file paths in render order") List<String> filePaths,
            @ToolParam(description = "Output filename without extension, e.g. 'exam-report'") String filename,
            @ToolParam(description = "Optional HTML title", required = false) String title,
            @ToolParam(description = "Optional output path. If omitted, defaults to ./output/<workspace-name>/<filename>.html under the current project/workspace when available.", required = false) String outputPath,
            @Nullable ToolContext ctx) {
        if (filePaths == null || filePaths.isEmpty()) {
            return errorJson("filePaths is empty");
        }
        try {
            StringBuilder combined = new StringBuilder();
            List<String> resolvedPaths = new ArrayList<>();
            for (int i = 0; i < filePaths.size(); i++) {
                String raw = filePaths.get(i);
                if (raw == null || raw.isBlank()) {
                    return errorJson("filePaths[" + i + "] is empty");
                }
                Path resolved = WorkspacePathGuard.validatePath(raw, ctx);
                if (!Files.exists(resolved) || !Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
                    return errorJson("filePaths[" + i + "] not found or unreadable: " + resolved);
                }
                String markdown = Files.readString(resolved, StandardCharsets.UTF_8);
                if (markdown.isBlank()) {
                    return errorJson("filePaths[" + i + "] is blank: " + resolved);
                }
                if (combined.length() > 0) combined.append("\n\n");
                combined.append(markdown);
                resolvedPaths.add(resolved.toString());
            }
            Path persistTarget = resolvePersistTarget(filename, outputPath, ctx);
            HtmlExportService.ExportedHtml exported = exportService.exportMarkdown(combined.toString(), filename, title, persistTarget, ctx);
            return successJson(exported, Map.of("sourceFiles", resolvedPaths));
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        } catch (Exception e) {
            log.error("[HtmlRenderTool] renderHtmlFromFiles failed for {}: {}", filename, e.getMessage(), e);
            return errorJson("render failed — " + e.getMessage());
        }
    }

    private Path resolvePersistTarget(String filename, String outputPath, @Nullable ToolContext ctx) {
        Path workingDir = WorkspacePathGuard.getWorkingDirectory(ctx);
        Path target = exportService.resolveOutputPath(workingDir, outputPath, filename);
        if (target == null) {
            return null;
        }
        return WorkspacePathGuard.validatePath(target.toString(), ctx);
    }

    private String successJson(HtmlExportService.ExportedHtml exported) {
        return successJson(exported, Map.of());
    }

    private String successJson(HtmlExportService.ExportedHtml exported, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filePath", exported.savedPath());
        payload.put("filename", exported.fileName());
        payload.put("mimeType", exported.mimeType());
        payload.put("apiUrl", exported.downloadUrl());
        payload.put("savedPath", exported.savedPath());
        payload.put("previewUrl", exported.downloadUrl() + "/inline");
        payload.put("error", false);
        payload.putAll(extra);
        return JSONUtil.toJsonPrettyStr(payload);
    }

    private String errorJson(String message) {
        return JSONUtil.toJsonPrettyStr(Map.of(
                "error", true,
                "message", message
        ));
    }
}
