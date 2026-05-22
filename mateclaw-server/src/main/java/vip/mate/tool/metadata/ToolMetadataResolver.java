package vip.mate.tool.metadata;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import vip.mate.tool.guard.model.GuardSeverity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds unified runtime metadata for all tool sources.
 */
@Component
public class ToolMetadataResolver {

    private static final Set<String> READ_ONLY_PREFIXES = Set.of(
            "read_", "list_", "get_", "search_", "query_", "fetch_", "find_",
            "inspect_", "check_", "view_", "probe_", "detect_", "describe_",
            "extract_", "current_"
    );

    private static final Set<String> READ_ONLY_TOKENS = Set.of(
            "read", "list", "get", "search", "query", "fetch", "find",
            "inspect", "check", "view", "probe", "detect", "describe",
            "extract", "current", "trace", "related", "explain"
    );

    private static final Set<String> MUTATING_PREFIXES = Set.of(
            "write_", "edit_", "delete_", "remove_", "create_", "update_", "toggle_",
            "run_", "execute_", "install_", "upload_", "save_", "append_", "import_",
            "export_", "generate_", "delegate_"
    );

    private static final Set<String> MUTATING_TOKENS = Set.of(
            "write", "edit", "delete", "remove", "create", "update", "toggle",
            "run", "execute", "install", "upload", "save", "append", "import",
            "export", "generate", "delegate", "compile", "enrich", "archive", "unarchive"
    );

    private static final Set<String> DESTRUCTIVE_KEYWORDS = Set.of(
            "delete", "remove", "drop", "truncate", "destroy", "overwrite", "kill"
    );

    public ToolRuntimeMetadata resolve(String toolName,
                                       ToolCallback callback,
                                       boolean concurrencySafe,
                                       long timeoutMs) {
        ToolCallback effective = unwrap(callback, new IdentityHashMap<>());
        ToolSourceType sourceType = detectSourceType(toolName, effective);
        String description = descriptionOf(callback);
        boolean returnDirect = safeReturnDirect(callback);
        boolean readOnly = inferReadOnly(toolName, description, sourceType);
        boolean destructive = inferDestructive(toolName, description, sourceType, readOnly);
        GuardSeverity riskLevel = inferRiskLevel(toolName, sourceType, readOnly, destructive);
        boolean replayable = inferReplayable(toolName, sourceType, readOnly, destructive);

        return new ToolRuntimeMetadata(
                toolName,
                sourceType,
                readOnly,
                destructive,
                riskLevel,
                timeoutMs,
                replayable,
                concurrencySafe,
                returnDirect
        );
    }

    private ToolCallback unwrap(ToolCallback callback, Map<ToolCallback, Boolean> visited) {
        if (callback == null || visited.put(callback, Boolean.TRUE) != null) {
            return callback;
        }

        ToolCallback delegate = invokeDelegateGetter(callback);
        if (delegate != null) {
            return unwrap(delegate, visited);
        }

        delegate = readDelegateField(callback);
        if (delegate != null) {
            return unwrap(delegate, visited);
        }

        return callback;
    }

