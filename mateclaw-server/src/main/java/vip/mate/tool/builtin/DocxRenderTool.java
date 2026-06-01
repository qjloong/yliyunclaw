package vip.mate.tool.builtin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import vip.mate.tool.document.DocxExportService;
import vip.mate.tool.guard.WorkspacePathGuard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Render a brand-new .docx from Markdown without ever forking a process.
 *
 * <p>The previous path forwarded these requests to {@code skills/docx} which
 * runs {@code npm install docx} on first use (3-5 minutes). For "create new
 * document" intents that subprocess is wholly unnecessary; this tool produces
 * the bytes in the JVM, saves the file to disk, and returns a signed markdown
 * link the user can click to download/preview via backend stream endpoint.
 *
 * <p>The skill workflow is still authoritative for editing existing .docx,
 * tracked changes, and other XML-level operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocxRenderTool {

    private final DocxExportService exportService;

    @Tool(description = """
        Render a new .docx file from Markdown text and return a one-time download URL.
        Use for creating NEW documents: reports, memos, contracts, letters, resumes.
        Supports: headings (# ## ###), bold (**text**), bullet lists (- item),
                  numbered lists (1. item), tables (| col | col |), plain paragraphs,
                  images (![alt](path/to/file.png|jpg|gif|bmp|svg)) — SVG is rasterized
                  to PNG; image lines must contain only the image syntax.

        For markdown bodies larger than ~5 KB, prefer renderDocxFromFile (read from
        disk) — passing huge markdown as a tool argument burns LLM tokens needlessly.

        If outputPath is omitted, the tool will save the generated file to
        ./output/<workspace-name>/<filename>.docx under the current project/workspace when a working
        directory is available, and also return a temporary download link.

        Do NOT use for:
        - Editing an existing .docx file (use run_skill_script with unpack/edit/pack)
        - Adding tracked changes or comments (use run_skill_script)
        - GB/T 9704 official documents (use writeGongwen tool, BmacClaw only)

        Returns a markdown link the user can click to download the file.
        The link is valid for the configured generated-file retention period.
        """)
    public String renderDocx(
            @ToolParam(description = "Document content in Markdown format")
            String markdown,
            @ToolParam(description = "Output filename without extension, e.g. 'monthly-report'")
            String filename,
            @ToolParam(description = "Page size: A4 or LETTER (default: A4)", required = false)
            String pageSize,
            @ToolParam(description = "Optional output path. If omitted, defaults to ./output/<workspace-name>/<filename>.docx under the current project/workspace when available.", required = false)
            String outputPath,
            @Nullable ToolContext ctx) {

        if (markdown == null || markdown.isBlank()) {
            return "错误：markdown 参数为空，无法生成文档。";
        }

        try {
            long t0 = System.currentTimeMillis();
            Path persistTarget = resolvePersistTarget(filename, outputPath, ctx);
            DocxExportService.ExportedDocx exported = exportService.exportMarkdown(markdown, filename, pageSize, persistTarget, ctx);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("[DocxRender] generated {} ({}ms, savedPath={})",
                    exported.fileName(), elapsed, exported.savedPath());
            return formatExportResult(exported, true);
        } catch (IllegalArgumentException e) {
            return "导出失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("[DocxRender] render failed for {}: {}", filename, e.getMessage(), e);
            return "渲染失败：" + e.getMessage();
        }
    }

    /**
     * File-based renderer — reads markdown from disk instead of taking it as a
     * tool argument. Bypasses the LLM token-cost cliff: a 80 KB markdown body
     * would otherwise be streamed through the chat completion as part of
     * {@code renderDocx.markdown} args (≈ 20 K tokens, several minutes of
     * generation just to repeat back content the LLM already wrote to disk).
     * <p>
     * Workflow: agent uses {@code write_file} / {@code edit_file} to assemble
     * the markdown locally → calls this tool with the file path → docx is
     * rendered from disk in one IO call. Token cost ≈ 50 (just the path).
     */
    @Tool(description = """
        Render a .docx file from a markdown FILE on disk and return a one-time download URL.
        Use this instead of `renderDocx` when the markdown body is large (>5 KB) — the
        LLM does not need to repeat its own previous output as a tool argument.

        Typical workflow:
          1. write_file(path="report.md", content="# Report\\n...")  // assemble markdown
          2. renderDocxFromFile(filePath="report.md", filename="monthly-report")
          3. return the download link to the user

        The markdown file is read with UTF-8. Path resolution honors the workspace
        boundary (same rules as read_file / write_file).

        If outputPath is omitted, the generated file is also saved to
        ./output/<workspace-name>/<filename>.docx under the current project/workspace when available.

        Same supported markdown subset as renderDocx (headings, bold, lists, tables,
        images). Image references ![alt](path) are rendered when path resolves to a
        readable file in the workspace. SVG sources are rasterized to PNG via Batik;
        PNG/JPG/GIF/BMP are embedded directly.
        """)
    public String renderDocxFromFile(
            @ToolParam(description = "Absolute or workspace-relative path to a markdown file")
            String filePath,
            @ToolParam(description = "Output filename without extension, e.g. 'monthly-report'")
            String filename,
            @ToolParam(description = "Page size: A4 or LETTER (default: A4)", required = false)
            String pageSize,
            @ToolParam(description = "Optional output path. If omitted, defaults to ./output/<workspace-name>/<filename>.docx under the current project/workspace when available.", required = false)
            String outputPath,
            @Nullable ToolContext ctx) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath parameter is empty.";
        }

        Path resolved;
        try {
            resolved = WorkspacePathGuard.validatePath(filePath, ctx);
        } catch (Exception e) {
            return "Error: path validation failed — " + e.getMessage();
        }
        if (!Files.exists(resolved)) {
            return "Error: file not found at " + resolved;
        }
        if (!Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
            return "Error: path is not a readable regular file " + resolved;
        }

        String markdown;
        long mdBytes;
        try {
            mdBytes = Files.size(resolved);
            markdown = Files.readString(resolved, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[DocxRender] read markdown failed for {}: {}", resolved, e.getMessage(), e);
            return "Error: failed to read markdown — " + e.getMessage();
        }
        if (markdown.isBlank()) {
            return "Error: markdown file is empty " + resolved;
        }

        try {
            long t0 = System.currentTimeMillis();
            Path persistTarget = resolvePersistTarget(filename, outputPath, ctx);
            DocxExportService.ExportedDocx exported = exportService.exportMarkdown(markdown, filename, pageSize, persistTarget, ctx);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("[DocxRender] generated {} ({} bytes md, {}ms, savedPath={})",
                exported.fileName(), mdBytes, elapsed, exported.savedPath());
            return formatExportResult(exported, false);
        } catch (IllegalArgumentException e) {
            return "Error: export failed — " + e.getMessage();
        } catch (Exception e) {
            log.error("[DocxRender] render failed for {} (source: {}): {}",
                filename, resolved, e.getMessage(), e);
            return "Render failed: " + e.getMessage();
        }
    }

    /**
     * Multi-file renderer — read several markdown files in order and concatenate
     * them into one docx. Lets the agent split a long report into chapters
     * (cover.md, intro.md, ch1.md, ...) and render the whole thing in one call,
     * so a 30-page deliverable does not need to live in a single source file.
     * <p>
     * Files are joined with a blank line so heading hierarchy and paragraph
     * structure carry over cleanly; no extra separator markup is injected.
     * Empty / missing files abort the render with a clear error so the agent
     * can fix its file list before retrying.
     */
    @Tool(description = """
        Render a .docx by concatenating MULTIPLE markdown files in order and return a
        download URL. Use when a report is split into chapters / sections, or when the
        agent assembled the document piece by piece (cover, table of contents, body,
        appendix) across several files.

        Typical workflow:
          1. write_file(path="cover.md",  content="# Title\\n...")
          2. write_file(path="ch1.md",    content="## Chapter 1\\n...")
          3. write_file(path="ch2.md",    content="## Chapter 2\\n...")
          4. renderDocxFromFiles(filePaths=["cover.md","ch1.md","ch2.md"],
                                 filename="quarterly-report")

        Files are read with UTF-8, joined with one blank line between them, and
        rendered with the same markdown subset as renderDocx (headings, bold,
        lists, tables). All paths must pass the workspace boundary check.

        If outputPath is omitted, the generated file is also saved to
        ./output/<workspace-name>/<filename>.docx under the current project/workspace when available.
        """)
    public String renderDocxFromFiles(
            @ToolParam(description = "List of markdown file paths in render order")
            List<String> filePaths,
            @ToolParam(description = "Output filename without extension, e.g. 'quarterly-report'")
            String filename,
            @ToolParam(description = "Page size: A4 or LETTER (default: A4)", required = false)
            String pageSize,
            @ToolParam(description = "Optional output path. If omitted, defaults to ./output/<workspace-name>/<filename>.docx under the current project/workspace when available.", required = false)
            String outputPath,
            @Nullable ToolContext ctx) {

        if (filePaths == null || filePaths.isEmpty()) {
            return "Error: filePaths is empty.";
        }

        StringBuilder combined = new StringBuilder();
        long totalBytes = 0;
        List<String> resolvedPaths = new ArrayList<>();
        for (int idx = 0; idx < filePaths.size(); idx++) {
            String raw = filePaths.get(idx);
            if (raw == null || raw.isBlank()) {
                return "Error: filePaths[" + idx + "] is empty.";
            }
            Path resolved;
            try {
                resolved = WorkspacePathGuard.validatePath(raw, ctx);
            } catch (Exception e) {
                return "Error: filePaths[" + idx + "] validation failed — " + e.getMessage();
            }
            if (!Files.exists(resolved)) {
                return "Error: filePaths[" + idx + "] not found at " + resolved;
            }
            if (!Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
                return "Error: filePaths[" + idx + "] is not a readable regular file " + resolved;
            }
            String content;
            try {
                totalBytes += Files.size(resolved);
                content = Files.readString(resolved, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.error("[DocxRender] read failed for {}: {}", resolved, e.getMessage(), e);
                return "Error: read failed for " + resolved + " — " + e.getMessage();
            }
            if (content.isBlank()) {
                return "Error: filePaths[" + idx + "] is blank " + resolved;
            }
            if (combined.length() > 0) combined.append("\n\n");
            combined.append(content);
            resolvedPaths.add(resolved.toString());
        }

        try {
            long t0 = System.currentTimeMillis();
            Path persistTarget = resolvePersistTarget(filename, outputPath, ctx);
            DocxExportService.ExportedDocx exported = exportService.exportMarkdown(combined.toString(), filename, pageSize, persistTarget, ctx);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("[DocxRender] generated {} ({} files / {} bytes md, {}ms, savedPath={})",
                    exported.fileName(), resolvedPaths.size(), totalBytes, elapsed, exported.savedPath());
            return formatExportResult(exported, false);
        } catch (IllegalArgumentException e) {
            return "Error: export failed — " + e.getMessage();
        } catch (Exception e) {
            log.error("[DocxRender] render failed for {} (sources: {}): {}",
                    filename, resolvedPaths, e.getMessage(), e);
            return "Render failed: " + e.getMessage();
        }
    }

    private Path resolvePersistTarget(String filename, String outputPath, @Nullable ToolContext ctx) {
        if (outputPath == null || outputPath.isBlank()) {
            return null;
        }
        Path workingDir = WorkspacePathGuard.getWorkingDirectory(ctx);
        Path target = exportService.resolveOutputPath(workingDir, outputPath, filename);
        if (target == null) {
            return null;
        }
        return WorkspacePathGuard.validatePath(target.toString(), ctx);
    }

    private String formatExportResult(DocxExportService.ExportedDocx exported, boolean chinese) {
        StringBuilder sb = new StringBuilder();
        if (chinese) {
            if (exported.savedPath() != null && !exported.savedPath().isBlank()) {
                sb.append("文档已保存到：").append(exported.savedPath()).append("\n");
            }
            sb.append("文档已生成：[").append(exported.fileName()).append("](")
                    .append(exported.downloadUrl()).append(")（链接 10 分钟内有效）。\n")
                    .append("重要：回答用户时**必须**使用上述相对路径 `")
                    .append(exported.downloadUrl())
                    .append("`，**不要**添加任何 https://、http:// 域名前缀，前端会自动拼接当前主机。");
        } else {
            if (exported.savedPath() != null && !exported.savedPath().isBlank()) {
                sb.append("Document saved to: ").append(exported.savedPath()).append("\n");
            }
            sb.append("Document generated: [").append(exported.fileName()).append("](")
                    .append(exported.downloadUrl()).append(").\n")
                    .append("IMPORTANT: when replying to the user you **must** use the relative path `")
                    .append(exported.downloadUrl())
                    .append("` verbatim. Do **not** prepend any https://, http:// or domain — the frontend will resolve the current host automatically.");
        }
        return sb.toString();
    }
}
