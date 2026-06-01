package vip.mate.tool.document;

import lombok.RequiredArgsConstructor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vip.mate.agent.context.ChatOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HtmlExportService {

    public static final String HTML_MIME = "text/html;charset=utf-8";
    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GeneratedDiskFileRegistry diskFileRegistry;

    private final Parser parser = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(List.of(TablesExtension.create()))
            .escapeHtml(false)
            .build();

    public record ExportedHtml(String fileName,
                               String mimeType,
                               String downloadUrl,
                               @Nullable String savedPath) {
    }

    public ExportedHtml exportMarkdown(String markdown,
                                       String filename,
                                       @Nullable String title,
                                       @Nullable Path outputTarget,
                                       @Nullable ToolContext ctx) throws IOException {
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalArgumentException("markdown is blank");
        }
        String safeBaseName = sanitizeBaseName(filename);
        Path normalizedTarget = normalizeTargetPath(outputTarget, safeBaseName);
        if (normalizedTarget == null) {
            Path workingDir = resolveWorkingDirectory(ctx);
            normalizedTarget = buildDefaultOutputPath(workingDir, safeBaseName, ChatOrigin.from(ctx)).toAbsolutePath().normalize();
        }
        String finalFileName = normalizedTarget.getFileName() != null
                ? normalizedTarget.getFileName().toString()
                : safeBaseName + ".html";

        String html = renderHtmlDocument(markdown, title, safeBaseName);
        Path parent = normalizedTarget.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(normalizedTarget, html);
        String savedPath = normalizedTarget.toAbsolutePath().normalize().toString();

        ChatOrigin origin = ChatOrigin.from(ctx);
        String fileId = diskFileRegistry.register(
                normalizedTarget,
                origin.workspaceId(),
                origin.conversationId(),
                origin.workspaceBasePath());
        return new ExportedHtml(finalFileName, HTML_MIME,
                "/api/v1/files/generated/disk/" + fileId, savedPath);
    }

    @Nullable
    public Path resolveOutputPath(@Nullable Path baseDir,
                                  @Nullable String outputPath,
                                  String filename) {
        if (!StringUtils.hasText(outputPath)) {
            return baseDir != null ? buildDefaultOutputPath(baseDir, filename) : null;
        }
        String raw = outputPath.trim();
        Path candidate = resolveOutputAlias(baseDir, raw);
        if (!candidate.isAbsolute() && baseDir != null) {
            candidate = baseDir.resolve(candidate);
        }
        boolean endsWithSeparator = raw.endsWith("/") || raw.endsWith("\\");
        if (endsWithSeparator || (Files.exists(candidate) && Files.isDirectory(candidate))) {
            candidate = candidate.resolve(sanitizeBaseName(filename) + ".html");
        }
        return ensureHtmlExtension(candidate, sanitizeBaseName(filename));
    }

    public Path buildDefaultOutputPath(Path baseDir, String filename) {
        String workspaceFolder = sanitizeBaseName(baseDir.getFileName() != null ? baseDir.getFileName().toString() : "workspace");
        return ensureHtmlExtension(baseDir.resolve("output").resolve("workspace").resolve(workspaceFolder).resolve(sanitizeBaseName(filename)),
                sanitizeBaseName(filename));
    }

    public Path buildDefaultOutputPath(Path baseDir, String filename, ChatOrigin origin) {
        String workspaceFolder = sanitizeBaseName(baseDir.getFileName() != null ? baseDir.getFileName().toString() : "workspace");
        if (origin.workspaceId() != null) {
            workspaceFolder = "workspace-" + origin.workspaceId();
        }
        String conversationFolder = sanitizeBaseName(origin.conversationId());
        if (!StringUtils.hasText(conversationFolder) || "document".equals(conversationFolder)) {
            conversationFolder = "session";
        }
        return ensureHtmlExtension(baseDir.resolve("output").resolve("workspace")
                        .resolve(workspaceFolder).resolve(conversationFolder).resolve(sanitizeBaseName(filename)),
                sanitizeBaseName(filename));
    }

    private Path resolveOutputAlias(@Nullable Path baseDir, String raw) {
        String normalizedRaw = raw.replace('\\', '/');
        if (baseDir != null && (normalizedRaw.equals("/output") || normalizedRaw.startsWith("/output/"))) {
            return baseDir.resolve(normalizedRaw.substring(1));
        }
        if (baseDir != null && (normalizedRaw.equals("output") || normalizedRaw.startsWith("output/"))) {
            return baseDir.resolve(normalizedRaw);
        }
        return Paths.get(raw);
    }

    public String sanitizeBaseName(String name) {
        if (name == null) {
            return "document";
        }
        String trimmed = name.trim();
        if (trimmed.toLowerCase().endsWith(".html")) {
            trimmed = trimmed.substring(0, trimmed.length() - 5);
        } else if (trimmed.toLowerCase().endsWith(".htm")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (char c : trimmed.toCharArray()) {
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|' || c < 0x20) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().strip();
        return cleaned.isEmpty() ? "document" : cleaned;
    }

    private Path resolveWorkingDirectory(@Nullable ToolContext ctx) {
        Path workingDir = vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(ctx);
        if (workingDir != null) {
            return workingDir;
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }

    @Nullable
    private Path normalizeTargetPath(@Nullable Path outputTarget, String safeBaseName) {
        if (outputTarget == null) {
            return null;
        }
        return ensureHtmlExtension(outputTarget, safeBaseName).toAbsolutePath().normalize();
    }

    private Path ensureHtmlExtension(Path candidate, String safeBaseName) {
        String fileName = candidate.getFileName() != null ? candidate.getFileName().toString() : "";
        if (!StringUtils.hasText(fileName)) {
            return candidate.resolve(safeBaseName + ".html");
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return candidate;
        }
        Path parent = candidate.getParent();
        String withExtension = fileName + ".html";
        return parent != null ? parent.resolve(withExtension) : Paths.get(withExtension);
    }

    private String renderHtmlDocument(String markdown, @Nullable String title, String safeBaseName) {
        Node document = parser.parse(markdown);
        String body = renderer.render(document);
        String effectiveTitle = StringUtils.hasText(title) ? title.trim() : safeBaseName;
        String header = buildReportHeader(markdown, effectiveTitle);
        return """
                <!DOCTYPE html>
                <html lang=\"zh-CN\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                  <title>%s</title>
              <style>
                                        :root {
                                            color-scheme: light;
                                            --page-bg: #eef4ff;
                                            --page-bg-accent: rgba(59, 130, 246, 0.14);
                                            --page-bg-warm: rgba(217, 119, 87, 0.12);
                                            --card-bg: #ffffff;
                                            --card-border: rgba(148, 163, 184, 0.2);
                                            --text-main: #172033;
                                            --text-sub: #52607a;
                                            --primary: #2563eb;
                                            --primary-soft: rgba(37, 99, 235, 0.12);
                                            --quote-bg: #fff7ed;
                                            --quote-border: #d97757;
                                            --table-head: #f8fafc;
                                            --code-bg: #0f172a;
                                            --code-text: #e2e8f0;
                                            --shadow-soft: 0 24px 60px rgba(15, 23, 42, 0.12);
                                        }
                                        * { box-sizing: border-box; }
                                        body {
                                            margin: 0;
                                            min-height: 100vh;
                                            background:
                                                radial-gradient(circle at top right, var(--page-bg-accent), transparent 34%%),
                                                radial-gradient(circle at top left, var(--page-bg-warm), transparent 26%%),
                                                linear-gradient(180deg, #f8fbff 0%%, var(--page-bg) 100%%);
                                            color: var(--text-main);
                                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
                                            line-height: 1.75;
                                        }
                                        .report-shell {
                                            max-width: 1120px;
                                            margin: 0 auto;
                                            padding: 48px 24px 72px;
                                        }
                                        .report {
                                            background: var(--card-bg);
                                            border: 1px solid var(--card-border);
                                            border-radius: 28px;
                                            overflow: hidden;
                                            box-shadow: var(--shadow-soft);
                                        }
                                        .report-header {
                                            padding: 36px 40px 28px;
                                            background:
                                                linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(217, 119, 87, 0.1)),
                                                linear-gradient(180deg, #ffffff, #f8fbff);
                                            border-bottom: 1px solid rgba(148, 163, 184, 0.18);
                                        }
                                        .report-eyebrow {
                                            display: inline-flex;
                                            align-items: center;
                                            gap: 8px;
                                            padding: 6px 12px;
                                            border-radius: 999px;
                                            background: var(--primary-soft);
                                            color: var(--primary);
                                            font-size: 12px;
                                            font-weight: 700;
                                            letter-spacing: 0.08em;
                                            text-transform: uppercase;
                                        }
                                        .report-title {
                                            margin: 16px 0 10px;
                                            font-size: clamp(2rem, 4vw, 2.7rem);
                                            line-height: 1.15;
                                            letter-spacing: -0.03em;
                                        }
                                        .report-meta {
                                            margin: 0;
                                            color: var(--text-sub);
                                            font-size: 0.95rem;
                                        }
                                        .report-content {
                                            padding: 36px 40px 44px;
                                        }
                                        .report-content > :first-child { margin-top: 0; }
                                        .report-content > :last-child { margin-bottom: 0; }
                                        h1, h2, h3, h4, h5, h6 {
                                            color: var(--text-main);
                                            line-height: 1.28;
                                            margin: 1.35em 0 0.65em;
                                            letter-spacing: -0.02em;
                                        }
                                        h1 {
                                            font-size: 2rem;
                                            padding-bottom: 0.42em;
                                            border-bottom: 2px solid rgba(148, 163, 184, 0.18);
                                        }
                                        h2 {
                                            font-size: 1.55rem;
                                            padding-left: 0.6rem;
                                            border-left: 4px solid rgba(37, 99, 235, 0.2);
                                        }
                                        h3 { font-size: 1.22rem; }
                                        p, ul, ol, table, blockquote, pre, hr { margin: 0.95em 0; }
                                        ul, ol { padding-left: 1.45rem; }
                                        li + li { margin-top: 0.28rem; }
                                        a {
                                            color: var(--primary);
                                            text-decoration: none;
                                            border-bottom: 1px solid rgba(37, 99, 235, 0.2);
                                        }
                                        a:hover { border-bottom-color: rgba(37, 99, 235, 0.55); }
                                        strong { color: #0f172a; }
                                        table {
                                            width: 100%%;
                                            border-collapse: collapse;
                                            font-size: 0.95rem;
                                            border-radius: 16px;
                                            overflow: hidden;
                                            border: 1px solid rgba(148, 163, 184, 0.2);
                                        }
                                        th, td {
                                            border: 1px solid rgba(148, 163, 184, 0.16);
                                            padding: 10px 12px;
                                            vertical-align: top;
                                        }
                                        th {
                                            background: var(--table-head);
                                            color: var(--text-main);
                                            font-weight: 700;
                                        }
                                        tr:nth-child(even) td { background: rgba(248, 250, 252, 0.8); }
                                        code, pre { font-family: 'Cascadia Code', Consolas, monospace; }
                                        code {
                                            padding: 0.14rem 0.38rem;
                                            border-radius: 8px;
                                            background: rgba(148, 163, 184, 0.14);
                                            font-size: 0.92em;
                                        }
                                        pre {
                                            padding: 16px 18px;
                                            background: var(--code-bg);
                                            color: var(--code-text);
                                            border-radius: 16px;
                                            overflow: auto;
                                        }
                                        pre code {
                                            padding: 0;
                                            background: transparent;
                                            color: inherit;
                                        }
                                        blockquote {
                                            padding: 14px 18px;
                                            border-left: 4px solid var(--quote-border);
                                            background: var(--quote-bg);
                                            color: #7c2d12;
                                            border-radius: 0 14px 14px 0;
                                        }
                                        hr {
                                            border: none;
                                            border-top: 1px solid rgba(148, 163, 184, 0.22);
                                        }
                                        img {
                                            display: block;
                                            max-width: 100%%;
                                            height: auto;
                                            border-radius: 16px;
                                            margin: 1.1em auto;
                                            box-shadow: 0 16px 32px rgba(15, 23, 42, 0.12);
                                        }
                                        @media (max-width: 768px) {
                                            .report-shell { padding: 20px 12px 32px; }
                                            .report-header { padding: 24px 22px 18px; }
                                            .report-content { padding: 22px; }
                                            .report-title { font-size: 1.8rem; }
                                        }
                                        @media print {
                                            body { background: #fff; }
                                            .report-shell { max-width: none; padding: 0; }
                                            .report {
                                                box-shadow: none;
                                                border: none;
                                                border-radius: 0;
                                            }
                                            .report-header {
                                                background: transparent;
                                                border-bottom: 1px solid #e5e7eb;
                                            }
                                            a {
                                                color: inherit;
                                                border-bottom: none;
                                                text-decoration: underline;
                                            }
                                        }
                                    </style>
                </head>
                <body>
                                    <section class=\"report-shell\">
                                        <main class=\"report\">
                                            %s
                                            <article class=\"report-content markdown-body\">%s</article>
                                        </main>
                                    </section>
                </body>
                </html>
                                """.formatted(escapeHtml(effectiveTitle), header, body);
        }

        private String buildReportHeader(String markdown, String effectiveTitle) {
                if (hasTopLevelHeading(markdown)) {
                        return "";
                }
                String exportedAt = EXPORT_TIME_FORMAT.format(LocalDateTime.now());
                return """
                                <header class=\"report-header\">
                                    <div class=\"report-eyebrow\">MateClaw HTML Report</div>
                                    <h1 class=\"report-title\">%s</h1>
                                    <p class=\"report-meta\">生成时间 · %s</p>
                                </header>
                                """.formatted(escapeHtml(effectiveTitle), escapeHtml(exportedAt));
        }

        private boolean hasTopLevelHeading(String markdown) {
                if (!StringUtils.hasText(markdown)) {
                        return false;
                }
                String trimmed = markdown.stripLeading();
                return trimmed.startsWith("# ") || trimmed.startsWith("<h1");
    }

    private String escapeHtml(String input) {
        return String.valueOf(input)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
