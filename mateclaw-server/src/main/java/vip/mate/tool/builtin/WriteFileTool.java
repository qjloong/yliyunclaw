package vip.mate.tool.builtin;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

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

    @vip.mate.tool.ConcurrencyUnsafe("file write — must serialize with reads/writes on overlapping paths")
        @Tool(description = "Write content to a file. Overwrites if exists, creates if not (auto-creates parent directories). "
            + "Returns structured JSON with filePath, bytesWritten. "
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
                path = vip.mate.tool.guard.WorkspacePathGuard.validatePath(filePath, ctx);
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

            // 写入文件
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(path, bytes);

            result.set("bytesWritten", bytes.length);
            result.set("created", !existed);
            result.set("overwritten", existed);
            result.set("message", existed
                    ? "Overwritten: " + path + " (" + bytes.length + " bytes)"
                    : "Created: " + path + " (" + bytes.length + " bytes)");

            log.info("[WriteFile] {} file: {} ({} bytes)",
                    existed ? "Overwritten" : "Created", path, bytes.length);

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
}
