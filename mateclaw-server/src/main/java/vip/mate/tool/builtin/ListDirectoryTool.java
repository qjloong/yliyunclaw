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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 内置工具：列出目录内容
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class ListDirectoryTool {

    private final vip.mate.i18n.I18nService i18n;

    private static final int DEFAULT_MAX_ENTRIES = 200;
    private static final int HARD_MAX_ENTRIES = 1000;
    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final int HARD_MAX_DEPTH = 8;
    private final Cache<String, String> directoryListCache = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    @Tool(description = """
            List files and subdirectories inside a directory within the active workspace boundary. \
            Supports shallow or recursive listing. Returns structured JSON with relative paths, file types, sizes, and timestamps. \
            Use this before reading files from a folder; do not guess filenames.""")
    public String list_directory(
            @ToolParam(description = "Absolute or relative directory path") String directoryPath,
            @ToolParam(description = "Whether to recurse into subdirectories. Default false", required = false) Boolean recursive,
            @ToolParam(description = "Maximum number of entries to return, default 200, hard cap 1000", required = false) Integer maxEntries,
            @ToolParam(description = "Maximum recursion depth when recursive=true, default 3, hard cap 8", required = false) Integer maxDepth,
            @ToolParam(description = "Force refresh instead of reusing the cached listing. Default false", required = false) Boolean refresh,
            @Nullable ToolContext ctx) {

        JSONObject result = new JSONObject();
        result.set("directoryPath", directoryPath);

        boolean recursiveMode = Boolean.TRUE.equals(recursive);
        int entryLimit = normalizePositive(maxEntries, DEFAULT_MAX_ENTRIES, HARD_MAX_ENTRIES);
        int depthLimit = normalizePositive(maxDepth, DEFAULT_MAX_DEPTH, HARD_MAX_DEPTH);
        result.set("recursive", recursiveMode);
        result.set("maxEntries", entryLimit);
        result.set("maxDepth", recursiveMode ? depthLimit : 1);

        try {
            Path path = vip.mate.tool.guard.WorkspacePathGuard.validatePath(directoryPath, ctx);

            if (!Files.exists(path)) {
                return errorResult(directoryPath, i18n.msg("tool.read_file.error.not_found", path));
            }
            if (!Files.isDirectory(path)) {
                return errorResult(directoryPath, "目标不是目录: " + path);
            }
            if (!Files.isReadable(path)) {
                return errorResult(directoryPath, i18n.msg("tool.read_file.error.not_readable", path));
            }

            int walkDepth = recursiveMode ? depthLimit : 1;
            String cacheKey = buildCacheKey(path, recursiveMode, entryLimit, walkDepth);
            if (!Boolean.TRUE.equals(refresh)) {
                String cached = directoryListCache.getIfPresent(cacheKey);
                if (cached != null) {
                    return markCacheHit(cached);
                }
            }
            List<Path> entries;
            boolean truncated;
            try (Stream<Path> stream = Files.walk(path, walkDepth)) {
                List<Path> collected = stream
                        .skip(1)
                        .sorted(Comparator
                                .comparing((Path candidate) -> !Files.isDirectory(candidate))
                                .thenComparing(candidate -> candidate.getFileName() != null
                                        ? candidate.getFileName().toString().toLowerCase(Locale.ROOT)
                                        : candidate.toString().toLowerCase(Locale.ROOT)))
                        .limit((long) entryLimit + 1)
                        .toList();
                truncated = collected.size() > entryLimit;
                entries = truncated ? collected.subList(0, entryLimit) : collected;
            }

            JSONArray items = new JSONArray();
            int directoryCount = 0;
            int fileCount = 0;
            for (Path entry : entries) {
                boolean directory = Files.isDirectory(entry);
                if (directory) {
                    directoryCount++;
                } else {
                    fileCount++;
                }

                JSONObject item = new JSONObject();
                item.set("name", entry.getFileName() != null ? entry.getFileName().toString() : entry.toString());
                item.set("path", entry.toString());
                item.set("relativePath", path.relativize(entry).toString().replace('\\', '/'));
                item.set("type", directory ? "directory" : "file");
                if (!directory) {
                    try {
                        item.set("size", Files.size(entry));
                    } catch (IOException e) {
                        item.set("size", -1);
                    }
                }
                String extension = extensionOf(entry, directory);
                if (extension != null) {
                    item.set("extension", extension);
                }
                try {
                    FileTime lastModifiedTime = Files.getLastModifiedTime(entry);
                    item.set("lastModified", lastModifiedTime.toInstant().toString());
                } catch (IOException ignored) {
                    // best-effort metadata only
                }
                items.add(item);
            }

            result.set("resolvedPath", path.toString());
            result.set("returnedEntries", entries.size());
            result.set("directoryCount", directoryCount);
            result.set("fileCount", fileCount);
            result.set("truncated", truncated);
            if (truncated) {
                result.set("message", "目录结果已截断。请缩小目录范围，或提高 maxEntries / 调整 maxDepth 后继续。");
            }
            result.set("entries", items);

            log.info("[ListDirectory] Listed {} entries from {} (recursive={}, depth={})",
                    entries.size(), path, recursiveMode, walkDepth);
            String payload = JSONUtil.toJsonPrettyStr(result);
            directoryListCache.put(cacheKey, payload);
            return payload;
        } catch (IllegalArgumentException e) {
            return errorResult(directoryPath, e.getMessage());
        } catch (Exception e) {
            log.error("[ListDirectory] Failed to list directory: {}", e.getMessage(), e);
            return errorResult(directoryPath, "读取目录失败: " + e.getMessage());
        }
    }

    private int normalizePositive(Integer value, int defaultValue, int hardMax) {
        int candidate = value != null && value > 0 ? value : defaultValue;
        return Math.min(candidate, hardMax);
    }

    private String extensionOf(Path path, boolean directory) {
        if (path == null || directory) {
            return null;
        }
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot >= fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String buildCacheKey(Path path, boolean recursiveMode, int entryLimit, int walkDepth) throws IOException {
        FileTime modified = Files.getLastModifiedTime(path);
        return path.toAbsolutePath().normalize() + "|r=" + recursiveMode
                + "|limit=" + entryLimit
                + "|depth=" + walkDepth
                + "|mtime=" + modified.toMillis();
    }

    private String markCacheHit(String cached) {
        try {
            JSONObject obj = JSONUtil.parseObj(cached);
            obj.set("cacheHit", true);
            obj.set("usageHint", "已复用缓存目录列表；除非用户要求刷新/同步，不要再次遍历同一目录。");
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
