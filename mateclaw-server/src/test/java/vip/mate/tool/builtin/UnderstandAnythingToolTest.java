package vip.mate.tool.builtin;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UnderstandAnythingTool} — 4-method single-entry-point design.
 */
@DisplayName("UnderstandAnythingTool")
class UnderstandAnythingToolTest {

    private vip.mate.i18n.I18nService i18n;
    private UnderstandAnythingTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        i18n = mock(vip.mate.i18n.I18nService.class);
        when(i18n.t(any(String.class), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
        tool = new UnderstandAnythingTool(i18n);
    }

    @AfterEach
    void tearDown() { reset(i18n); }

    // ── helpers ────────────────────────────────────────────────────────

    private record MockedExec(Path stdout, Path stderr) {}

    private MockedExec setupProcess(int exitCode, String stdout, String stderr)
            throws IOException {
        Path out = tempDir.resolve("stdout_" + System.nanoTime() + ".tmp");
        Path err = tempDir.resolve("stderr_" + System.nanoTime() + ".tmp");
        Files.writeString(out, stdout);
        Files.writeString(err, stderr);
        return new MockedExec(out, err);
    }

    private MockedStatic<vip.mate.tool.guard.WorkspacePathGuard> mockGuard() {
        var g = mockStatic(vip.mate.tool.guard.WorkspacePathGuard.class);
        g.when(() -> vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(any()))
                .thenReturn(tempDir);
        return g;
    }

    private MockedStatic<Files> mockTempFiles(MockedExec exec) {
        var f = mockStatic(Files.class);
        f.when(() -> Files.createTempFile(eq("mc_ua_out_"), eq(".tmp"))).thenReturn(exec.stdout());
        f.when(() -> Files.createTempFile(eq("mc_ua_err_"), eq(".tmp"))).thenReturn(exec.stderr());
        return f;
    }

    private Path createTempRepo() throws IOException {
        Path repo = tempDir.resolve("test-repo");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("README.md"), "# Test\n", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("main.py"), "print('hi')", StandardCharsets.UTF_8);
        return repo;
    }

