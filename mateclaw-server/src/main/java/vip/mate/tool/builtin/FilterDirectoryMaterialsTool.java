package vip.mate.tool.builtin;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Query-driven lightweight shortlist tool for large material folders.
 */
@Slf4j
@Component
public class FilterDirectoryMaterialsTool {

    private static final int DEFAULT_SCAN_FILES = 160;
    private static final int HARD_SCAN_FILES = 240;
    private static final int DEFAULT_MAX_RESULTS = 12;
    private static final int HARD_MAX_RESULTS = 30;
    private static final int DEFAULT_MAX_DEPTH = 4;
    private static final int HARD_MAX_DEPTH = 8;
    private static final int DEFAULT_PREVIEW_CHARS = 180;
    private static final int HARD_PREVIEW_CHARS = 500;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}\\p{Alnum}_-]{2,}");

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".markdown", ".csv", ".json", ".yaml", ".yml", ".xml",
            ".html", ".htm", ".ini", ".conf", ".toml", ".properties", ".java", ".js",
            ".ts", ".tsx", ".jsx", ".py", ".sql", ".log"
    );

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods", ".odp"
    );

    @Tool(description = """
            Build a lightweight, query-driven shortlist from a materials directory inside the active workspace boundary.
            Prefer existing search/filter skills or tools when they are already available.
            If no richer ranking capability exists, use this tool to narrow a large folder to the most relevant candidate files before reading them.""")
    public String filter_directory_materials(
            @ToolParam(description = "Absolute or relative directory path") String directoryPath,
            @ToolParam(description = "Current task or question used to rank relevant files") String query,
            @ToolParam(description = "Maximum number of shortlisted files to return, default 12, hard cap 30", required = false) Integer maxResults,
            @ToolParam(description = "Maximum recursion depth, default 4, hard cap 8", required = false) Integer maxDepth,
            @ToolParam(description = "Maximum number of files to scan, default 160, hard cap 240", required = false) Integer maxScanFiles,
            @ToolParam(description = "Maximum preview characters per candidate, default 180, hard cap 500", required = false) Integer previewChars,
            @Nullable ToolContext ctx) {

        JSONObject result = new JSONObject();
        result.set("directoryPath", directoryPath);
        result.set("query", query);

        int scanLimit = normalizePositive(maxScanFiles, DEFAULT_SCAN_FILES, HARD_SCAN_FILES);
        int depthLimit = normalizePositive(maxDepth, DEFAULT_MAX_DEPTH, HARD_MAX_DEPTH);
        int resultLimit = normalizePositive(maxResults, DEFAULT_MAX_RESULTS, HARD_MAX_RESULTS);
        int previewLimit = normalizePositive(previewChars, DEFAULT_PREVIEW_CHARS, HARD_PREVIEW_CHARS);

        try {
            Path root = vip.mate.tool.guard.WorkspacePathGuard.validatePath(directoryPath, ctx);
            if (!Files.exists(root)) {
                return errorResult(directoryPath, "目录不存在: " + root);
            }
            if (!Files.isDirectory(root)) {
                return errorResult(directoryPath, "目标不是目录: " + root);
            }

            List<String> tokens = tokenize(query);
            result.set("tokens", tokens);

            List<Candidate> candidates;
            boolean truncated;
            try (Stream<Path> stream = Files.walk(root, depthLimit)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> root.relativize(path).toString().toLowerCase(Locale.ROOT)))
                        .limit((long) scanLimit + 1)
                        .toList();
                truncated = files.size() > scanLimit;
                if (truncated) {
                    files = files.subList(0, scanLimit);
                }
                candidates = new ArrayList<>(files.size());
                for (Path file : files) {
                    String relativePath = root.relativize(file).toString().replace('\\', '/');
                    String extension = extensionOf(file);
                    String preview = isTextExtension(extension) ? readCompactPreview(file, previewLimit) : null;
                    candidates.add(score(file, relativePath, extension, preview, tokens));
                }
            }

            candidates.sort(Comparator
                    .comparingInt(Candidate::score).reversed()
                    .thenComparing(Candidate::hasPreview).reversed()
                    .thenComparing(Candidate::lastModified, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Candidate::relativePath));

            JSONArray shortlist = new JSONArray();
            boolean allZero = candidates.stream().allMatch(c -> c.score() <= 0);
            candidates.stream().limit(resultLimit).forEach(candidate -> shortlist.add(candidate.toJson()));

            result.set("resolvedPath", root.toString());
            result.set("scanCount", candidates.size());
            result.set("truncated", truncated);
            result.set("allScoresZero", allZero);
            result.set("candidates", shortlist);
            result.set("usageHint", allZero
                    ? "未找到明显关键词匹配。请改写 query、缩小目录范围，或先用 index_directory_materials 查看主题分布。"
                    : "先读取 score 最高的少量候选文件；文本类优先 read_file，Office/PDF 优先 extract_document_text / extract_pdf_text / extract_docx_text。");
            if (truncated) {
                result.set("message", "扫描范围已截断。请缩小目录范围、降低 maxDepth，或继续对子目录单独筛选。");
            }

            return JSONUtil.toJsonPrettyStr(result);
        } catch (IllegalArgumentException e) {
            return errorResult(directoryPath, e.getMessage());
        } catch (Exception e) {
            log.error("[FilterDirectoryMaterials] Failed: {}", e.getMessage(), e);
            return errorResult(directoryPath, "目录筛选失败: " + e.getMessage());
        }
    }

    private Candidate score(Path file,
                            String relativePath,
                            String extension,
                            @Nullable String preview,
                            List<String> tokens) {
        String name = file.getFileName() != null ? file.getFileName().toString() : relativePath;
        String nameLower = name.toLowerCase(Locale.ROOT);
        String pathLower = relativePath.toLowerCase(Locale.ROOT);
        String previewLower = preview != null ? preview.toLowerCase(Locale.ROOT) : "";

        int score = 0;
        JSONArray reasons = new JSONArray();
        for (String token : tokens) {
            if (nameLower.contains(token)) {
                score += 12;
                reasons.add("name:" + token);
            }
            if (pathLower.contains(token)) {
                score += 8;
                reasons.add("path:" + token);
            }
            if (!previewLower.isEmpty() && previewLower.contains(token)) {
                score += 5;
                reasons.add("preview:" + token);
            }
        }
        if (tokens.isEmpty()) {
            score += preview != null && !preview.isBlank() ? 2 : 0;
        }
        try {
            FileTime modified = Files.getLastModifiedTime(file);
            return new Candidate(relativePath, file.toString(), name, extension, classify(extension),
                    preview, score, reasons, modified.toInstant().toString(), Files.size(file));
        } catch (IOException e) {
            return new Candidate(relativePath, file.toString(), name, extension, classify(extension),
                    preview, score, reasons, null, -1L);
        }
    }

    private List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
            if (tokens.size() >= 24) {
                break;
            }
        }
        return new ArrayList<>(tokens);
    }

    private int normalizePositive(Integer value, int defaultValue, int hardMax) {
        int candidate = value != null && value > 0 ? value : defaultValue;
        return Math.min(candidate, hardMax);
    }

    private String extensionOf(Path file) {
        if (file == null || file.getFileName() == null) {
            return null;
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) {
            return null;
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private boolean isTextExtension(String extension) {
        return extension != null && TEXT_EXTENSIONS.contains(extension);
    }

    private String classify(String extension) {
        if (extension == null) {
            return "other";
        }
        if (TEXT_EXTENSIONS.contains(extension)) {
            return "text";
        }
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return "document";
        }
        if (Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp").contains(extension)) {
            return "image";
        }
        return "other";
    }

    private String readCompactPreview(Path file, int charLimit) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<String> picked = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line != null ? line.trim() : "";
                if (trimmed.isEmpty()) {
                    continue;
                }
                picked.add(trimmed);
                if (picked.size() >= 5) {
                    break;
                }
            }
            if (picked.isEmpty()) {
                return null;
            }
            String preview = String.join(" | ", picked);
            return preview.length() > charLimit ? preview.substring(0, charLimit) + "…" : preview;
        } catch (MalformedInputException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private String errorResult(String directoryPath, String message) {
        JSONObject result = new JSONObject();
        result.set("directoryPath", directoryPath);
        result.set("error", true);
        result.set("message", message);
        return JSONUtil.toJsonPrettyStr(result);
    }

    private record Candidate(String relativePath,
                             String path,
                             String name,
                             String extension,
                             String category,
                             @Nullable String preview,
                             int score,
                             JSONArray reasons,
                             @Nullable String lastModified,
                             long size) {

        boolean hasPreview() {
            return preview != null && !preview.isBlank();
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.set("relativePath", relativePath);
            json.set("path", path);
            json.set("name", name);
            json.set("extension", extension);
            json.set("category", category);
            json.set("score", score);
            json.set("reasons", reasons);
            json.set("size", size);
            if (lastModified != null) {
                json.set("lastModified", lastModified);
            }
            if (preview != null && !preview.isBlank()) {
                json.set("preview", preview);
            }
            return json;
        }
    }
}