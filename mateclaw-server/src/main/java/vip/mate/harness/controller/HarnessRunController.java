package vip.mate.harness.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mate.common.result.R;
import vip.mate.harness.model.DesktopLocalToolPayload;
import vip.mate.harness.model.HarnessRun;
import vip.mate.harness.service.HarnessRunService;

import java.util.List;

@Tag(name = "Harness Runs")
@RestController
@RequestMapping("/api/v1/harness/runs")
@RequiredArgsConstructor
public class HarnessRunController {

    private final HarnessRunService harnessRunService;

    @Operation(summary = "List latest in-memory harness runs")
    @GetMapping
    public R<List<HarnessRun>> latest(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(harnessRunService.listLatest(limit));
    }

    @Operation(summary = "Get latest harness run for a conversation")
    @GetMapping("/latest")
    public R<HarnessRun> latestForConversation(@RequestParam String conversationId) {
        return R.ok(harnessRunService.latestForConversation(conversationId));
    }

    @Operation(summary = "Ingest a desktop-local shared tool payload into a harness run")
    @PostMapping("/{runId}/desktop-tool-results")
    public R<HarnessRun> ingestDesktopToolResult(@PathVariable String runId,
                                                 @RequestBody DesktopLocalToolPayload payload) {
        HarnessRun run = harnessRunService.ingestDesktopLocalToolResult(runId, payload);
        if (run == null) {
            return R.fail("Harness run not found");
        }
        return R.ok(run);
    }
}
