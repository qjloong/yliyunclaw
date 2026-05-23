package vip.mate.tool.document;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared DOCX export service for both tool-driven and UI-driven export flows.
 */
@Service
@RequiredArgsConstructor
public class DocxExportService {

    public static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final MarkdownDocxRenderer renderer;
    private final GeneratedFileCache cache;

    public record ExportedDocx(String fileName,
                               String mimeType,
                               String downloadUrl,
                               @Nullable String savedPath) {
    }

    public ExportedDocx exportMarkdown(String markdown,
                                       String filename,
                                       @Nullable String pageSize,
                                       @Nullable Path outputTarget) throws IOException {
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalArgumentException("markdown is blank");
        }
        String safeBaseName = sanitizeBaseName(filename);
        Path normalizedTarget = normalizeTargetPath(outputTarget, safeBaseName);
        String finalFileName = normalizedTarget != null && normalizedTarget.getFileName() != null
                ? normalizedTarget.getFileName().toString()
                : safeBaseName + ".docx";

        byte[] bytes = renderer.render(markdown, normalizePageSize(pageSize));

        String savedPath = null;
        if (normalizedTarget != null) {
            Path parent = normalizedTarget.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(normalizedTarget, bytes);
            savedPath = normalizedTarget.toAbsolutePath().normalize().toString();
        }

        String id = cache.put(bytes, finalFileName, DOCX_MIME);
        return new ExportedDocx(finalFileName, DOCX_MIME,
                "/api/v1/files/generated/" + id, savedPath);
    }

    @Nullable
    public Path resolveOutputPath(@Nullable Path baseDir,
                                  @Nullable String outputPath,
                                  String filename) {
        if (!StringUtils.hasText(outputPath)) {
            return baseDir != null ? buildDefaultOutputPath(baseDir, filename) : null;
        }
        String raw = outputPath.trim();
        Path candidate = Paths.get(raw);
        if (!candidate.isAbsolute() && baseDir != null) {
            candidate = baseDir.resolve(candidate);
        }
        boolean endsWithSeparator = raw.endsWith("/") || raw.endsWith("\\");
        if (endsWithSeparator || (Files.exists(candidate) && Files.isDirectory(candidate))) {
            candidate = candidate.resolve(sanitizeBaseName(filename) + ".docx");
        }
        return ensureDocxExtension(candidate, sanitizeBaseName(filename));
    }

    public Path buildDefaultOutputPath(Path baseDir, String filename) {
        return ensureDocxExtension(baseDir.resolve("output").resolve(sanitizeBaseName(filename)),
                sanitizeBaseName(filename));
    }

    public String sanitizeBaseName(String name) {
        if (name == null) {
            return "document";
        }
        String trimmed = name.trim();
        if (trimmed.toLowerCase().endsWith(".docx")) {
            trimmed = trimmed.substring(0, trimmed.length() - 5);
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

    private String normalizePageSize(@Nullable String pageSize) {
        return StringUtils.hasText(pageSize) ? pageSize.trim() : "A4";
    }

    @Nullable
    private Path normalizeTargetPath(@Nullable Path outputTarget, String safeBaseName) {
        if (outputTarget == null) {
            return null;
        }
        return ensureDocxExtension(outputTarget, safeBaseName).toAbsolutePath().normalize();
    }

    private Path ensureDocxExtension(Path candidate, String safeBaseName) {
        String fileName = candidate.getFileName() != null ? candidate.getFileName().toString() : "";
        if (!StringUtils.hasText(fileName)) {
            return candidate.resolve(safeBaseName + ".docx");
        }
        if (fileName.toLowerCase().endsWith(".docx")) {
            return candidate;
        }
        Path parent = candidate.getParent();
        String withExtension = fileName + ".docx";
        return parent != null ? parent.resolve(withExtension) : Paths.get(withExtension);
    }
}