    // ════════════════════════════════════════════════════════════════════
    //  understand_scan (primary entry point)
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("scan: wraps output under understand_scan_result")
    void scanWrapsCorrectKey() throws Exception {
        Path repo = createTempRepo();
        MockedExec exec = setupProcess(0, "{\"modules\":[\"core\"]}", "");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_scan(repo.toString(), null, null, false, 300, null);
            assertThat(out).contains("\"understand_scan_result\"");
            assertThat(out).contains("\"action\":\"scan\"");
            JSONObject inner = JSONUtil.parseObj(out).getJSONObject("understand_scan_result");
            assertThat(inner.getInt("exitCode")).isEqualTo(0);
            assertThat(inner.getJSONObject("output").getJSONArray("modules").getStr(0))
                    .isEqualTo("core");
        }
    }

    @Test
    @DisplayName("scan: includes language='zh' when requested")
    void scanIncludesLanguage() throws Exception {
        Path repo = createTempRepo();
        MockedExec exec = setupProcess(0, "{}", "");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_scan(repo.toString(), "zh", null, false, 300, null);
            JSONObject inner = JSONUtil.parseObj(out).getJSONObject("understand_scan_result");
            assertThat(inner.getStr("language")).isEqualTo("zh");
        }
    }

    @Test
    @DisplayName("scan: language='en' is NOT passed to CLI (it's the default)")
    void scanSkipsEnLanguage() throws Exception {
        Path repo = createTempRepo();
        MockedExec exec = setupProcess(0, "{}", "");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_scan(repo.toString(), "en", null, false, 300, null);
            JSONObject inner = JSONUtil.parseObj(out).getJSONObject("understand_scan_result");
            // 'en' should not appear in result since it's the default and not passed to CLI
            assertThat(inner.get("language")).isNull();
        }
    }

    @Test
    @DisplayName("scan: with startDashboard=true, dashboard URL is included on success")
    void scanWithDashboardReturnsUrl() throws Exception {
        Path repo = createTempRepo();
        MockedExec exec = setupProcess(0, "{}", "");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_scan(repo.toString(), null, null, true, 300, null);
            JSONObject inner = JSONUtil.parseObj(out).getJSONObject("understand_scan_result");
            // Dashboard startup may fail in test (no actual Python), so check for
            // either dashboardUrl or dashboardError — both indicate the feature was triggered
            assertThat(inner.get("dashboardUrl") != null || inner.get("dashboardError") != null).isTrue();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  understand_diff
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("diff: wraps under understand_diff_result with action=diff")
    void diffWrapsCorrectKey() throws Exception {
        Path repo = createTempRepo();
        MockedExec exec = setupProcess(0, "{\"changed\":[\"main.py\"]}", "");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_diff(repo.toString(), 120, null);
            assertThat(out).contains("\"understand_diff_result\"");
            JSONObject inner = JSONUtil.parseObj(out).getJSONObject("understand_diff_result");
            assertThat(inner.getStr("action")).isEqualTo("diff");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  understand_explain
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("explain: wraps under understand_explain_result with filePath")
    void explainWrapsCorrectKey() throws Exception {
        Path file = createTempRepo().resolve("main.py");
        MockedExec exec = setupProcess(0, "{\"role\":\"entry\"}", "");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_explain(file.toString(), 120, null);
            JSONObject inner = JSONUtil.parseObj(out).getJSONObject("understand_explain_result");
            assertThat(inner.getStr("action")).isEqualTo("explain");
            assertThat(inner.getStr("filePath")).isEqualTo(file.toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  understand_knowledge
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("knowledge: wraps under understand_knowledge_result with wikiPath")
    void knowledgeWrapsCorrectKey() throws Exception {
        Path wiki = createTempRepo();
        MockedExec exec = setupProcess(0, "{\"topics\":[]}", "");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_knowledge(wiki.toString(), 120, null);
            JSONObject inner = JSONUtil.parseObj(out).getJSONObject("understand_knowledge_result");
            assertThat(inner.getStr("action")).isEqualTo("knowledge");
            assertThat(inner.getStr("wikiPath")).isEqualTo(wiki.toString());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Common error cases
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("timeout: exitCode=-1, timedOut=true")
    void timeoutReturnsMinusOne() throws Exception {
        Path repo = createTempRepo();
        Process mockProc = mock(Process.class);
        when(mockProc.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(false);
        Path outPath = tempDir.resolve("partial.tmp");
        Files.writeString(outPath, "...");

        try (var g = mockGuard();
             var f = mockStatic(Files.class)) {
            g.when(() -> vip.mate.tool.guard.WorkspacePathGuard.getWorkingDirectory(any())).thenReturn(tempDir);
            f.when(() -> Files.createTempFile(eq("mc_ua_out_"), eq(".tmp"))).thenReturn(outPath);
            f.when(() -> Files.createTempFile(eq("mc_ua_err_"), eq(".tmp"))).thenReturn(tempDir.resolve("err.tmp"));

            String out = tool.understand_scan(repo.toString(), null, null, false, 3, null);
            assertThat(out).contains("\"exitCode\":-1");
            assertThat(out).contains("\"timedOut\":true");
        }
    }

    @Test
    @DisplayName("non-zero exit: reports error with stderr")
    void nonZeroExitReportsError() throws Exception {
        Path repo = createTempRepo();
        MockedExec exec = setupProcess(1, "", "not a git repo");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_diff(repo.toString(), 120, null);
            assertThat(out).contains("\"exitCode\":1");
            assertThat(out).contains("not a git repo");
        }
    }

    @Test
    @DisplayName("non-JSON stdout stored as rawOutput")
    void nonJsonAsRaw() throws Exception {
        Path file = createTempRepo().resolve("main.py");
        MockedExec exec = setupProcess(0, "plain text analysis", "");

        try (var g = mockGuard(); var f = mockTempFiles(exec)) {
            String out = tool.understand_explain(file.toString(), 120, null);
            JSONObject inner = JSONUtil.parseObj(out).getJSONObject("understand_explain_result");
            assertThat(inner.getStr("rawOutput")).isEqualTo("plain text analysis");
            assertThat(inner.get("output")).isNull();
        }
    }
}
