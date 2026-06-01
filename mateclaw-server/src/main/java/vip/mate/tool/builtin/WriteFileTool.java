package vip.mate.tool.builtin;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import vip.mate.agent.context.ChatOrigin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 内置工具：写入文件
 * <p>
 * 创建新文件或完全覆写已有文件。自动创建不存在的父目录。
 * 创建新文件或完全覆写已有文件。
 * <p>
 * 安全说明：
 * <ul>
 *   <li>目标路径始终受 workspace / project 边界约束</li>
 *   <li>是否需要审批由当前 ToolGuard findings 与 workspace 权限模式共同决定</li>
 * </ul>
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class WriteFileTool {

    private final vip.mate.i18n.I18nService i18n;
    private final vip.mate.tool.document.GeneratedDiskFileRegistry generatedDiskFileRegistry;

    @Value("${mate.generated-files.output-dir:output}")
    private String outputDir;

    @vip.mate.tool.ConcurrencyUnsafe("file write — must serialize with reads/writes on overlapping paths")
        @Tool(description = "Write content to a file. Overwrites if exists, creates if not (auto-creates parent directories). "
            + "Returns structured JSON with filePath, bytesWritten, filename, apiUrl. "
            + "The apiUrl field is a relative URL that streams the file from disk via backend API. "
            + "Target path must stay inside the active project boundary; risky writes may require user approval.")
    public String write_file(
            @ToolParam(description = "Absolute or relative file path") String filePath,
            @ToolParam(description = "Content to write to the file") String content,
            @Nullable ToolContext ctx) {

        JSONObject result = new JSONObject();
        result.set("filePath", filePath);

        try {
            if (filePath == null || filePath.isBlank()) {
                return errorResult(filePath, i18n.msg("tool.write_file.error.path_empty"));
            }
            if (content == null) {
                content = "";
            }

            Path path;
            try {
                path = vip.mate.tool.guard.WorkspacePathGuard.validatePath(resolvePathAlias(filePath, ctx), ctx);
            } catch (IllegalArgumentException e) {
                return errorResult(filePath, e.getMessage());
            }

            // 如果路径是已有目录，拒绝
            if (Files.isDirectory(path)) {
                return errorResult(filePath, i18n.msg("tool.write_file.error.is_directory", path));
            }

            // 自动创建父目录
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
                log.info("[WriteFile] Created parent directories: {}", parent);
            }

            boolean existed = Files.exists(path);
            Path finalPath = remapWorkspaceOutputPath(path, ctx);
            if (!finalPath.equals(path)) {
                Path finalParent = finalPath.getParent();
                if (finalParent != null && !Files.exists(finalParent)) {
                    Files.createDirectories(finalParent);
                }
            }
            boolean finalExisted = Files.exists(finalPath);

            // 写入文件
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(finalPath, bytes);

            result.set("filePath", finalPath.toString());
            result.set("bytesWritten", bytes.length);
            result.set("created", !finalExisted);
            result.set("overwritten", finalExisted);
            result.set("message", existed
                    ? "Overwritten: " + finalPath + " (" + bytes.length + " bytes)"
                    : "Created: " + finalPath + " (" + bytes.length + " bytes)");

            String filename = finalPath.getFileName().toString();
            result.set("filename", filename);
            Path outputRoot = resolveOutputRoot(ctx);
            Path normalizedPath = finalPath.toAbsolutePath().normalize();
            if (normalizedPath.startsWith(outputRoot)) {
                ChatOrigin origin = ChatOrigin.from(ctx);
                String fileId = generatedDiskFileRegistry.register(
                        normalizedPath,
                        origin.workspaceId(),
                        origin.conversationId(),
                        origin.workspaceBasePath());
                result.set("apiUrl", "/api/v1/files/generated/disk/" + fileId);
                log.info("[WriteFile] {} file: {} ({} bytes), registered generated disk file", existed ? "Overwritten" : "Created", finalPath, bytes.length);
            } else {
                log.info("[WriteFile] {} file: {} ({} bytes), skip apiUrl because it is outside output root {}",
                    existed ? "Overwritten" : "Created", finalPath, bytes.length, outputRoot);
            }

        } catch (Exception e) {
            log.error("[WriteFile] Failed to write file: {}", e.getMessage(), e);
            return errorResult(filePath, i18n.msg("tool.write_file.error.write_exception", e.getMessage()));
        }

        return JSONUtil.toJsonPrettyStr(result);
    }

    private String errorResult(String filePath, String message) {
        JSONObject result = new JSONObject();
        result.set("filePath", filePath);
        result.set("error", true);
        result.set("message", message);
        return JSONUtil.toJsonPrettyStr(result);
    }

    private Path resolveOutputRoot(@Nullable ToolContext ctx) {
        Path workspaceDir = vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(ctx);
        Path base = workspaceDir != null ? workspaceDir : Paths.get(".").toAbsolutePath().normalize();
        return base.resolve(outputDir).toAbsolutePath().normalize();
    }

    private String resolvePathAlias(String rawPath, @Nullable ToolContext ctx) {
        if (rawPath == null) return null;
        Path workspaceDir = vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(ctx);
        if (workspaceDir == null) return rawPath;
        String normalized = rawPath.trim().replace('\\', '/');
        if (normalized.equals("/output") || normalized.startsWith("/output/")) {
            return workspaceDir.resolve(normalized.substring(1)).toString();
        }
        if (normalized.equals("output") || normalized.startsWith("output/")) {
            return workspaceDir.resolve(normalized).toString();
        }
        Path candidate = Paths.get(rawPath);
        if (!candidate.isAbsolute()) {
            return workspaceDir.resolve(candidate).toString();
        }
        return rawPath;
    }

    private Path remapWorkspaceOutputPath(Path originalPath, @Nullable ToolContext ctx) {
        Path outputRoot = resolveOutputRoot(ctx);
        Path normalized = originalPath.toAbsolutePath().normalize();
        if (!normalized.startsWith(outputRoot)) {
            return normalized;
        }
        ChatOrigin origin = ChatOrigin.from(ctx);
        Path scopedRoot = resolveScopedOutputRoot(outputRoot, origin);
        Path relative = outputRoot.relativize(normalized);
        Path scopedRelative;
        if (normalized.startsWith(scopedRoot)) {
            return normalized;
        }
        if (relative.getNameCount() > 0 && "workspace".equalsIgnoreCase(relative.getName(0).toString())) {
            scopedRelative = relative.getFileName();
        } else {
            scopedRelative = relative;
        }
        Path remapped = scopedRoot.resolve(scopedRelative);
        return remapped.toAbsolutePath().normalize();
    }

    private Path resolveScopedOutputRoot(Path outputRoot, ChatOrigin origin) {
        return outputRoot.resolve("workspace")
                .resolve(resolveWorkspaceFolder(origin))
                .resolve(resolveConversationFolder(origin))
                .toAbsolutePath()
                .normalize();
    }

    private String resolveWorkspaceFolder(ChatOrigin origin) {
        String basePath = origin.workspaceBasePath();
        if (basePath != null && !basePath.isBlank()) {
            try {
                Path p = Paths.get(basePath).toAbsolutePath().normalize();
                Path fileName = p.getFileName();
                if (fileName != null) {
                    String folder = sanitizeFolderName(fileName.toString());
                    if (!folder.isBlank()) {
                        return folder;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (origin.workspaceId() != null) {
            return "workspace-" + origin.workspaceId();
        }
        return "workspace";
    }

    private String resolveConversationFolder(ChatOrigin origin) {
        String conversationId = origin.conversationId();
        if (conversationId != null && !conversationId.isBlank()) {
            String folder = sanitizeFolderName(conversationId);
            if (!folder.isBlank()) {
                return folder;
            }
        }
        return "session";
    }

    private String sanitizeFolderName(String name) {
        if (name == null || name.isBlank()) return "workspace";
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.trim().toCharArray()) {
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|' || c < 0x20) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().trim();
        return cleaned.isEmpty() ? "workspace" : cleaned;
    }

}
