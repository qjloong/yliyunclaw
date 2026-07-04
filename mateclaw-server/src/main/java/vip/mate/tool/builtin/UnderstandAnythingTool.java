package vip.mate.tool.builtin;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Built-in tool: Understand-Anything codebase analysis via Python CLI.
 *
 * <h3>Design principle — single entry point</h3>
 * Understand-Anything's official architecture uses a multi-agent orchestrator:
 * {@code /understand} is the <b>one universal entry point</b>. The orchestrator
 * internally decides between full-analysis / incremental-update / review-only
 * based on git history and existing knowledge-graph state. Sub-agents (scanner,
 * file-analyzer, architecture-analyzer, domain-analyzer, tour-builder, reviewer)
 * run automatically in a fixed pipeline — users never route to them manually.
 *
 * <h3>Tool surface (4 methods, minimal overlap)</h3>
 * <ul>
 *   <li>{@link #understand_scan} — {@code /understand} — the ONE primary entry
 *       point for 95% of use cases (full scan, incremental update, domain
 *       extraction, onboarding tour — all handled internally by the pipeline).
 *       Also supports {@code --language zh} for Chinese output.</li>
 *   <li>{@link #understand_diff} — {@code /understand-diff} — distinct: analyzes
 *       <b>uncommitted changes</b> impact, not the whole codebase.</li>
 *   <li>{@link #understand_explain} — {@code /understand-explain} — distinct:
 *       deep-dives a <b>single file</b>, not a whole repo.</li>
 *   <li>{@link #understand_knowledge} — {@code /understand-knowledge} — distinct:
 *       analyzes a <b>wiki/doc directory</b> (Karpathy-style), not source code.</li>
 * </ul>
 *
 * <h3>Dashboard integration</h3>
 * After {@code understand_scan} completes, the tool can optionally start the
 * {@code /understand-dashboard} HTTP server in the background and return the
 * preview URL. Default: disabled (opt-in via {@code startDashboard=true}).
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class UnderstandAnythingTool {

    private final vip.mate.i18n.I18nService i18n;

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int SCAN_TIMEOUT_SECONDS = 300;       // full scan is heavy
    private static final int MAX_OUTPUT_BYTES = 20_000;
    private static final int DASHBOARD_PORT = 8765;
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("win");

    // ══════════════════════════════════════════════════════════════════════
    //  understand_scan — /understand (THE primary entry point)
    // ══════════════════════════════════════════════════════════════════════
    //
    //  Design note: This is the ONLY tool the LLM should call for broad
    //  codebase understanding tasks. The Understand-Anything multi-agent
    //  orchestrator internally runs the full pipeline:
    //    scanner → file-analyzer → architecture-analyzer →
    //    domain-analyzer → tour-builder → graph-reviewer → assemble
    //
    //  Therefore, questions like "分析架构", "提取业务领域", "生成导航" are
    //  ALL handled by this single tool. Do NOT route them to separate tools.
    //
    //  The LLM should call understand_scan for ANY of these user intents:
    //    "分析这个仓库的整体架构"
    //    "这个项目有哪些模块"
    //    "帮我理解这个代码库"
    //    "生成一份项目导航"
    //    "提取业务领域知识"
    //    "新人接手这个项目应该从哪里开始"
    //    "这个系统有哪些核心流程"
    //    "重新扫描更新分析"   (incremental update — costs fewer tokens)

    @vip.mate.tool.ConcurrencyUnsafe("Python understand-anything CLI may mutate local state and has provider rate limits")
    @Tool(description = """
            PRIMARY entry point for codebase understanding. Use this for ANY broad codebase analysis task.
            Internally runs Understand-Anything's full multi-agent pipeline (scanner, file-analyzer,
            architecture-analyzer, domain-analyzer, tour-builder). Supports incremental updates —
            re-running only analyzes changed files (fewer tokens). Works for: architecture overview,
            module analysis, domain extraction, onboarding guides, and general codebase understanding.
            Requires Python 3.10+ with 'understand-anything' installed. Read-only.""")
    public String understand_scan(
            @ToolParam(description = "Absolute path to the code repository to analyze") String repoPath,
            @ToolParam(description = "Output language for knowledge graph: 'en' (default) or 'zh' (Chinese)",
                    required = false) String language,
            @ToolParam(description = "Optional: limit scan to a sub-directory (useful for monorepos)",
                    required = false) String subPath,
            @ToolParam(description = "Start the interactive web dashboard after scan completes. Default false.",
                    required = false) Boolean startDashboard,
            @ToolParam(description = "Timeout in seconds, default 300", required = false) Integer timeoutSeconds,
            @Nullable ToolContext ctx) {

        java.nio.file.Path workingDir = vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(ctx);
        String targetPath = resolveScanPath(repoPath, subPath);
        int timeout = clampTimeout(timeoutSeconds, SCAN_TIMEOUT_SECONDS, 600);

        log.info("[UnderstandAnything] Scan {}, language={}, dashboard={}",
                truncateForLog(targetPath), language, startDashboard);

        JSONObject result = new JSONObject();
        result.set("repoPath", repoPath);
        result.set("action", "scan");
        if (language != null && !language.isBlank()) result.set("language", language);
        if (subPath != null && !subPath.isBlank()) result.set("subPath", subPath);

        String output = executeCommand(
                buildScanCommand(targetPath, language, workingDir),
                ctx, timeout, result, workingDir);

        // Post-scan: optionally start dashboard
        JSONObject wrapper = JSONUtil.parseObj(output);
        JSONObject inner = wrapper.getJSONObject("understand_scan_result");
        if (Boolean.TRUE.equals(startDashboard) && inner.getInt("exitCode", -1) == 0) {
            try {
                String kgPath = Path.of(targetPath, ".understand-anything", "knowledge-graph.json")
                        .toAbsolutePath().toString();
                String dashboardUrl = startDashboardServer(targetPath, workingDir);
                inner.set("knowledgeGraphPath", kgPath);
                inner.set("dashboardUrl", dashboardUrl);
                inner.set("dashboardNote",
                        "Dashboard started in background. Open the URL above to explore the interactive knowledge graph. "
                                + "The graph is color-coded by architecture layer, supports search, click-to-inspect, "
                                + "and guided tours. To stop the dashboard later, kill the background process.");
                wrapper.set("understand_scan_result", inner);
                output = wrapper.toString();
            } catch (Exception e) {
                log.warn("[UnderstandAnything] Failed to start dashboard: {}", e.getMessage());
                inner.set("dashboardError", e.getMessage());
                wrapper.set("understand_scan_result", inner);
                output = wrapper.toString();
            }
        }
        return output;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  understand_diff — /understand-diff (uncommitted changes impact)
    // ══════════════════════════════════════════════════════════════════════
    //
    //  Clear differentiator: analyzes ONLY uncommitted (staged + unstaged)
    //  changes, NOT the whole codebase. The LLM should call this ONLY when
    //  the user explicitly asks about "changes I just made" or "diff impact".
    //
    //  User intents that should route HERE:
    //    "我刚才改的代码会影响哪些地方"
    //    "这个 PR 改了什么，影响范围有多大"
    //    "分析未提交修改的影响"

    @vip.mate.tool.ConcurrencyUnsafe("Python understand-anything CLI may mutate local state and has provider rate limits")
    @Tool(description = """
            Analyze the impact of UNCOMMITTED changes only (staged + unstaged diffs).
            Use ONLY when user explicitly asks about "changes I just made", "diff impact",
            or "uncommitted modifications". Does NOT scan the whole codebase — that's
            understand_scan's job. Requires Python 3.10+ with understand-anything. Read-only.""")
    public String understand_diff(
            @ToolParam(description = "Absolute path to the git repository root") String repoPath,
            @ToolParam(description = "Timeout in seconds, default 120", required = false) Integer timeoutSeconds,
            @Nullable ToolContext ctx) {

        int timeout = clampTimeout(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS, 300);
        log.info("[UnderstandAnything] Diff: {}", truncateForLog(repoPath));

        JSONObject result = new JSONObject();
        result.set("repoPath", repoPath);
        result.set("action", "diff");

        java.nio.file.Path workingDir = vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(ctx);
        return executeCommand(
                buildSingleArgCommand("understand-diff", repoPath, workingDir),
                ctx, timeout, result, workingDir);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  understand_explain — /understand-explain (single file deep-dive)
    // ══════════════════════════════════════════════════════════════════════
    //
    //  Clear differentiator: analyzes exactly ONE file, not a repo.
    //  The LLM should call this ONLY when the user asks about a specific file.

    @vip.mate.tool.ConcurrencyUnsafe("Python understand-anything CLI may mutate local state and has provider rate limits")
    @Tool(description = """
            Deep-dive into a SINGLE source file. Use ONLY when user asks about one specific file
            (e.g. "what does src/auth/login.ts do?"). For whole-codebase analysis use understand_scan.
            Requires Python 3.10+ with understand-anything. Read-only.""")
    public String understand_explain(
            @ToolParam(description = "Absolute path to the single source file to explain") String filePath,
            @ToolParam(description = "Timeout in seconds, default 120", required = false) Integer timeoutSeconds,
            @Nullable ToolContext ctx) {

        int timeout = clampTimeout(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS, 300);
        log.info("[UnderstandAnything] Explain: {}", truncateForLog(filePath));

        JSONObject result = new JSONObject();
        result.set("filePath", filePath);
        result.set("action", "explain");

        java.nio.file.Path workingDir = vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(ctx);
        return executeCommand(
                buildSingleArgCommand("understand-explain", filePath, workingDir),
                ctx, timeout, result, workingDir);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  understand_knowledge — /understand-knowledge (wiki/doc analysis)
    // ══════════════════════════════════════════════════════════════════════
    //
    //  Clear differentiator: analyzes a documentation/wiki directory
    //  (Karpathy-style LLM Wiki), NOT source code.

    @vip.mate.tool.ConcurrencyUnsafe("Python understand-anything CLI may mutate local state and has provider rate limits")
    @Tool(description = """
            Analyze a DOCUMENTATION or wiki directory (Karpathy-style LLM Wiki), NOT source code.
            Use ONLY when user wants to understand a docs/wiki folder. For source code analysis
            use understand_scan. Requires Python 3.10+ with understand-anything. Read-only.""")
    public String understand_knowledge(
            @ToolParam(description = "Absolute path to the wiki/knowledge directory to analyze") String wikiPath,
            @ToolParam(description = "Timeout in seconds, default 120", required = false) Integer timeoutSeconds,
            @Nullable ToolContext ctx) {

        int timeout = clampTimeout(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS, 300);
        log.info("[UnderstandAnything] Knowledge: {}", truncateForLog(wikiPath));

        JSONObject result = new JSONObject();
        result.set("wikiPath", wikiPath);
        result.set("action", "knowledge");

        java.nio.file.Path workingDir = vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(ctx);
        return executeCommand(
                buildSingleArgCommand("understand-knowledge", wikiPath, workingDir),
                ctx, timeout, result, workingDir);
    }

    // ── command builders ────────────────────────────────────────────────

    private static ProcessBuilder buildScanCommand(String repoPath, String language,
                                                    java.nio.file.Path workingDir) {
        if (IS_WINDOWS) {
            String cmd = "python -m understand_anything /understand " + quoteArg(repoPath);
            if (language != null && !language.isBlank() && !"en".equalsIgnoreCase(language)) {
                cmd += " --language " + language;
            }
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/D", "/S", "/C", cmd);
            if (workingDir != null && java.nio.file.Files.isDirectory(workingDir))
                pb.directory(workingDir.toFile());
            return pb;
        } else {
            List<String> args = new ArrayList<>(
                    List.of("python3", "-m", "understand_anything", "/understand", repoPath));
            if (language != null && !language.isBlank() && !"en".equalsIgnoreCase(language)) {
                args.add("--language");
                args.add(language);
            }
            ProcessBuilder pb = new ProcessBuilder(args);
            if (workingDir != null && java.nio.file.Files.isDirectory(workingDir))
                pb.directory(workingDir.toFile());
            return pb;
        }
    }

    private static ProcessBuilder buildSingleArgCommand(String subCommand, String target,
                                                         java.nio.file.Path workingDir) {
        if (IS_WINDOWS) {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/D", "/S", "/C",
                    "python -m understand_anything /" + subCommand + " " + quoteArg(target));
            if (workingDir != null && java.nio.file.Files.isDirectory(workingDir))
                pb.directory(workingDir.toFile());
            return pb;
        } else {
            ProcessBuilder pb = new ProcessBuilder(
                    "python3", "-m", "understand_anything", "/" + subCommand, target);
            if (workingDir != null && java.nio.file.Files.isDirectory(workingDir))
                pb.directory(workingDir.toFile());
            return pb;
        }
    }

    // ── dashboard ───────────────────────────────────────────────────────

    /**
     * Start the Understand-Anything dashboard as a detached background process.
     * Returns the HTTP URL. The process keeps running after the tool returns.
     */
    private String startDashboardServer(String repoPath, java.nio.file.Path workingDir) throws IOException {
        log.info("[UnderstandAnything] Starting dashboard for {} on port {}", repoPath, DASHBOARD_PORT);

        ProcessBuilder pb;
        if (IS_WINDOWS) {
            // cmd /c start /B runs the command detached in background on Windows
            pb = new ProcessBuilder("cmd.exe", "/C", "start", "/B",
                    "python", "-m", "understand_anything", "/understand-dashboard", repoPath,
                    "--port", String.valueOf(DASHBOARD_PORT));
        } else {
            pb = new ProcessBuilder("python3", "-m", "understand_anything",
                    "/understand-dashboard", repoPath,
                    "--port", String.valueOf(DASHBOARD_PORT));
        }
        if (workingDir != null && java.nio.file.Files.isDirectory(workingDir))
            pb.directory(workingDir.toFile());

        // Detach: redirect output to /dev/null, don't wait for process
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.start(); // fire-and-forget

        return "http://localhost:" + DASHBOARD_PORT;
    }

    // ── execution engine ────────────────────────────────────────────────

    private String executeCommand(ProcessBuilder pb, @Nullable ToolContext ctx,
                                   int timeout, JSONObject result, java.nio.file.Path workingDir) {
        pb.environment().keySet().removeIf(key ->
                key.contains("KEY") || key.contains("SECRET") || key.contains("TOKEN")
                        || key.contains("PASSWORD") || key.contains("CREDENTIAL"));

        Path stdoutFile = null;
        Path stderrFile = null;
        try {
            stdoutFile = Files.createTempFile("mc_ua_out_", ".tmp");
            stderrFile = Files.createTempFile("mc_ua_err_", ".tmp");
            pb.redirectOutput(stdoutFile.toFile());
            pb.redirectError(stderrFile.toFile());

            Process process = pb.start();
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

            if (!completed) {
                killProcessTree(process);
                result.set("exitCode", -1);
                result.set("timedOut", true);
                result.set("error", "Command timed out after " + timeout + "s");
                result.set("stdout", readFileTruncated(stdoutFile, MAX_OUTPUT_BYTES));
                result.set("stderr", readFileTruncated(stderrFile, MAX_OUTPUT_BYTES / 2));
                return wrapResult(result);
            }

            int exitCode = process.exitValue();
            String stdout = readFileTruncated(stdoutFile, MAX_OUTPUT_BYTES);
            String stderr = readFileTruncated(stderrFile, MAX_OUTPUT_BYTES / 2);

            result.set("exitCode", exitCode);
            result.set("timedOut", false);

            if (exitCode != 0) {
                result.set("error", "Command exited with code " + exitCode);
                result.set("stderr", stderr);
                result.set("stdout", stdout);
            } else {
                try { result.set("output", JSONUtil.parse(stdout)); }
                catch (Exception e) { result.set("rawOutput", stdout); }
                if (!stderr.isBlank()) result.set("stderr", stderr);
            }
            return wrapResult(result);

        } catch (IOException e) {
            log.error("[UnderstandAnything] IO error: {}", e.getMessage());
            result.set("exitCode", -2);
            result.set("error", "IO error: " + e.getMessage());
            return wrapResult(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.set("exitCode", -3);
            result.set("timedOut", true);
            result.set("error", "Interrupted");
            return wrapResult(result);
        } finally {
            deleteQuietly(stdoutFile);
            deleteQuietly(stderrFile);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static int clampTimeout(Integer provided, int defaultValue, int hardCap) {
        int t = (provided != null && provided > 0) ? provided : defaultValue;
        return Math.min(t, hardCap);
    }

    private static String resolveScanPath(String repoPath, String subPath) {
        if (subPath == null || subPath.isBlank()) return repoPath;
        return Path.of(repoPath).resolve(subPath).normalize().toString();
    }

    private static String quoteArg(String arg) {
        if (arg.contains(" ")) return "\"" + arg.replace("\"", "\\\"") + "\"";
        return arg;
    }

    private static String readFileTruncated(Path file, int maxBytes) {
        if (file == null || !Files.exists(file)) return "";
        try {
            byte[] raw = Files.readAllBytes(file);
            String content = new String(raw, StandardCharsets.UTF_8);
            if (content.length() > maxBytes)
                return content.substring(0, maxBytes) + "\n\n... [truncated at " + maxBytes + " bytes]";
            return content;
        } catch (IOException e) { return "[read error: " + e.getMessage() + "]"; }
    }

    private static void killProcessTree(Process process) {
        try {
            if (IS_WINDOWS)
                new ProcessBuilder("taskkill", "/F", "/T", "/PID",
                        String.valueOf(process.pid())).start();
            else process.destroyForcibly();
        } catch (Exception ignored) { process.destroyForcibly(); }
    }

    private static void deleteQuietly(Path path) {
        if (path != null) { try { Files.deleteIfExists(path); } catch (IOException ignored) {} }
    }

    private static String truncateForLog(String s) {
        if (s == null) return "null";
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }

    private static String wrapResult(JSONObject result) {
        JSONObject wrapper = new JSONObject();
        String action = result.getStr("action", "scan");
        wrapper.set("understand_" + action + "_result", result);
        return wrapper.toString();
    }
}
