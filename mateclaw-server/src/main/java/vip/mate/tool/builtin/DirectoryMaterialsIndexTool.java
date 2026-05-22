package vip.mate.tool.builtin;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 为大目录建立紧凑索引，避免一次性把整批资料塞入上下文。
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class DirectoryMaterialsIndexTool {

    private final vip.mate.i18n.I18nService i18n;

    private static final int DEFAULT_MAX_FILES = 80;
    private static final int HARD_MAX_FILES = 200;
    private static final int DEFAULT_MAX_DEPTH = 4;
    private static final int HARD_MAX_DEPTH = 8;
    private static final int DEFAULT_PREVIEW_FILES = 12;
    private static final int HARD_PREVIEW_FILES = 30;
    private static final int DEFAULT_PREVIEW_CHARS = 220;
    private static final int HARD_PREVIEW_CHARS = 600;
    private final Cache<String, String> materialIndexCache = Caffeine.newBuilder()
            .maximumSize(128)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".markdown", ".csv", ".json", ".yaml", ".yml", ".xml",
            ".html", ".htm", ".ini", ".conf", ".toml", ".properties", ".java", ".js",
            ".ts", ".tsx", ".jsx", ".py", ".sql", ".log"
    );

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods", ".odp"
    );

    @Tool(description = """
            Build a compact index for a materials directory inside the active workspace boundary. \
            Returns a budget-friendly summary: file counts, extension groups, candidate files, and small previews for selected text files. \
            Use this before reading a large folder so only the relevant files are pulled into context.""")
    public String index_directory_materials(
            @ToolParam(description = "Absolute or relative directory path") String directoryPath,
            @ToolParam(description = "Maximum number of files to scan, default 80, hard cap 200", required = false) Integer maxFiles,
            @ToolParam(description = "Maximum recursion depth, default 4, hard cap 8", required = false) Integer maxDepth,
            @ToolParam(description = "Maximum number of text files to include previews for, default 12, hard cap 30", required = false) Integer maxPreviewFiles,
            @ToolParam(description = "Maximum preview characters per file, default 220, hard cap 600", required = false) Integer previewChars,
            @ToolParam(description = "Force refresh instead of reusing the cached index. Default false", required = false) Boolean refresh,
            @Nullable ToolContext ctx) {

        JSONObject result = new JSONObject();
        result.set("directoryPath", directoryPath);

        int fileLimit = normalizePositive(maxFiles, DEFAULT_MAX_FILES, HARD_MAX_FILES);
        int depthLimit = normalizePositive(maxDepth, DEFAULT_MAX_DEPTH, HARD_MAX_DEPTH);
        int previewFileLimit = normalizePositive(maxPreviewFiles, DEFAULT_PREVIEW_FILES, HARD_PREVIEW_FILES);
        int previewCharLimit = normalizePositive(previewChars, DEFAULT_PREVIEW_CHARS, HARD_PREVIEW_CHARS);

        result.set("maxFiles", fileLimit);
        result.set("maxDepth", depthLimit);
        result.set("maxPreviewFiles", previewFileLimit);
        result.set("previewChars", previewCharLimit);

        try {
            Path root = vip.mate.tool.guard.WorkspacePathGuard.validatePath(directoryPath, ctx);
            if (!Files.exists(root)) {
                return errorResult(directoryPath, i18n.msg("tool.read_file.error.not_found", root));
            }
            if (!Files.isDirectory(root)) {
                return errorResult(directoryPath, "目标不是目录: " + root);
            }
            if (!Files.isReadable(root)) {
                return errorResult(directoryPath, i18n.msg("tool.read_file.error.not_readable", root));
            }

            String cacheKey = buildCacheKey(root, fileLimit, depthLimit, previewFileLimit, previewCharLimit);
            if (!Boolean.TRUE.equals(refresh)) {
                String cached = materialIndexCache.getIfPresent(cacheKey);
                if (cached != null) {
                    return markCacheHit(cached);
                }
            }

            List<Path> files;
            boolean truncated;
            try (Stream<Path> stream = Files.walk(root, depthLimit)) {
                List<Path> collected = stream
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> root.relativize(path).toString().toLowerCase(Locale.ROOT)))
                        .limit((long) fileLimit + 1)
                        .toList();
                truncated = collected.size() > fileLimit;
                files = truncated ? collected.subList(0, fileLimit) : collected;
            }

            Map<String, Integer> extensionCounts = new LinkedHashMap<>();
            JSONArray filesJson = new JSONArray();
            JSONArray previewsJson = new JSONArray();
            int previewCount = 0;

            for (Path file : files) {
                String extension = extensionOf(file);
                extensionCounts.merge(extension != null ? extension : "(no extension)", 1, Integer::sum);

                JSONObject fileJson = new JSONObject();
                fileJson.set("relativePath", root.relativize(file).toString().replace('\\', '/'));
                fileJson.set("path", file.toString());
                fileJson.set("name", file.getFileName() != null ? file.getFileName().toString() : file.toString());
                fileJson.set("extension", extension);
                fileJson.set("category", classify(extension));
                try {
                    fileJson.set("size", Files.size(file));
                } catch (IOException e) {
                    fileJson.set("size", -1);
                }
                try {
                    FileTime modified = Files.getLastModifiedTime(file);
                    fileJson.set("lastModified", modified.toInstant().toString());
                } catch (IOException ignored) {
                    // best effort metadata only
                }
                filesJson.add(fileJson);

                if (previewCount < previewFileLimit && isTextExtension(extension)) {
                    String preview = readCompactPreview(file, previewCharLimit);
                    if (preview != null && !preview.isBlank()) {
                        JSONObject previewJson = new JSONObject();
                        previewJson.set("relativePath", fileJson.getStr("relativePath"));
                        previewJson.set("extension", extension);
                        previewJson.set("preview", preview);
                        previewsJson.add(previewJson);
                        previewCount++;
                    }
                }
            }

            JSONArray extensionSummary = new JSONArray();
            extensionCounts.forEach((extension, count) -> {
                JSONObject item = new JSONObject();
                item.set("extension", extension);
                item.set("count", count);
                extensionSummary.add(item);
            });

            result.set("resolvedPath", root.toString());
            result.set("fileCount", files.size());
            result.set("truncated", truncated);
            result.set("extensionSummary", extensionSummary);
            result.set("files", filesJson);
            result.set("textPreviews", previewsJson);
            result.set("usageHint", "先依据 extensionSummary 和 textPreviews 确定候选文件，再按需调用 read_file / extract_document_text 读取少量相关文件。");
            if (truncated) {
                result.set("message", "目录索引已截断。请缩小目录范围、降低 maxDepth，或分子目录继续索引。");
            }

            log.info("[DirectoryMaterialsIndex] Indexed {} files from {}", files.size(), root);
            String payload = JSONUtil.toJsonPrettyStr(result);
            materialIndexCache.put(cacheKey, payload);
            return payload;
        } catch (IllegalArgumentException e) {
            return errorResult(directoryPath, e.getMessage());
        } catch (Exception e) {
            log.error("[DirectoryMaterialsIndex] Failed to index directory: {}", e.getMessage(), e);
            return errorResult(directoryPath, "目录索引失败: " + e.getMessage());
        }
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
                if (picked.size() >= 4) {
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

    private String buildCacheKey(Path root, int fileLimit, int depthLimit, int previewFileLimit, int previewCharLimit) throws IOException {
        FileTime modified = Files.getLastModifiedTime(root);
        return root.toAbsolutePath().normalize() + "|files=" + fileLimit
                + "|depth=" + depthLimit
                + "|previewFiles=" + previewFileLimit
                + "|previewChars=" + previewCharLimit
                + "|mtime=" + modified.toMillis();
    }

    private String markCacheHit(String cached) {
        try {
            JSONObject obj = JSONUtil.parseObj(cached);
            obj.set("cacheHit", true);
            obj.set("usageHint", "已复用缓存资料索引；除非用户要求刷新/同步，不要再次遍历同一目录。");
            return JSONUtil.toJsonPrettyStr(obj);
        } catch (Exception ignored) {
            return cached;
        }
    }

    private String errorResult(String directoryPath, String message) {
        JSONObject result = new JSONObject();
        result.set("directoryPath", directoryPath);
        result.set("error", true);
        result.set("message", message);
        return JSONUtil.toJsonPrettyStr(result);
    }
}