    private ToolCallback invokeDelegateGetter(ToolCallback callback) {
        try {
            Method method = callback.getClass().getMethod("getDelegate");
            Object value = method.invoke(callback);
            return value instanceof ToolCallback toolCallback ? toolCallback : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ToolCallback readDelegateField(ToolCallback callback) {
        Class<?> type = callback.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField("delegate");
                field.setAccessible(true);
                Object value = field.get(callback);
                return value instanceof ToolCallback toolCallback ? toolCallback : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private ToolSourceType detectSourceType(String toolName, ToolCallback callback) {
        String lowerToolName = lower(toolName);
        if (lowerToolName.startsWith("kb_") || lowerToolName.startsWith("acp_")) {
            return ToolSourceType.SKILL;
        }
        if (lowerToolName.startsWith("wiki_")
                || lowerToolName.startsWith("session_")
                || lowerToolName.startsWith("fact_")) {
            return ToolSourceType.BUILTIN;
        }

        String className = callback != null ? callback.getClass().getName().toLowerCase(Locale.ROOT) : "";
        if (className.contains(".skill.")) {
            return ToolSourceType.SKILL;
        }
        if (className.contains(".tool.mcp.") || className.contains("mcptool") || className.contains("mcp")) {
            return ToolSourceType.MCP;
        }
        if (className.contains(".plugin.")) {
            return ToolSourceType.PLUGIN;
        }
        if (className.contains(".tool.builtin.")
                || className.contains(".wiki.tool.")
                || className.contains(".memory.search.")
                || className.contains(".memory.fact.tool.")
                || className.contains(".memory.tool.")) {
            return ToolSourceType.BUILTIN;
        }
        if (className.startsWith("vip.mate.tool.")) {
            return ToolSourceType.BUILTIN;
        }
        return ToolSourceType.UNKNOWN;
    }

    private boolean inferReadOnly(String toolName, String description, ToolSourceType sourceType) {
        String lowerToolName = lower(toolName);
        if (lowerToolName.isBlank()) {
            return false;
        }
        if (READ_ONLY_PREFIXES.stream().anyMatch(lowerToolName::startsWith)) {
            return true;
        }
        if (MUTATING_PREFIXES.stream().anyMatch(lowerToolName::startsWith)) {
            return false;
        }
        if (tokenizedVerbMatches(lowerToolName, MUTATING_TOKENS)) {
            return false;
        }
        if (tokenizedVerbMatches(lowerToolName, READ_ONLY_TOKENS)) {
            return true;
        }
        if (lowerToolName.contains("cron") && !(lowerToolName.startsWith("list_") || lowerToolName.startsWith("get_"))) {
            return false;
        }

        String lowerDescription = lower(description);
        if (lowerDescription.contains("read only") || lowerDescription.contains("read-only")) {
            return true;
        }
        if (lowerDescription.contains("overwrites") || lowerDescription.contains("creates if not")
                || lowerDescription.contains("delete") || lowerDescription.contains("execute")) {
            return false;
        }

        return sourceType == ToolSourceType.BUILTIN && lowerToolName.contains("read");
    }

    private boolean tokenizedVerbMatches(String lowerToolName, Set<String> verbs) {
        List<String> tokens = Arrays.stream(lowerToolName.split("[^a-z0-9]+"))
                .filter(token -> token != null && !token.isBlank())
                .toList();
        return tokens.stream().anyMatch(verbs::contains);
    }

    private boolean inferDestructive(String toolName,
                                     String description,
                                     ToolSourceType sourceType,
                                     boolean readOnly) {
        if (readOnly) {
            return false;
        }

        String lowerToolName = lower(toolName);
        if ("write_file".equals(lowerToolName) || "edit_file".equals(lowerToolName)
                || "execute_shell_command".equals(lowerToolName)) {
            return true;
        }
        if (DESTRUCTIVE_KEYWORDS.stream().anyMatch(lowerToolName::contains)) {
            return true;
        }

        String lowerDescription = lower(description);
        if (lowerDescription.contains("overwrites") || lowerDescription.contains("delete")
                || lowerDescription.contains("destroy") || lowerDescription.contains("remove")) {
            return true;
        }

        return sourceType != ToolSourceType.BUILTIN && !readOnly;
    }

    private GuardSeverity inferRiskLevel(String toolName,
                                         ToolSourceType sourceType,
                                         boolean readOnly,
                                         boolean destructive) {
        String lowerToolName = lower(toolName);
        if ("execute_shell_command".equals(lowerToolName)
                || lowerToolName.startsWith("shell_")
                || lowerToolName.startsWith("run_command")) {
            return GuardSeverity.HIGH;
        }
        if (lowerToolName.contains("cron") && !readOnly) {
            return destructive ? GuardSeverity.HIGH : GuardSeverity.MEDIUM;
        }
        if (destructive) {
            return GuardSeverity.HIGH;
        }
        if (readOnly) {
            return GuardSeverity.LOW;
        }
        if (sourceType == ToolSourceType.MCP || sourceType == ToolSourceType.PLUGIN
                || sourceType == ToolSourceType.SKILL || sourceType == ToolSourceType.UNKNOWN) {
            return GuardSeverity.MEDIUM;
        }
        return GuardSeverity.MEDIUM;
    }

    private boolean inferReplayable(String toolName,
                                    ToolSourceType sourceType,
                                    boolean readOnly,
                                    boolean destructive) {
        String lowerToolName = lower(toolName);
        if (readOnly) {
            return true;
        }
        if (destructive || "execute_shell_command".equals(lowerToolName) || lowerToolName.contains("cron")) {
            return false;
        }
        return sourceType == ToolSourceType.BUILTIN;
    }

    private boolean safeReturnDirect(ToolCallback callback) {
        try {
            return callback != null
                    && callback.getToolMetadata() != null
                    && callback.getToolMetadata().returnDirect();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String descriptionOf(ToolCallback callback) {
        try {
            ToolDefinition definition = callback != null ? callback.getToolDefinition() : null;
            return definition != null && definition.description() != null ? definition.description() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}