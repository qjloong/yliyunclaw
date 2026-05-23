package vip.mate.harness.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.agent.GraphEventPublisher;
import vip.mate.approval.model.ToolApprovalEntity;
import vip.mate.approval.repository.ToolApprovalMapper;
import vip.mate.harness.model.DesktopLocalToolPayload;
import vip.mate.harness.model.HarnessApproval;
import vip.mate.harness.model.HarnessExecutionSummary;
import vip.mate.harness.model.HarnessRun;
import vip.mate.harness.model.HarnessRunStatus;
import vip.mate.harness.model.HarnessStep;
import vip.mate.harness.model.HarnessToolInvocation;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HarnessRunService {

    private final ToolApprovalMapper toolApprovalMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, HarnessRun> runs = new ConcurrentHashMap<>();

    public HarnessRun startRun(String mode, String conversationId, String agentId, String agentName) {
        return startRun(mode, conversationId, agentId, agentName, Map.of());
    }

    public HarnessRun startRun(String mode, String conversationId, String agentId, String agentName,
                               Map<String, Object> metadata) {
        HarnessRun run = new HarnessRun();
        run.setId(UUID.randomUUID().toString());
        run.setMode(mode);
        run.setConversationId(conversationId);
        run.setAgentId(agentId);
        run.setAgentName(agentName);
        run.setStatus(HarnessRunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        if (metadata != null && !metadata.isEmpty()) {
            run.getMetadata().putAll(metadata);
        }
        runs.put(run.getId(), run);
        addStep(run.getId(), "run_started", "lifecycle", "completed");
        return run;
    }

    public void addStep(String runId, String name, String phase, String status) {
        HarnessRun run = runs.get(runId);
        if (run == null) return;
        HarnessStep step = new HarnessStep();
        step.setId(UUID.randomUUID().toString());
        step.setName(name);
        step.setPhase(phase);
        step.setStatus(status);
        step.setStartedAt(LocalDateTime.now());
        if (!"running".equalsIgnoreCase(status)) {
            step.setCompletedAt(LocalDateTime.now());
        }
        run.getSteps().add(step);
        refreshMockAcceptance(run);
    }

    public void mergeMetadata(String runId, Map<String, Object> metadata) {
        HarnessRun run = runs.get(runId);
        if (run == null || metadata == null || metadata.isEmpty()) return;
        synchronized (run) {
            run.getMetadata().putAll(metadata);
            refreshMockAcceptance(run);
        }
    }

    public void updateSummary(String runId, Consumer<HarnessExecutionSummary> updater) {
        HarnessRun run = runs.get(runId);
        if (run == null || updater == null) return;
        synchronized (run) {
            HarnessExecutionSummary summary = run.getSummary();
            if (summary == null) {
                summary = new HarnessExecutionSummary();
                run.setSummary(summary);
            }
            updater.accept(summary);
            refreshMockAcceptance(run);
        }
    }

    public void ingestEvents(String runId, Collection<GraphEventPublisher.GraphEvent> events) {
        HarnessRun run = runs.get(runId);
        if (run == null || events == null || events.isEmpty()) return;
        synchronized (run) {
            for (GraphEventPublisher.GraphEvent event : events) {
                if (event == null) continue;
                applyEvent(run, event);
            }
            refreshMockAcceptance(run);
        }
    }

    public void completeRun(String runId, HarnessExecutionSummary summary) {
        HarnessRun run = runs.get(runId);
        if (run == null) return;
        synchronized (run) {
            run.setStatus(HarnessRunStatus.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
            if (summary != null) {
                mergeSummary(run.getSummary(), summary);
            }
            refreshMockAcceptance(run);
        }
        addStep(runId, "run_completed", "lifecycle", "completed");
    }

    public void interruptRun(String runId, String finishReason) {
        HarnessRun run = runs.get(runId);
        if (run == null) return;
        synchronized (run) {
            HarnessExecutionSummary summary = run.getSummary() != null ? run.getSummary() : new HarnessExecutionSummary();
            if (summary.getFinishReason() == null || summary.getFinishReason().isBlank()) {
                summary.setFinishReason(finishReason != null && !finishReason.isBlank() ? finishReason : "interrupted");
            }
            run.setSummary(summary);
            run.setStatus(HarnessRunStatus.INTERRUPTED);
            run.setCompletedAt(LocalDateTime.now());
            refreshMockAcceptance(run);
        }
        addStep(runId, "run_interrupted", "lifecycle", "completed");
    }

    public void failRun(String runId, Throwable error) {
        HarnessRun run = runs.get(runId);
        if (run == null) return;
        synchronized (run) {
            HarnessExecutionSummary summary = run.getSummary() != null ? run.getSummary() : new HarnessExecutionSummary();
            summary.setFinishReason("error");
            summary.setErrorMessage(error != null ? error.getMessage() : "unknown error");
            run.setSummary(summary);
            run.setStatus(HarnessRunStatus.FAILED);
            run.setCompletedAt(LocalDateTime.now());
            refreshMockAcceptance(run);
        }
        addStep(runId, "run_failed", "lifecycle", "failed");
        log.debug("Harness run {} failed: {}", runId, error != null ? error.getMessage() : "unknown error");
    }

    public HarnessRun get(String runId) {
        HarnessRun run = runs.get(runId);
        hydrateApprovals(run);
        return run;
    }

    public HarnessRun ingestDesktopLocalToolResult(String runId, DesktopLocalToolPayload payload) {
        HarnessRun run = runs.get(runId);
        if (run == null || payload == null) {
            return null;
        }
        synchronized (run) {
            applyDesktopLocalToolPayload(run, payload);
            refreshMockAcceptance(run);
        }
        hydrateApprovals(run);
        return run;
    }

    public HarnessRun latestForConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        HarnessRun run = runs.values().stream()
                .filter(candidate -> conversationId.equals(candidate.getConversationId()))
                .max(Comparator.comparing(HarnessRun::getStartedAt))
                .orElse(null);
        hydrateApprovals(run);
        return run;
    }

    public List<HarnessRun> listLatest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<HarnessRun> latest = runs.values().stream()
                .sorted(Comparator.comparing(HarnessRun::getStartedAt).reversed())
                .limit(safeLimit)
                .toList();
        latest.forEach(this::hydrateApprovals);
        return latest;
    }

    private void applyEvent(HarnessRun run, GraphEventPublisher.GraphEvent event) {
        Map<String, Object> data = event.data() != null ? event.data() : Map.of();
        switch (event.type()) {
            case GraphEventPublisher.EVENT_PHASE -> recordPhaseStep(run, data, event.timestamp());
            case GraphEventPublisher.EVENT_PLAN_CREATED -> recordPlanCreated(run, data, event.timestamp());
            case GraphEventPublisher.EVENT_STEP_STARTED -> recordPlanStepStarted(run, data, event.timestamp());
            case GraphEventPublisher.EVENT_STEP_COMPLETED -> recordPlanStepCompleted(run, data, event.timestamp());
            case GraphEventPublisher.EVENT_TOOL_START -> recordToolStarted(run, data, event.timestamp());
            case GraphEventPublisher.EVENT_TOOL_COMPLETE -> recordToolCompleted(run, data, event.timestamp());
            case GraphEventPublisher.EVENT_TOOL_APPROVAL_REQUESTED -> recordApprovalRequested(run, data, event.timestamp());
            case GraphEventPublisher.EVENT_TOOL_DIRECT_RESULT -> recordToolDirectResult(run, data, event.timestamp());
            case GraphEventPublisher.EVENT_PERF_SUMMARY -> recordPerfSummary(run, data);
            default -> {
                // ignore unsupported event types for now
            }
        }
    }

    private void recordPhaseStep(HarnessRun run, Map<String, Object> data, long timestamp) {
        String phaseName = asString(data.get("phase"), "phase");
        HarnessStep step = new HarnessStep();
        step.setId(UUID.randomUUID().toString());
        step.setName(phaseName);
        step.setPhase("phase");
        step.setStatus("completed");
        step.setStartedAt(toLocalDateTime(timestamp));
        step.setCompletedAt(toLocalDateTime(timestamp));
        step.getMetadata().putAll(data);
        run.getSteps().add(step);
    }

    private void recordPlanCreated(HarnessRun run, Map<String, Object> data, long timestamp) {
        HarnessStep step = new HarnessStep();
        step.setId(UUID.randomUUID().toString());
        step.setName("plan_created");
        step.setPhase("planning");
        step.setStatus("completed");
        step.setStartedAt(toLocalDateTime(timestamp));
        step.setCompletedAt(toLocalDateTime(timestamp));
        step.getMetadata().putAll(data);
        run.getSteps().add(step);
        run.getMetadata().put("planCreated", true);
    }

    private void recordPlanStepStarted(HarnessRun run, Map<String, Object> data, long timestamp) {
        int index = asInt(data.get("index"), run.getSteps().size());
        HarnessStep step = findOrCreatePlanStep(run, index);
        step.setName(asString(data.get("title"), "step_" + index));
        step.setPhase("plan");
        step.setStatus("running");
        if (step.getStartedAt() == null) {
            step.setStartedAt(toLocalDateTime(timestamp));
        }
        step.getMetadata().putAll(data);
    }

    private void recordPlanStepCompleted(HarnessRun run, Map<String, Object> data, long timestamp) {
        int index = asInt(data.get("index"), -1);
        HarnessStep step = findOrCreatePlanStep(run, index >= 0 ? index : run.getSteps().size());
        if (step.getStartedAt() == null) {
            step.setStartedAt(toLocalDateTime(timestamp));
        }
        step.setPhase("plan");
        step.setStatus("completed");
        step.setCompletedAt(toLocalDateTime(timestamp));
        step.getMetadata().putAll(data);
    }

    private void recordToolStarted(HarnessRun run, Map<String, Object> data, long timestamp) {
        String toolCallId = nonBlank(asString(data.get("toolCallId"), null), UUID.randomUUID().toString());
        HarnessToolInvocation invocation = findToolInvocation(run, toolCallId)
                .orElseGet(() -> {
                    HarnessToolInvocation created = new HarnessToolInvocation();
                    created.setId(toolCallId);
                    created.setStepId(resolveCurrentStepId(run));
                    created.setToolName(asString(data.get("toolName"), "tool"));
                    created.setStatus("running");
                    created.setStartedAt(toLocalDateTime(timestamp));
                    run.getToolInvocations().add(created);
                    return created;
                });
        invocation.setToolName(asString(data.get("toolName"), invocation.getToolName()));
        invocation.setStatus("running");
        if (invocation.getStartedAt() == null) {
            invocation.setStartedAt(toLocalDateTime(timestamp));
        }
        invocation.getMetadata().putAll(data);
        invocation.getMetadata().put("arguments", asString(data.get("arguments"), ""));
        if (invocation.getToolName() != null && invocation.getToolName().startsWith("acp_")) {
            run.getMetadata().put("usedAcp", true);
        }
        if ("delegate_agent".equals(invocation.getToolName())) {
            run.getMetadata().put("usedSubagent", true);
        }
    }

    private void recordToolCompleted(HarnessRun run, Map<String, Object> data, long timestamp) {
        String toolCallId = asString(data.get("toolCallId"), "");
        String toolName = asString(data.get("toolName"), "tool");
        HarnessToolInvocation invocation = findToolInvocation(run, toolCallId)
                .orElseGet(() -> findLastRunningToolInvocation(run, toolName)
                        .orElseGet(() -> {
                            HarnessToolInvocation created = new HarnessToolInvocation();
                            created.setId(nonBlank(toolCallId, UUID.randomUUID().toString()));
                            created.setStepId(resolveCurrentStepId(run));
                            created.setToolName(toolName);
                            created.setStartedAt(toLocalDateTime(timestamp));
                            run.getToolInvocations().add(created);
                            return created;
                        }));
        invocation.setToolName(toolName);
        invocation.setStatus(Boolean.TRUE.equals(data.get("success")) ? "completed" : "failed");
        invocation.setCompletedAt(toLocalDateTime(timestamp));
        invocation.getMetadata().putAll(data);
        invocation.getMetadata().put("result", data.get("result"));
    }

    private void recordApprovalRequested(HarnessRun run, Map<String, Object> data, long timestamp) {
        String pendingId = asString(data.get("pendingId"), UUID.randomUUID().toString());
        HarnessApproval approval = findApproval(run, pendingId).orElseGet(() -> {
            HarnessApproval created = new HarnessApproval();
            created.setId(pendingId);
            created.setToolInvocationId(resolveToolInvocationId(run, asString(data.get("toolName"), "")));
            created.setStatus("pending");
            created.setRequestedAt(toLocalDateTime(timestamp));
            created.setRequestedBy(asString(run.getMetadata().get("requesterId"), ""));
            run.getApprovals().add(created);
            return created;
        });
        approval.setStatus("pending");
        approval.setRequestedAt(toLocalDateTime(timestamp));
        approval.setScope(asString(data.get("scope"), approval.getScope()));
        approval.getMetadata().putAll(data);

        findLastRunningToolInvocation(run, asString(data.get("toolName"), ""))
                .ifPresent(invocation -> invocation.setStatus("awaiting_approval"));
    }

    private void recordToolDirectResult(HarnessRun run, Map<String, Object> data, long timestamp) {
        String toolCallId = asString(data.get("toolCallId"), "");
        String toolName = asString(data.get("toolName"), "tool");
        HarnessToolInvocation invocation = findToolInvocation(run, toolCallId)
                .orElseGet(() -> findLastRunningToolInvocation(run, toolName).orElse(null));
        if (invocation == null) return;
        invocation.getMetadata().put("directResult", true);
        invocation.getMetadata().put("directResultAt", toLocalDateTime(timestamp));
    }

    private void recordPerfSummary(HarnessRun run, Map<String, Object> data) {
        Object phase = data.get("phase");
        if (phase == null) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> perfSummaries = (Map<String, Object>) run.getMetadata()
                .computeIfAbsent("perfSummaries", key -> new LinkedHashMap<String, Object>());
        perfSummaries.put(String.valueOf(phase), new LinkedHashMap<>(data));
    }

    private void applyDesktopLocalToolPayload(HarnessRun run, DesktopLocalToolPayload payload) {
        String toolName = nonBlank(payload.getToolName(), "desktop-local-tool");
        String invocationId = resolveDesktopInvocationId(payload);
        LocalDateTime eventTime = parseDesktopLocalTimestamp(payload.getTimestamp());
        HarnessToolInvocation invocation = findToolInvocation(run, invocationId)
                .orElseGet(() -> {
                    HarnessToolInvocation created = new HarnessToolInvocation();
                    created.setId(invocationId);
                    created.setStepId(resolveCurrentStepId(run));
                    created.setStartedAt(eventTime);
                    run.getToolInvocations().add(created);
                    return created;
                });

        invocation.setToolName(toolName);
        if (payload.getRiskLevel() != null && !payload.getRiskLevel().isBlank()) {
            invocation.setRiskLevel(payload.getRiskLevel());
        }
        if (invocation.getStartedAt() == null) {
            invocation.setStartedAt(eventTime);
        }

        Map<String, Object> sharedPayload = toPayloadMap(payload);
        invocation.getMetadata().put("desktopLocal", true);
        invocation.getMetadata().put("schemaVersion", nonBlank(payload.getSchemaVersion(), "desktop-local-tool-result.v1"));
        invocation.getMetadata().put("source", nonBlank(payload.getSource(), "desktop-local"));
        invocation.getMetadata().put("mode", payload.getMode());
        invocation.getMetadata().put("status", payload.getStatus());
        invocation.getMetadata().put("summary", payload.getSummary());
        invocation.getMetadata().put("workspaceRoot", payload.getWorkspaceRoot());
        invocation.getMetadata().put("cwd", payload.getCwd());
        invocation.getMetadata().put("sharedPayload", sharedPayload);
        invocation.getMetadata().put("evidence", new LinkedHashMap<>(safeMap(payload.getEvidence())));
        if (!safeMap(payload.getApproval()).isEmpty()) {
            invocation.getMetadata().put("approval", new LinkedHashMap<>(safeMap(payload.getApproval())));
        }
        if (!safeMap(payload.getTruncation()).isEmpty()) {
            invocation.getMetadata().put("truncation", new LinkedHashMap<>(safeMap(payload.getTruncation())));
        }
        invocation.getMetadata().put("arguments", buildDesktopArguments(payload));
        invocation.getMetadata().put("result", buildDesktopResult(payload));
        if (safeMap(payload.getEvidence()).containsKey("filePath")) {
            invocation.getMetadata().put("filePath", safeMap(payload.getEvidence()).get("filePath"));
        }
        if (safeMap(payload.getEvidence()).containsKey("operationsApplied")) {
            invocation.getMetadata().put("operationsApplied", safeMap(payload.getEvidence()).get("operationsApplied"));
        }
        if (safeMap(payload.getEvidence()).containsKey("created")) {
            invocation.getMetadata().put("created", safeMap(payload.getEvidence()).get("created"));
        }

        String lifecycleStatus = mapDesktopInvocationStatus(payload);
        invocation.setStatus(lifecycleStatus);
        if (!"running".equalsIgnoreCase(lifecycleStatus) && !"awaiting_approval".equalsIgnoreCase(lifecycleStatus)) {
            invocation.setCompletedAt(eventTime);
        }

        String approvalId = resolveDesktopApprovalId(payload);
        if (approvalId != null) {
            HarnessApproval approval = findApproval(run, approvalId).orElseGet(() -> {
                HarnessApproval created = new HarnessApproval();
                created.setId(approvalId);
                created.setToolInvocationId(invocation.getId());
                run.getApprovals().add(created);
                return created;
            });
            approval.setToolInvocationId(invocation.getId());
            approval.setStatus(mapDesktopApprovalStatus(payload));
            approval.setScope(asString(safeMap(payload.getApproval()).get("scope"), approval.getScope()));
            if (approval.getRequestedAt() == null) {
                approval.setRequestedAt(eventTime);
            }
            if (!"pending".equalsIgnoreCase(approval.getStatus())) {
                approval.setResolvedAt(eventTime);
            }
            approval.getMetadata().put("desktopLocal", true);
            approval.getMetadata().put("summary", payload.getSummary());
            approval.getMetadata().put("riskLevel", payload.getRiskLevel());
            approval.getMetadata().put("payload", sharedPayload);
            approval.getMetadata().putAll(safeMap(payload.getApproval()));
        }

        run.getMetadata().put("desktopLocalPayloadSeen", true);
        run.getMetadata().put("desktopLocalPayloadSchema", nonBlank(payload.getSchemaVersion(), "desktop-local-tool-result.v1"));
        run.getMetadata().put("desktopLocalPayloadSource", nonBlank(payload.getSource(), "desktop-local"));
        run.getMetadata().put("desktopLocalPayloadUpdatedAt", eventTime.toString());
    }

    private HarnessStep findOrCreatePlanStep(HarnessRun run, int index) {
        String stableId = "plan-step-" + index;
        for (HarnessStep step : run.getSteps()) {
            if (stableId.equals(step.getId())) {
                return step;
            }
        }
        HarnessStep step = new HarnessStep();
        step.setId(stableId);
        step.setName("step_" + index);
        step.setPhase("plan");
        step.getMetadata().put("index", index);
        run.getSteps().add(step);
        return step;
    }

    private java.util.Optional<HarnessToolInvocation> findToolInvocation(HarnessRun run, String toolCallId) {
        if (toolCallId == null || toolCallId.isBlank()) return java.util.Optional.empty();
        return run.getToolInvocations().stream()
                .filter(invocation -> toolCallId.equals(invocation.getId()))
                .findFirst();
    }

    private java.util.Optional<HarnessToolInvocation> findLastRunningToolInvocation(HarnessRun run, String toolName) {
        List<HarnessToolInvocation> invocations = run.getToolInvocations();
        for (int i = invocations.size() - 1; i >= 0; i--) {
            HarnessToolInvocation invocation = invocations.get(i);
            if (!Objects.equals(toolName, invocation.getToolName())) continue;
            if (invocation.getCompletedAt() == null) {
                return java.util.Optional.of(invocation);
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<HarnessApproval> findApproval(HarnessRun run, String approvalId) {
        if (approvalId == null || approvalId.isBlank()) return java.util.Optional.empty();
        return run.getApprovals().stream()
                .filter(approval -> approvalId.equals(approval.getId()))
                .findFirst();
    }

    private String resolveCurrentStepId(HarnessRun run) {
        List<HarnessStep> steps = run.getSteps();
        for (int i = steps.size() - 1; i >= 0; i--) {
            HarnessStep step = steps.get(i);
            if ("running".equalsIgnoreCase(step.getStatus())) {
                return step.getId();
            }
        }
        return steps.isEmpty() ? null : steps.get(steps.size() - 1).getId();
    }

    private String resolveToolInvocationId(HarnessRun run, String toolName) {
        return findLastRunningToolInvocation(run, toolName)
                .map(HarnessToolInvocation::getId)
                .orElse(null);
    }

    private void mergeSummary(HarnessExecutionSummary target, HarnessExecutionSummary source) {
        if (target == null || source == null) return;
        if (source.getPromptTokens() > 0) {
            target.setPromptTokens(source.getPromptTokens());
        }
        if (source.getCompletionTokens() > 0) {
            target.setCompletionTokens(source.getCompletionTokens());
        }
        if (source.getRuntimeModelName() != null && !source.getRuntimeModelName().isBlank()) {
            target.setRuntimeModelName(source.getRuntimeModelName());
        }
        if (source.getRuntimeProviderId() != null && !source.getRuntimeProviderId().isBlank()) {
            target.setRuntimeProviderId(source.getRuntimeProviderId());
        }
        if (source.getFinishReason() != null && !source.getFinishReason().isBlank()) {
            target.setFinishReason(source.getFinishReason());
        }
        if (source.getErrorMessage() != null && !source.getErrorMessage().isBlank()) {
            target.setErrorMessage(source.getErrorMessage());
        }
        if (source.getFinalAnswerPreview() != null && !source.getFinalAnswerPreview().isBlank()) {
            target.setFinalAnswerPreview(source.getFinalAnswerPreview());
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshMockAcceptance(HarnessRun run) {
        if (run == null) {
            return;
        }
        Object mockTaskObject = run.getMetadata().get("mockTask");
        if (!(mockTaskObject instanceof Map<?, ?> rawMockTask)) {
            run.getMetadata().remove("mockAcceptance");
            return;
        }
        Map<String, Object> mockTask = new LinkedHashMap<>();
        rawMockTask.forEach((key, value) -> {
            if (key != null) {
                mockTask.put(String.valueOf(key), value);
            }
        });
        if (mockTask.isEmpty()) {
            run.getMetadata().remove("mockAcceptance");
            return;
        }

        String templateId = asString(mockTask.get("templateId"), "").trim();
        String taskId = asString(mockTask.get("taskId"), "").trim();
        String title = asString(mockTask.get("title"), "").trim();
        List<String> expectedItems = toStringList(mockTask.get("expected"));
        String preview = run.getSummary() != null ? asString(run.getSummary().getFinalAnswerPreview(), "") : "";
        String finishReason = run.getSummary() != null ? asString(run.getSummary().getFinishReason(), "") : "";
        String normalizedPreview = normalizeText(preview);
        boolean codingTemplate = "builtin.coding_agent".equals(templateId);
        boolean teacherTemplate = "builtin.teacher_exam_assistant".equals(templateId);

        int readToolCount = 0;
        int writeToolCount = 0;
        int validationToolCount = 0;
        int knowledgeToolCount = 0;
        PatchEvidence patchEvidence = new PatchEvidence();
        SourceCitationEvidence citationEvidence = new SourceCitationEvidence();
        Set<String> touchedFiles = new HashSet<>();
        List<String> validationCommands = new ArrayList<>();
        List<String> validationResults = new ArrayList<>();
        for (HarnessToolInvocation invocation : run.getToolInvocations()) {
            if (invocation == null) {
                continue;
            }
            String toolName = asString(invocation.getToolName(), "");
            String normalizedToolName = toolName.toLowerCase(Locale.ROOT);
            if (isReadTool(normalizedToolName)) {
                readToolCount++;
            }
            if (isWriteTool(normalizedToolName)) {
                writeToolCount++;
            }
            if (isValidationTool(normalizedToolName, invocation.getMetadata())) {
                validationToolCount++;
            }
            if (isKnowledgeTool(normalizedToolName)) {
                knowledgeToolCount++;
            }
            collectFileEvidence(touchedFiles, invocation.getMetadata().get("arguments"));
            collectFileEvidence(touchedFiles, invocation.getMetadata().get("result"));
            collectPatchEvidence(invocation, patchEvidence);
            collectKnowledgeCitationEvidence(invocation, citationEvidence);
            collectValidationEvidence(invocation, validationCommands, validationResults);
        }

        Set<String> sourceEvidenceSet = new java.util.LinkedHashSet<>();
        collectSourceEvidence(preview, sourceEvidenceSet);
        citationEvidence.appendSummaryTo(sourceEvidenceSet);
        List<String> sourceEvidence = new ArrayList<>(sourceEvidenceSet);

        boolean planEvidence = Boolean.TRUE.equals(run.getMetadata().get("planCreated"))
                || run.getSteps().stream().anyMatch(step -> "plan".equalsIgnoreCase(step.getPhase())
                || "planning".equalsIgnoreCase(step.getPhase()))
                || containsAny(normalizedPreview, "\u8ba1\u5212", "plan");
        touchedFiles.addAll(patchEvidence.changedFiles());

        boolean hasPatchEvidence = patchEvidence.hasPatchEvidence();
        boolean hasMaterialDiff = patchEvidence.hasMaterialDiff();
        boolean retrievedSourceEvidence = citationEvidence.hasRetrievedSources();
        List<String> citedSourceEntries = citationEvidence.citedSources(preview);
        int citedSourceCount = citedSourceEntries.size();
        boolean citedRetrievedSources = citedSourceCount > 0;
        boolean mentionsFiles = !touchedFiles.isEmpty()
                || containsAny(normalizedPreview, "\u6587\u4ef6", "file", "src/", "api/");
        boolean mentionsDiff = hasMaterialDiff || containsAny(normalizedPreview, "diff", "\u6539\u52a8", "\u4fee\u6539", "\u53d8\u66f4");
        boolean mentionsRisk = containsAny(normalizedPreview, "\u98ce\u9669", "\u4e0d\u786e\u5b9a", "residual risk", "remaining risk", "\u5f85\u786e\u8ba4");
        boolean mentionsContract = containsAny(normalizedPreview, "\u5951\u7ea6", "\u63a5\u53e3", "contract", "\u524d\u7aef", "\u540e\u7aef");
        boolean mentionsSources = retrievedSourceEvidence
            || containsAny(normalizedPreview, "\u77e5\u8bc6\u5e93", "\u6765\u6e90", "\u4f9d\u636e", "\u6750\u6599", "\u4e0a\u4f20", "\u8303\u9898", "\u540d\u8457");
        boolean mentionsSourceBasis = containsAny(normalizedPreview,
            "\u6765\u6e90\u4f9d\u636e", "\u547d\u9898\u4f9d\u636e", "\u6750\u6599\u4f9d\u636e", "\u77e5\u8bc6\u5e93\u4f9d\u636e", "\u6765\u6e90\uff1a", "\u4f9d\u636e\uff1a", "\u51fa\u5904");
        boolean mentionsAnswerRubric = containsAny(normalizedPreview, "\u7b54\u6848", "\u91c7\u5206", "\u8bc4\u5206\u6807\u51c6", "rubric");
        boolean mentionsAnswerSection = containsAny(normalizedPreview, "\u53c2\u8003\u7b54\u6848", "\u7b54\u6848");
        boolean mentionsRubricSection = containsAny(normalizedPreview, "\u91c7\u5206\u70b9", "\u8bc4\u5206\u6807\u51c6", "\u8d4b\u5206");
        boolean mentionsQualityReview = containsAny(normalizedPreview,
            "\u8d28\u91cf\u5ba1\u6838", "\u5ba1\u6838\u6e05\u5355", "\u6765\u6e90\u68c0\u67e5", "\u91cd\u590d\u68c0\u67e5", "\u96be\u5ea6\u68af\u5ea6", "\u91c7\u5206\u53ef\u64cd\u4f5c\u6027", "\u6559\u5e08\u590d\u6838");
        boolean mentionsQuestionMeta = containsAny(normalizedPreview, "\u8003\u70b9", "\u9898\u578b", "\u96be\u5ea6", "\u5206\u503c");
        boolean mentionsCoverage = containsAny(normalizedPreview, "\u4eba\u7269", "\u60c5\u8282", "\u8003\u70b9", "\u96be\u5ea6", "\u57fa\u7840", "\u63d0\u5347", "\u62d3\u5c55");
        boolean mentionsPatternAnalysis = containsAny(normalizedPreview, "\u89c4\u5f8b", "\u5206\u6790", "\u8303\u9898");
        boolean mentionsRewrite = containsAny(normalizedPreview, "\u4eff\u5199", "\u6539\u5199", "\u540c\u98ce\u683c");
        boolean mentionsBookMultiplicity = countOccurrences(preview, "\u300a") >= 2;
        boolean teacherAwaitingConfirmation = teacherTemplate
                && ("awaiting_teacher_confirmation".equalsIgnoreCase(finishReason)
                || preview.contains("teacher_exam_plan_state:awaiting_confirmation")
                || containsAny(normalizedPreview, "\u547d\u9898\u65b9\u6848", "\u5f85\u786e\u8ba4"));
        boolean teacherHasPlanSection = teacherTemplate
                && (containsMarkdownHeading(preview, "\u547d\u9898\u65b9\u6848", "\u51fa\u9898\u65b9\u6848")
                || containsAny(normalizedPreview, "\u547d\u9898\u65b9\u6848", "\u51fa\u9898\u65b9\u6848"));
        boolean teacherHasQuestionSection = teacherTemplate
                && containsMarkdownHeading(preview, "\u8bd5\u9898", "\u9898\u76ee", "\u7ec3\u4e60\u9898", "\u8bd5\u5377");
        boolean teacherHasAnswerSection = teacherTemplate
                && containsMarkdownHeading(preview, "\u53c2\u8003\u7b54\u6848", "\u7b54\u6848\u89e3\u6790", "\u7b54\u6848");
        boolean teacherHasRubricSection = teacherTemplate
                && containsMarkdownHeading(preview, "\u91c7\u5206\u70b9", "\u8bc4\u5206\u6807\u51c6", "\u8bc4\u5206\u7ec6\u5219");
        boolean teacherHasQualityReviewSection = teacherTemplate
                && containsMarkdownHeading(preview, "\u547d\u9898\u8d28\u91cf\u5ba1\u6838", "\u8d28\u91cf\u5ba1\u6838", "\u5ba1\u6838");
        boolean teacherHasSourceSection = teacherTemplate
                && containsMarkdownHeading(preview, "\u6765\u6e90\u4f9d\u636e", "\u6765\u6e90", "\u4f9d\u636e");
        boolean teacherStructuredSections = teacherTemplate
                && teacherHasQuestionSection
                && teacherHasAnswerSection
                && teacherHasRubricSection
                && teacherHasQualityReviewSection
                && teacherHasSourceSection;
        int changedFileCount = !patchEvidence.changedFiles().isEmpty() ? patchEvidence.changedFiles().size() : touchedFiles.size();
        boolean focusedEdit = changedFileCount > 0 && changedFileCount <= 4;
        boolean broadEdit = changedFileCount > 6;
        List<Integer> validationExitCodes = validationResults.stream()
                .map(HarnessRunService::extractExitCode)
                .filter(Objects::nonNull)
                .toList();
        boolean validationPassed = validationResults.stream()
            .map(HarnessRunService::normalizeText)
            .anyMatch(result -> containsAny(result, "no errors found", "exit code: 0", "exit code 0", "passed", "pass", "\u6210\u529f", "\u901a\u8fc7"))
                || validationExitCodes.stream().anyMatch(code -> code == 0);
        boolean validationFailed = validationResults.stream()
            .map(HarnessRunService::normalizeText)
            .anyMatch(result -> containsAny(result, "failed", "error", "exception", "exit code: 1", "exit code 1", "\u672a\u901a\u8fc7", "\u5931\u8d25"))
                || validationExitCodes.stream().anyMatch(code -> code != 0);

        List<String> matchedItems = new ArrayList<>();
        List<String> missingItems = new ArrayList<>();
        for (String item : expectedItems) {
            boolean matched = matchExpectedItem(item, normalizedPreview, planEvidence, writeToolCount, validationToolCount,
                    mentionsFiles, mentionsDiff, mentionsRisk, mentionsContract, mentionsSources,
                    mentionsSourceBasis, mentionsAnswerRubric, mentionsQualityReview, mentionsQuestionMeta,
                    mentionsCoverage, mentionsPatternAnalysis, mentionsRewrite,
                    mentionsBookMultiplicity, focusedEdit, knowledgeToolCount, !sourceEvidence.isEmpty(),
                    hasMaterialDiff, validationPassed, retrievedSourceEvidence, citedRetrievedSources);
            if (!matched && teacherTemplate) {
                matched = matchTeacherExpectedItem(item, teacherAwaitingConfirmation, teacherHasPlanSection,
                        teacherStructuredSections, teacherHasQuestionSection, teacherHasAnswerSection,
                        teacherHasRubricSection, teacherHasQualityReviewSection, teacherHasSourceSection,
                        mentionsSources, mentionsSourceBasis, retrievedSourceEvidence, citedRetrievedSources);
            }
            if (matched) {
                matchedItems.add(item);
            } else {
                missingItems.add(item);
            }
        }

        boolean requiresMutation = containsAny(normalizeText(title), "bugfix", "\u4fee\u590d", "\u589e\u5f3a", "\u8865\u9f50", "\u63a5\u53e3")
                || expectedItems.stream().anyMatch(item -> containsAny(normalizeText(item), "\u4fee\u6539", "diff", "\u6d4b\u8bd5\u7ed3\u679c", "\u7f16\u8bd1", "\u8bca\u65ad", "\u5951\u7ea6"));
        boolean requiresValidation = containsAny(normalizeText(title), "bugfix", "\u589e\u5f3a", "\u68c0\u6d4b", "\u7ec3\u4e60")
                || expectedItems.stream().anyMatch(item -> containsAny(normalizeText(item), "\u6d4b\u8bd5", "\u7f16\u8bd1", "\u8bca\u65ad", "\u8bc4\u5206\u6807\u51c6", "\u91c7\u5206"));
        boolean readOnlyViolation = !requiresMutation && (writeToolCount > 0 || hasMaterialDiff);
        boolean mutationGateFailed = codingTemplate && requiresMutation && !hasMaterialDiff;
        boolean validationGateFailed = codingTemplate && requiresValidation && !validationPassed;
        boolean readOnlyGateFailed = codingTemplate && readOnlyViolation;
        boolean sourceGroundingGateFailed = teacherTemplate && !teacherAwaitingConfirmation && !retrievedSourceEvidence && !containsAny(normalizedPreview, "\u6765\u6e90", "\u77e5\u8bc6\u5e93", "\u4f9d\u636e", "\u6750\u6599", "\u300a");
        boolean sourceBasisGateFailed = teacherTemplate
            && !teacherAwaitingConfirmation
            && (retrievedSourceEvidence || knowledgeToolCount > 0 || mentionsSources)
            && !mentionsSourceBasis;
        boolean sourceCitationGateFailed = teacherTemplate && !teacherAwaitingConfirmation && retrievedSourceEvidence
            && run.getStatus() != HarnessRunStatus.RUNNING && !citedRetrievedSources;
        boolean answerRubricGateFailed = teacherTemplate && !teacherAwaitingConfirmation && !(teacherHasAnswerSection && teacherHasRubricSection);
        boolean qualityReviewGateFailed = teacherTemplate && !teacherAwaitingConfirmation && !teacherHasQualityReviewSection;
        boolean teacherStructuredGateFailed = teacherTemplate && !teacherAwaitingConfirmation && !teacherStructuredSections;
        List<String> gateBlockers = new ArrayList<>();
        if (mutationGateFailed) {
            gateBlockers.add("missing patch diff");
        }
        if (validationGateFailed) {
            gateBlockers.add(validationToolCount > 0 ? "validation did not pass" : "validation not executed");
        }
        if (readOnlyGateFailed) {
            gateBlockers.add("read-only task modified files");
        }
        if (sourceGroundingGateFailed) {
            gateBlockers.add("missing source grounding");
        }
        if (sourceBasisGateFailed) {
            gateBlockers.add("missing explicit source basis");
        }
        if (sourceCitationGateFailed) {
            gateBlockers.add("retrieved sources not cited");
        }
        if (answerRubricGateFailed) {
            gateBlockers.add("missing answer or scoring rubric");
        }
        if (qualityReviewGateFailed) {
            gateBlockers.add("missing quality review checklist");
        }
        if (teacherAwaitingConfirmation) {
            gateBlockers.remove("missing source grounding");
            gateBlockers.remove("missing explicit source basis");
            gateBlockers.remove("retrieved sources not cited");
            gateBlockers.remove("missing answer or scoring rubric");
            gateBlockers.remove("missing quality review checklist");
        }
        if (teacherStructuredGateFailed) {
            gateBlockers.add("missing teacher result sections");
        }
        if (teacherAwaitingConfirmation) {
            gateBlockers.add("awaiting teacher confirmation");
        }

        int expectedSize = expectedItems.size();
        int matchedSize = matchedItems.size();
        int score = expectedSize == 0 ? 25 : (int) Math.round(45.0 * matchedSize / expectedSize);
        score += Math.min(15, run.getSteps().size() >= 4 ? 15 : run.getSteps().size() >= 2 ? 10 : run.getSteps().size() >= 1 ? 5 : 0);
        if (planEvidence) {
            score += 10;
        }
        if (preview != null && !preview.isBlank()) {
            score += 5;
        }
        if (readToolCount > 0) {
            score += 5;
        }

        List<String> evidence = new ArrayList<>();
        if (planEvidence) {
            evidence.add("plan evidence");
        }
        if (readToolCount > 0) {
            evidence.add("read tools=" + readToolCount);
        }
        if (writeToolCount > 0) {
            evidence.add("write tools=" + writeToolCount);
        }
        if (validationToolCount > 0) {
            evidence.add("validation tools=" + validationToolCount);
        }
        if (hasPatchEvidence) {
            evidence.add("diff files=" + patchEvidence.changedFiles().size()
                    + " (+" + patchEvidence.addedLines() + "/-" + patchEvidence.removedLines() + ")");
        }
        if (!touchedFiles.isEmpty()) {
            evidence.add("files=" + String.join(", ", touchedFiles.stream().sorted().limit(4).toList()));
        }
        if (!validationCommands.isEmpty()) {
            evidence.add("validation=" + String.join(" | ", validationCommands.stream().limit(2).toList()));
        }
        if (knowledgeToolCount > 0) {
            evidence.add("knowledge tools=" + knowledgeToolCount);
        }
        if (retrievedSourceEvidence) {
            evidence.add("retrieved sources=" + citationEvidence.sourceCount());
        }
        if (citedRetrievedSources) {
            evidence.add("answer cites sources=" + citedSourceCount);
        }
        if (validationPassed) {
            evidence.add("validation passed");
        } else if (validationFailed) {
            evidence.add("validation failed");
        }
        if (mentionsDiff) {
            evidence.add("diff summary mentioned");
        }
        if (mentionsRisk) {
            evidence.add("risk summary mentioned");
        }
        if (readOnlyViolation) {
            evidence.add("read-only task wrote files");
        }
        if (teacherAwaitingConfirmation) {
            evidence.add("teacher workflow awaiting confirmation");
        }
        if (teacherStructuredSections) {
            evidence.add("teacher structured sections complete");
        }
        if (!gateBlockers.isEmpty()) {
            evidence.add("gate blockers=" + String.join(" | ", gateBlockers));
        }

        if (codingTemplate) {
            if (requiresMutation) {
                score += hasMaterialDiff ? 14 : writeToolCount > 0 ? 6 : -10;
            } else if (!readOnlyViolation) {
                score += 10;
                evidence.add("read-only behavior preserved");
            }
            if (requiresValidation && validationPassed) {
                score += 15;
            } else if (requiresValidation && validationToolCount > 0 && !validationFailed) {
                score += 8;
            } else if (requiresValidation && validationFailed) {
                score -= 12;
            }
            if (mentionsFiles) {
                score += 8;
            }
            if (!touchedFiles.isEmpty()) {
                score += 5;
            }
            if (focusedEdit) {
                score += 8;
                evidence.add("focused edit scope");
            } else if (broadEdit) {
                score -= 8;
                evidence.add("edit scope too broad");
            }
            if (hasMaterialDiff) {
                score += 10;
                evidence.add("patch diff captured");
            } else if (mentionsDiff) {
                score += 8;
            }
            if (mentionsContract) {
                score += 8;
            }
            if (mentionsRisk) {
                score += 7;
            }
            if (readOnlyViolation) {
                score -= 18;
            }
            if (mutationGateFailed) {
                score -= 12;
            }
            if (validationGateFailed) {
                score -= validationToolCount > 0 ? 14 : 10;
            }
        } else if (teacherTemplate) {
            if (teacherAwaitingConfirmation) {
                score += 18;
            }
            if (teacherStructuredSections) {
                score += 16;
                evidence.add("structured teacher result sections");
            }
            if (retrievedSourceEvidence) {
                score += 14;
                evidence.add("source traces captured");
            } else if (mentionsSources) {
                score += 6;
                evidence.add("source grounding mentioned");
            }
            if (mentionsSourceBasis) {
                score += 10;
                evidence.add("explicit source basis section");
            }
            if (knowledgeToolCount > 0) {
                score += 10;
                evidence.add("knowledge tools used");
            }
            if (!sourceEvidence.isEmpty()) {
                score += 8;
                evidence.add("sources=" + String.join(" | ", sourceEvidence.stream().limit(3).toList()));
            }
            if (citedRetrievedSources) {
                score += 10;
                evidence.add("source citations in answer");
            } else if (retrievedSourceEvidence && run.getStatus() != HarnessRunStatus.RUNNING) {
                score -= 6;
                evidence.add("sources not cited in answer");
            }
            if (mentionsAnswerRubric) {
                score += 15;
                evidence.add("answer/rubric mentioned");
            }
            if (mentionsQuestionMeta) {
                score += 8;
                evidence.add("question metadata labeled");
            }
            if (mentionsCoverage) {
                score += 10;
                evidence.add("coverage/difficulty mentioned");
            }
            if (mentionsQualityReview) {
                score += 10;
                evidence.add("quality review included");
            }
            if (mentionsPatternAnalysis) {
                score += 8;
            }
            if (mentionsRewrite) {
                score += 8;
            }
            if (mentionsBookMultiplicity) {
                score += 6;
            }
            if (sourceGroundingGateFailed) {
                score -= 16;
            }
            if (sourceBasisGateFailed) {
                score -= 10;
            }
            if (sourceCitationGateFailed) {
                score -= 10;
            }
            if (answerRubricGateFailed) {
                score -= 14;
            }
            if (qualityReviewGateFailed) {
                score -= 10;
            }
        } else {
            if (mentionsFiles) {
                score += 5;
            }
            if (validationToolCount > 0) {
                score += 5;
            }
            if (mentionsRisk) {
                score += 5;
            }
        }

        score = Math.max(0, Math.min(score, 100));
        int passThreshold = teacherTemplate ? 72 : 75;
        int minimumMatched = expectedSize <= 1 ? expectedSize : Math.max(1, (int) Math.ceil(expectedSize * 0.67));
        String assessmentStatus;
        if (run.getStatus() == HarnessRunStatus.RUNNING) {
            assessmentStatus = "running";
        } else if (run.getStatus() == HarnessRunStatus.FAILED || run.getStatus() == HarnessRunStatus.INTERRUPTED) {
            assessmentStatus = score >= 45 || matchedSize > 0 ? "partial" : "failed";
        } else if (teacherTemplate && teacherAwaitingConfirmation) {
            assessmentStatus = "partial";
        } else if ((codingTemplate || teacherTemplate) && !gateBlockers.isEmpty()) {
            assessmentStatus = score >= 45 || matchedSize > 0 ? "partial" : "failed";
        } else if (score >= passThreshold && matchedSize >= minimumMatched) {
            assessmentStatus = "passed";
        } else if (score >= 45 || matchedSize > 0) {
            assessmentStatus = "partial";
        } else {
            assessmentStatus = "failed";
        }

        String confidence = score >= 80 && evidence.size() >= 4 ? "high"
                : score >= 55 && evidence.size() >= 2 ? "medium"
                : "low";
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("plan", planEvidence);
        signals.put("readTools", readToolCount);
        signals.put("writeTools", writeToolCount);
        signals.put("validationTools", validationToolCount);
        signals.put("knowledgeTools", knowledgeToolCount);
        signals.put("touchedFiles", touchedFiles.stream().sorted().toList());
        signals.put("diffFiles", patchEvidence.changedFiles().stream().sorted().toList());
        signals.put("diffFileCount", patchEvidence.changedFiles().size());
        signals.put("diffHunks", patchEvidence.hunkCount());
        signals.put("diffAddedLines", patchEvidence.addedLines());
        signals.put("diffRemovedLines", patchEvidence.removedLines());
        signals.put("hasMaterialDiff", hasMaterialDiff);
        signals.put("readOnlyViolation", readOnlyViolation);
        signals.put("requiresMutation", requiresMutation);
        signals.put("requiresValidation", requiresValidation);
        signals.put("focusedEdit", focusedEdit);
        signals.put("broadEdit", broadEdit);
        signals.put("validationCommands", List.copyOf(validationCommands));
        signals.put("validationResults", List.copyOf(validationResults));
        signals.put("validationExitCodes", validationExitCodes);
        signals.put("validationPassed", validationPassed);
        signals.put("validationFailed", validationFailed);
        signals.put("mutationGateFailed", mutationGateFailed);
        signals.put("validationGateFailed", validationGateFailed);
        signals.put("sourceGroundingGateFailed", sourceGroundingGateFailed);
        signals.put("sourceBasisGateFailed", sourceBasisGateFailed);
        signals.put("sourceCitationGateFailed", sourceCitationGateFailed);
        signals.put("answerRubricGateFailed", answerRubricGateFailed);
        signals.put("qualityReviewGateFailed", qualityReviewGateFailed);
        signals.put("teacherAwaitingConfirmation", teacherAwaitingConfirmation);
        signals.put("teacherHasPlanSection", teacherHasPlanSection);
        signals.put("teacherHasQuestionSection", teacherHasQuestionSection);
        signals.put("teacherHasAnswerSection", teacherHasAnswerSection);
        signals.put("teacherHasRubricSection", teacherHasRubricSection);
        signals.put("teacherHasQualityReviewSection", teacherHasQualityReviewSection);
        signals.put("teacherHasSourceSection", teacherHasSourceSection);
        signals.put("teacherStructuredSections", teacherStructuredSections);
        signals.put("teacherStructuredGateFailed", teacherStructuredGateFailed);
        signals.put("gateBlockers", List.copyOf(gateBlockers));
        signals.put("mentionsDiff", mentionsDiff);
        signals.put("mentionsRisk", mentionsRisk);
        signals.put("mentionsContract", mentionsContract);
        signals.put("mentionsSources", mentionsSources);
        signals.put("mentionsSourceBasis", mentionsSourceBasis);
        signals.put("sourceEvidence", List.copyOf(sourceEvidence));
        signals.put("retrievedSourceEvidence", retrievedSourceEvidence);
        signals.put("retrievedSourceTitles", citationEvidence.retrievedTitles());
        signals.put("retrievedSourceFiles", citationEvidence.sourceFiles());
        signals.put("retrievedSections", citationEvidence.sections());
        signals.put("citedSources", List.copyOf(citedSourceEntries));
        signals.put("citedSourceCount", citedSourceCount);
        signals.put("mentionsAnswerRubric", mentionsAnswerRubric);
        signals.put("mentionsAnswerSection", mentionsAnswerSection);
        signals.put("mentionsRubricSection", mentionsRubricSection);
        signals.put("mentionsCoverage", mentionsCoverage);
        signals.put("mentionsQualityReview", mentionsQualityReview);
        signals.put("mentionsQuestionMeta", mentionsQuestionMeta);

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("templateId", templateId);
        assessment.put("taskId", taskId);
        assessment.put("taskTitle", title);
        assessment.put("status", assessmentStatus);
        assessment.put("score", score);
        assessment.put("confidence", confidence);
        assessment.put("matched", matchedSize);
        assessment.put("total", expectedSize);
        assessment.put("matchedItems", matchedItems);
        assessment.put("missingItems", missingItems);
        assessment.put("gateSatisfied", gateBlockers.isEmpty());
        assessment.put("gateBlockers", gateBlockers);
        assessment.put("evidence", evidence);
        assessment.put("signals", signals);
        run.getMetadata().put("mockAcceptance", assessment);
    }

    private boolean matchTeacherExpectedItem(String item,
                                             boolean awaitingConfirmation,
                                             boolean hasPlanSection,
                                             boolean structuredSections,
                                             boolean hasQuestionSection,
                                             boolean hasAnswerSection,
                                             boolean hasRubricSection,
                                             boolean hasQualityReviewSection,
                                             boolean hasSourceSection,
                                             boolean mentionsSources,
                                             boolean mentionsSourceBasis,
                                             boolean retrievedSourceEvidence,
                                             boolean citedRetrievedSources) {
        String normalizedItem = normalizeText(item);
        if (normalizedItem.isBlank()) {
            return false;
        }
        if (containsAny(normalizedItem,
                "\u5148\u8f93\u51fa\u547d\u9898\u65b9\u6848", "\u547d\u9898\u65b9\u6848", "\u51fa\u9898\u65b9\u6848",
                "\u7b49\u5f85\u786e\u8ba4", "\u5148\u786e\u8ba4", "\u672a\u786e\u8ba4")) {
            return awaitingConfirmation || hasPlanSection;
        }
        if (containsAny(normalizedItem,
                "\u672a\u786e\u8ba4\u524d\u4e0d\u5f97\u751f\u6210", "\u4e0d\u76f4\u63a5\u51fa\u5b8c\u6574\u8bd5\u9898",
                "\u4e0d\u5f97\u76f4\u63a5\u51fa\u9898")) {
            return awaitingConfirmation;
        }
        if (containsAny(normalizedItem, "\u7ed3\u6784\u5316", "\u5206\u5757", "\u8bd5\u9898\u4f18\u5148")) {
            return structuredSections || (hasQuestionSection && (hasAnswerSection || hasRubricSection));
        }
        if (containsAny(normalizedItem, "\u8bd5\u9898", "\u9898\u76ee", "\u8bd5\u5377")) {
            return hasQuestionSection;
        }
        if (containsAny(normalizedItem, "\u53c2\u8003\u7b54\u6848", "\u7b54\u6848")) {
            return hasAnswerSection;
        }
        if (containsAny(normalizedItem, "\u91c7\u5206\u70b9", "\u8bc4\u5206\u6807\u51c6", "\u8bc4\u5206\u7ec6\u5219")) {
            return hasRubricSection;
        }
        if (containsAny(normalizedItem, "\u547d\u9898\u8d28\u91cf\u5ba1\u6838", "\u8d28\u91cf\u5ba1\u6838", "\u5ba1\u6838")) {
            return hasQualityReviewSection;
        }
        if (containsAny(normalizedItem, "\u6765\u6e90\u4f9d\u636e", "\u6765\u6e90", "\u4f9d\u636e", "\u77e5\u8bc6\u5e93")) {
            return hasSourceSection || mentionsSourceBasis || mentionsSources || retrievedSourceEvidence || citedRetrievedSources;
        }
        return false;
    }

    private static boolean containsMarkdownHeading(String text, String... headings) {
        if (text == null || text.isBlank() || headings == null || headings.length == 0) {
            return false;
        }
        String normalized = text.replace("\r\n", "\n");
        for (String heading : headings) {
            if (heading == null || heading.isBlank()) {
                continue;
            }
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "(?m)^\\s{0,3}#{1,6}\\s*" + java.util.regex.Pattern.quote(heading)
                            + "(?:\\s|[:\uff1a#]|$)");
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean matchExpectedItem(String item,
                                      String normalizedPreview,
                                      boolean planEvidence,
                                      int writeToolCount,
                                      int validationToolCount,
                                      boolean mentionsFiles,
                                      boolean mentionsDiff,
                                      boolean mentionsRisk,
                                      boolean mentionsContract,
                                      boolean mentionsSources,
                                      boolean mentionsSourceBasis,
                                      boolean mentionsAnswerRubric,
                                      boolean mentionsQualityReview,
                                      boolean mentionsQuestionMeta,
                                      boolean mentionsCoverage,
                                      boolean mentionsPatternAnalysis,
                                      boolean mentionsRewrite,
                                      boolean mentionsBookMultiplicity,
                                      boolean focusedEdit,
                                      int knowledgeToolCount,
                                      boolean hasSourceEvidence,
                                      boolean hasMaterialDiff,
                                      boolean validationPassed,
                                      boolean retrievedSourceEvidence,
                                      boolean citedRetrievedSources) {
        String normalizedItem = normalizeText(item);
        if (normalizedItem.isBlank()) {
            return false;
        }
        if (normalizedPreview.contains(normalizedItem)) {
            return true;
        }
        if (containsAny(normalizedItem, "\u4e0d\u4fee\u6539\u6587\u4ef6")) {
            return writeToolCount == 0;
        }
        if (containsAny(normalizedItem, "\u8f93\u51fa\u6d89\u53ca\u6587\u4ef6", "\u76f8\u5173\u6587\u4ef6")) {
            return mentionsFiles;
        }
        if (containsAny(normalizedItem, "\u6807\u6ce8\u4e0d\u786e\u5b9a\u70b9", "\u6b8b\u4f59\u98ce\u9669")) {
            return mentionsRisk;
        }
        if (containsAny(normalizedItem, "\u5148\u8f93\u51fa\u8ba1\u5212", "\u5148\u7ed9\u51fa\u8ba1\u5212")) {
            return planEvidence;
        }
        if (containsAny(normalizedItem, "\u53ea\u4fee\u6539\u76f8\u5173\u6587\u4ef6", "\u6709\u9650\u8303\u56f4\u4fee\u6539")) {
            return focusedEdit;
        }
        boolean itemNeedsDiff = containsAny(normalizedItem, "diff", "\u6539\u52a8", "\u4fee\u6539");
        boolean itemNeedsValidation = containsAny(normalizedItem, "\u6d4b\u8bd5\u7ed3\u679c", "\u7f16\u8bd1", "\u8bca\u65ad");
        if (itemNeedsDiff && itemNeedsValidation) {
            return (mentionsDiff || hasMaterialDiff) && (validationToolCount > 0 || validationPassed);
        }
        if (itemNeedsValidation) {
            return validationToolCount > 0 || validationPassed;
        }
        if (itemNeedsDiff) {
            return mentionsDiff || hasMaterialDiff;
        }
        if (containsAny(normalizedItem, "\u524d\u7aef\u4e0e\u540e\u7aef\u5951\u7ea6", "\u5951\u7ea6")) {
            return mentionsContract;
        }
        if (containsAny(normalizedItem, "\u4f7f\u7528\u4e03\u5e74\u7ea7\u4e0a\u518c\u77e5\u8bc6\u5e93", "\u77e5\u8bc6\u5e93")) {
            return retrievedSourceEvidence || mentionsSources || knowledgeToolCount > 0 || hasSourceEvidence;
        }
        if (containsAny(normalizedItem, "\u6765\u6e90\u4f9d\u636e", "\u547d\u9898\u4f9d\u636e", "\u6750\u6599\u4f9d\u636e")) {
            return mentionsSourceBasis || citedRetrievedSources;
        }
        if (containsAny(normalizedItem, "\u77e5\u8bc6\u5e93", "\u6765\u6e90", "\u4f9d\u636e", "\u8303\u9898")) {
            return retrievedSourceEvidence || mentionsSources || knowledgeToolCount > 0 || hasSourceEvidence;
        }
        if (containsAny(normalizedItem, "\u5f15\u7528\u6765\u6e90", "\u5f15\u7528\u68c0\u7d22\u6765\u6e90")) {
            return citedRetrievedSources;
        }
        if (containsAny(normalizedItem, "\u8d28\u91cf\u5ba1\u6838", "\u5ba1\u6838\u6e05\u5355", "\u6765\u6e90\u68c0\u67e5", "\u91cd\u590d\u68c0\u67e5")) {
            return mentionsQualityReview;
        }
        if (containsAny(normalizedItem, "\u7b54\u6848", "\u8bc4\u5206\u6807\u51c6", "\u91c7\u5206")) {
            return mentionsAnswerRubric;
        }
        if (containsAny(normalizedItem, "\u6807\u6ce8\u96be\u5ea6\u548c\u8003\u70b9", "\u9898\u578b", "\u5206\u503c")) {
            return mentionsQuestionMeta;
        }
        if (containsAny(normalizedItem, "\u4eba\u7269", "\u60c5\u8282", "\u96be\u5ea6", "\u8003\u70b9", "\u57fa\u7840", "\u63d0\u5347", "\u62d3\u5c55")) {
            return mentionsCoverage;
        }
        if (containsAny(normalizedItem, "\u89c4\u5f8b\u5206\u6790")) {
            return mentionsPatternAnalysis;
        }
        if (containsAny(normalizedItem, "\u4eff\u5199")) {
            return mentionsRewrite;
        }
        if (containsAny(normalizedItem, "\u81f3\u5c11\u8986\u76d6\u4e24\u90e8\u540d\u8457")) {
            return mentionsBookMultiplicity;
        }
        List<String> keywords = extractKeywords(normalizedItem);
        if (keywords.isEmpty()) {
            return false;
        }
        int matchedKeywords = 0;
        for (String keyword : keywords) {
            if (normalizedPreview.contains(keyword)) {
                matchedKeywords++;
            }
        }
        return matchedKeywords >= Math.min(keywords.size(), keywords.size() >= 3 ? 2 : 1);
    }

    private static boolean isReadTool(String toolName) {
        return containsAny(toolName, "read_file", "grep_search", "semantic_search", "file_search", "list_dir",
                "list_code_usages", "fetch_webpage", "get_errors", "github_repo",
                "workspace.tree", "workspace.glob", "workspace.grep", "workspace.read_snippet",
                "git.status", "git.diff");
    }

    private static boolean isKnowledgeTool(String toolName) {
        return toolName != null && (toolName.startsWith("wiki_") || toolName.startsWith("kb_"));
    }

    private static boolean isWriteTool(String toolName) {
        return containsAny(toolName, "apply_patch", "create_file", "delete", "edit_",
            "create_directory", "write", "workspace.write_patch");
    }

    private static boolean isValidationTool(String toolName, Map<String, Object> metadata) {
        if (containsAny(toolName, "get_errors", "run_task", "run_in_terminal", "run_notebook_cell", "test", "lint", "build",
                "command.run.readonly", "command.run.approval")) {
            return true;
        }
        String arguments = normalizeText(asString(metadata.get("arguments"), ""));
        String result = normalizeText(asString(metadata.get("result"), ""));
        return containsAny(arguments, "vue-tsc", "test", "lint", "build", "compile", "diagnostic", "\u8bca\u65ad", "\u7f16\u8bd1")
                || containsAny(result, "vue-tsc", "test", "lint", "build", "compile", "diagnostic", "\u8bca\u65ad", "\u7f16\u8bd1");
    }

    private static void collectFileEvidence(Set<String> touchedFiles, Object payload) {
        if (payload == null) {
            return;
        }
        String text = String.valueOf(payload);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[A-Za-z0-9_./-]+\\.(vue|ts|tsx|js|jsx|java|md|json|yml|yaml|xml)")
                .matcher(text);
        while (matcher.find()) {
            touchedFiles.add(matcher.group());
            if (touchedFiles.size() >= 8) {
                return;
            }
        }
    }

    private static void collectValidationEvidence(HarnessToolInvocation invocation,
                                                  List<String> validationCommands,
                                                  List<String> validationResults) {
        if (invocation == null) {
            return;
        }
        String toolName = normalizeText(invocation.getToolName());
        String arguments = asString(invocation.getMetadata().get("arguments"), "");
        String result = asString(invocation.getMetadata().get("result"), "");
        String normalizedArguments = normalizeText(arguments);
        String normalizedResult = normalizeText(result);
        boolean validationLike = isValidationTool(toolName, invocation.getMetadata())
                || containsAny(normalizedArguments, "vue-tsc", "test", "lint", "build", "compile", "diagnostic", "mvn", "pnpm", "npm run")
                || containsAny(normalizedResult, "no errors found", "exit code", "tests passed", "build succeeded", "\u7f16\u8bd1", "\u8bca\u65ad");
        if (!validationLike) {
            return;
        }
        String command = extractValidationCommand(arguments, toolName);
        if (command != null && !command.isBlank() && validationCommands.stream().noneMatch(command::equals)) {
            validationCommands.add(command);
        }
        String outcome = extractValidationOutcome(result, invocation.getStatus());
        if (outcome != null && !outcome.isBlank() && validationResults.stream().noneMatch(outcome::equals)) {
            validationResults.add(outcome);
        }
    }

    private static void collectPatchEvidence(HarnessToolInvocation invocation, PatchEvidence patchEvidence) {
        if (invocation == null || patchEvidence == null) {
            return;
        }
        String toolName = normalizeText(invocation.getToolName());
        String arguments = asString(invocation.getMetadata().get("arguments"), "");
        if (containsAny(toolName, "apply_patch")) {
            parseApplyPatch(arguments, patchEvidence);
            return;
        }
        if (containsAny(toolName, "workspace.write_patch")) {
            String filePath = asString(invocation.getMetadata().get("filePath"), "");
            if (filePath.isBlank()) {
                filePath = extractFilePath(arguments, "filePath", "path");
            }
            if (filePath != null && !filePath.isBlank()) {
                if (Boolean.TRUE.equals(invocation.getMetadata().get("created"))) {
                    patchEvidence.recordAddedFile(filePath);
                } else {
                    patchEvidence.recordUpdatedFile(filePath);
                }
            }
            Object operationsApplied = invocation.getMetadata().get("operationsApplied");
            if (operationsApplied instanceof Number number) {
                for (int i = 0; i < Math.max(1, number.intValue()); i++) {
                    patchEvidence.incrementHunkCount();
                }
            } else if (operationsApplied instanceof Collection<?> collection) {
                int size = Math.max(1, collection.size());
                for (int i = 0; i < size; i++) {
                    patchEvidence.incrementHunkCount();
                }
            } else if (operationsApplied instanceof Map<?, ?> map && !map.isEmpty()) {
                for (int i = 0; i < map.size(); i++) {
                    patchEvidence.incrementHunkCount();
                }
            }
            return;
        }
        if (containsAny(toolName, "create_file")) {
            String filePath = extractFilePath(arguments, "filepath", "filePath");
            if (filePath != null) {
                patchEvidence.recordAddedFile(filePath);
            }
            return;
        }
        if (containsAny(toolName, "delete")) {
            String filePath = extractFilePath(arguments, "filepath", "filePath", "path");
            if (filePath != null) {
                patchEvidence.recordDeletedFile(filePath);
            }
        }
    }

    private static void parseApplyPatch(String arguments, PatchEvidence patchEvidence) {
        if (arguments == null || arguments.isBlank()) {
            return;
        }
        String currentAction = null;
        for (String rawLine : arguments.split("\\r?\\n")) {
            String line = rawLine != null ? rawLine : "";
            String trimmed = line.trim();
            if (trimmed.startsWith("*** Update File:")) {
                currentAction = "update";
                String filePath = trimmed.substring("*** Update File:".length()).trim();
                patchEvidence.recordUpdatedFile(filePath);
                continue;
            }
            if (trimmed.startsWith("*** Add File:")) {
                currentAction = "add";
                String filePath = trimmed.substring("*** Add File:".length()).trim();
                patchEvidence.recordAddedFile(filePath);
                continue;
            }
            if (trimmed.startsWith("*** Delete File:")) {
                currentAction = "delete";
                String filePath = trimmed.substring("*** Delete File:".length()).trim();
                patchEvidence.recordDeletedFile(filePath);
                continue;
            }
            if (trimmed.startsWith("@@")) {
                patchEvidence.incrementHunkCount();
                continue;
            }
            if (line.startsWith("+++") || line.startsWith("---")) {
                continue;
            }
            if (line.startsWith("+") && !"delete".equals(currentAction)) {
                patchEvidence.incrementAddedLines();
                continue;
            }
            if (line.startsWith("-") && !"add".equals(currentAction)) {
                patchEvidence.incrementRemovedLines();
            }
        }
    }

    private static String extractFilePath(String text, String... fieldNames) {
        if (text == null || text.isBlank() || fieldNames == null || fieldNames.length == 0) {
            return null;
        }
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)" + java.util.regex.Pattern.quote(fieldName) + "\\s*[=:]\\s*['\"]?([^,'\"}\\]]+)")
                    .matcher(text);
            if (matcher.find()) {
                String filePath = matcher.group(1).trim();
                if (!filePath.isEmpty()) {
                    return filePath;
                }
            }
        }
        return null;
    }

    private String resolveDesktopInvocationId(DesktopLocalToolPayload payload) {
        String approvalId = resolveDesktopApprovalId(payload);
        if (approvalId != null) {
            return approvalId;
        }
        String requestId = asString(safeMap(payload.getEvidence()).get("requestId"), "");
        if (!requestId.isBlank()) {
            return requestId;
        }
        String filePath = asString(safeMap(payload.getEvidence()).get("filePath"), "");
        if (!filePath.isBlank()) {
            return payload.getToolName() + ":" + filePath;
        }
        return payload.getToolName() + ":" + nonBlank(payload.getTimestamp(), UUID.randomUUID().toString());
    }

    private String resolveDesktopApprovalId(DesktopLocalToolPayload payload) {
        String requestId = asString(safeMap(payload.getApproval()).get("requestId"), "");
        if (!requestId.isBlank()) {
            return requestId;
        }
        requestId = asString(safeMap(payload.getEvidence()).get("requestId"), "");
        return requestId.isBlank() ? null : requestId;
    }

    private LocalDateTime parseDesktopLocalTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(timestamp).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(timestamp);
            } catch (Exception ignoredAgain) {
                return LocalDateTime.now();
            }
        }
    }

    private Map<String, Object> toPayloadMap(DesktopLocalToolPayload payload) {
        return objectMapper.convertValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private static Map<String, Object> safeMap(Map<String, Object> value) {
        return value != null ? value : Map.of();
    }

    private static String buildDesktopArguments(DesktopLocalToolPayload payload) {
        Map<String, Object> evidence = safeMap(payload.getEvidence());
        if (containsAny(normalizeText(payload.getToolName()), "command.run")) {
            String command = asString(evidence.get("command"), "");
            List<String> args = toStringList(evidence.get("args"));
            return (command + " " + String.join(" ", args)).trim();
        }
        String filePath = asString(evidence.get("filePath"), "");
        if (!filePath.isBlank()) {
            return "filePath=" + filePath;
        }
        return asString(payload.getSummary(), payload.getToolName());
    }

    private static String buildDesktopResult(DesktopLocalToolPayload payload) {
        Map<String, Object> evidence = safeMap(payload.getEvidence());
        List<String> parts = new ArrayList<>();
        if (payload.getSummary() != null && !payload.getSummary().isBlank()) {
            parts.add(payload.getSummary());
        }
        if (evidence.containsKey("filePath")) {
            parts.add("filePath=" + evidence.get("filePath"));
        }
        if (evidence.containsKey("exitCode")) {
            parts.add("exit code: " + evidence.get("exitCode"));
        }
        if (evidence.containsKey("changeCount")) {
            parts.add("change count=" + evidence.get("changeCount"));
        }
        if (Boolean.TRUE.equals(payload.getRequiresApproval())) {
            parts.add("approval=" + asString(safeMap(payload.getApproval()).get("state"), payload.getStatus()));
        }
        if (parts.isEmpty()) {
            parts.add(nonBlank(payload.getStatus(), "completed"));
        }
        return String.join(" | ", parts);
    }

    private static String mapDesktopInvocationStatus(DesktopLocalToolPayload payload) {
        String approvalState = normalizeText(asString(safeMap(payload.getApproval()).get("state"), ""));
        String status = normalizeText(payload.getStatus());
        String mode = normalizeText(payload.getMode());
        if ("pending".equals(approvalState) || "approval-required".equals(mode)) {
            return "awaiting_approval";
        }
        if ("approved".equals(approvalState) || "approved".equals(mode)) {
            return "approved";
        }
        if (containsAny(status, "failed", "error", "denied", "blocked")) {
            return "failed";
        }
        if (containsAny(status, "running", "in_progress")) {
            return "running";
        }
        return "completed";
    }

    private static String mapDesktopApprovalStatus(DesktopLocalToolPayload payload) {
        String approvalState = normalizeText(asString(safeMap(payload.getApproval()).get("state"), ""));
        if (!approvalState.isBlank()) {
            return approvalState;
        }
        return Boolean.TRUE.equals(payload.getRequiresApproval()) ? "pending" : "completed";
    }

    private static void collectKnowledgeCitationEvidence(HarnessToolInvocation invocation, SourceCitationEvidence citationEvidence) {
        if (invocation == null || citationEvidence == null) {
            return;
        }
        String toolName = normalizeText(invocation.getToolName());
        if (!isKnowledgeTool(toolName)) {
            return;
        }
        String arguments = asString(invocation.getMetadata().get("arguments"), "");
        String result = asString(invocation.getMetadata().get("result"), "");
        if (containsAny(toolName, "trace_source")) {
            extractJsonStringValues(result, "pageTitle").forEach(citationEvidence::addPageTitle);
            extractJsonStringValues(result, "pageSlug").forEach(citationEvidence::addPageSlug);
            extractJsonStringValues(result, "title", "name", "path").forEach(citationEvidence::addSourceFile);
            return;
        }
        if (containsAny(toolName, "semantic_search")) {
            extractJsonStringValues(result, "rawTitle").forEach(citationEvidence::addRawTitle);
            extractJsonStringValues(result, "section").forEach(citationEvidence::addSection);
            extractJsonIntegerValues(result, "pageNumber").stream()
                    .map(pageNumber -> "page=" + pageNumber)
                    .forEach(citationEvidence::addSection);
            return;
        }
        if (containsAny(toolName, "read")) {
            extractJsonStringValues(result, "title").forEach(citationEvidence::addPageTitle);
            extractJsonStringValues(result, "slug").forEach(citationEvidence::addPageSlug);
            extractJsonStringValues(result, "sourceFiles", "title", "name", "path").forEach(citationEvidence::addSourceFile);
            extractJsonStringValues(arguments, "slug").forEach(citationEvidence::addPageSlug);
            return;
        }
        if (containsAny(toolName, "search", "list")) {
            extractJsonStringValues(result, "title").forEach(citationEvidence::addPageTitle);
            extractJsonStringValues(result, "slug").forEach(citationEvidence::addPageSlug);
        }
    }

    private static void collectSourceEvidence(String preview, Collection<String> sourceEvidence) {
        if (preview == null || preview.isBlank()) {
            return;
        }
        Set<String> titles = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher titleMatcher = java.util.regex.Pattern.compile("\u300a([^\u300b]{1,20})\u300b").matcher(preview);
        while (titleMatcher.find() && titles.size() < 4) {
            titles.add("book=" + titleMatcher.group(1));
        }
        sourceEvidence.addAll(titles);
        if (containsAny(normalizeText(preview), "\u77e5\u8bc6\u5e93")) {
            sourceEvidence.add("kb referenced");
        }
        if (containsAny(normalizeText(preview), "\u4e0a\u4f20\u6750\u6599", "\u6750\u6599")) {
            sourceEvidence.add("uploaded material referenced");
        }
        if (containsAny(normalizeText(preview), "\u8303\u9898")) {
            sourceEvidence.add("sample exam referenced");
        }
    }

    private static List<String> extractJsonStringValues(String text, String... fieldNames) {
        if (text == null || text.isBlank() || fieldNames == null || fieldNames.length == 0) {
            return List.of();
        }
        Set<String> values = new java.util.LinkedHashSet<>();
        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                    .matcher(text);
            while (matcher.find() && values.size() < 12) {
                String value = unescapeJsonString(matcher.group(1)).trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private static List<Integer> extractJsonIntegerValues(String text, String fieldName) {
        if (text == null || text.isBlank() || fieldName == null || fieldName.isBlank()) {
            return List.of();
        }
        Set<Integer> values = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*(\\d+)")
                .matcher(text);
        while (matcher.find() && values.size() < 12) {
            try {
                values.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // ignore malformed numeric capture
            }
        }
        return List.copyOf(values);
    }

    private static String unescapeJsonString(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace("\\\\", "\\")
                .trim();
    }

    private static String extractValidationCommand(String arguments, String fallbackToolName) {
        if (arguments == null || arguments.isBlank()) {
            return containsAny(fallbackToolName, "get_errors") ? "get_errors" : null;
        }
        for (String line : arguments.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String normalized = normalizeText(trimmed);
            if (containsAny(normalized, "vue-tsc", "test", "lint", "build", "compile", "diagnostic", "mvn", "pnpm", "npm run", "get_errors")) {
                return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
            }
        }
        String compact = arguments.trim();
        return compact.length() > 120 ? compact.substring(0, 120) + "..." : compact;
    }

    private static String extractValidationOutcome(String result, String status) {
        String normalizedStatus = normalizeText(status);
        if (result != null && !result.isBlank()) {
            for (String line : result.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String normalized = normalizeText(trimmed);
                if (containsAny(normalized, "no errors found", "exit code", "passed", "failed", "error", "\u7f16\u8bd1", "\u8bca\u65ad", "\u901a\u8fc7", "\u5931\u8d25")) {
                    return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
                }
            }
        }
        if (containsAny(normalizedStatus, "completed")) {
            return "completed";
        }
        if (containsAny(normalizedStatus, "failed")) {
            return "failed";
        }
        return null;
    }

    private static Integer extractExitCode(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(?:\\\"exitCode\\\"\\s*:\\s*|exit code\\s*[:=]?\\s*)(\\d+)")
                .matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return List.of();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace('\uff08', ' ')
                .replace('\uff09', ' ')
                .replace('\uff0c', ' ')
                .replace('\u3002', ' ')
                .replace('\uff1a', ' ')
                .replace(':', ' ')
                .replace('\u3001', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace("`", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> ignored = Set.of("\u8f93\u51fa", "\u5b8c\u6574", "\u76f8\u5173", "\u8bb0\u5f55", "\u603b\u7ed3",
                "\u81f3\u5c11", "\u6bcf\u9898", "\u518d", "\u5148");
        return java.util.Arrays.stream(text.split("[\\s/|,+-]+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .filter(token -> token.length() >= 2)
                .filter(token -> !ignored.contains(token))
                .distinct()
                .limit(5)
                .toList();
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null || text.isBlank() || needles == null || needles.length == 0) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static int countOccurrences(String text, String needle) {
        if (text == null || text.isBlank() || needle == null || needle.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static final class PatchEvidence {
        private final Set<String> updatedFiles = new java.util.LinkedHashSet<>();
        private final Set<String> addedFiles = new java.util.LinkedHashSet<>();
        private final Set<String> deletedFiles = new java.util.LinkedHashSet<>();
        private int hunkCount;
        private int addedLines;
        private int removedLines;

        void recordUpdatedFile(String filePath) {
            String normalized = normalizePath(filePath);
            if (normalized != null) {
                updatedFiles.add(normalized);
            }
        }

        void recordAddedFile(String filePath) {
            String normalized = normalizePath(filePath);
            if (normalized != null) {
                addedFiles.add(normalized);
            }
        }

        void recordDeletedFile(String filePath) {
            String normalized = normalizePath(filePath);
            if (normalized != null) {
                deletedFiles.add(normalized);
            }
        }

        void incrementHunkCount() {
            hunkCount++;
        }

        void incrementAddedLines() {
            addedLines++;
        }

        void incrementRemovedLines() {
            removedLines++;
        }

        boolean hasPatchEvidence() {
            return !updatedFiles.isEmpty() || !addedFiles.isEmpty() || !deletedFiles.isEmpty();
        }

        boolean hasMaterialDiff() {
            return hasPatchEvidence() || addedLines > 0 || removedLines > 0;
        }

        Set<String> changedFiles() {
            Set<String> files = new java.util.LinkedHashSet<>();
            files.addAll(updatedFiles);
            files.addAll(addedFiles);
            files.addAll(deletedFiles);
            return files;
        }

        int hunkCount() {
            return hunkCount;
        }

        int addedLines() {
            return addedLines;
        }

        int removedLines() {
            return removedLines;
        }

        private static String normalizePath(String filePath) {
            if (filePath == null) {
                return null;
            }
            String normalized = filePath.trim().replace('\\', '/');
            return normalized.isEmpty() ? null : normalized;
        }
    }

    private static final class SourceCitationEvidence {
        private final Set<String> pageTitles = new java.util.LinkedHashSet<>();
        private final Set<String> pageSlugs = new java.util.LinkedHashSet<>();
        private final Set<String> rawTitles = new java.util.LinkedHashSet<>();
        private final Set<String> sourceFiles = new java.util.LinkedHashSet<>();
        private final Set<String> sections = new java.util.LinkedHashSet<>();

        void addPageTitle(String title) {
            addNormalized(pageTitles, title);
        }

        void addPageSlug(String slug) {
            addNormalized(pageSlugs, slug);
        }

        void addRawTitle(String title) {
            addNormalized(rawTitles, title);
        }

        void addSourceFile(String sourceFile) {
            addNormalized(sourceFiles, sourceFile);
        }

        void addSection(String section) {
            addNormalized(sections, section);
        }

        boolean hasRetrievedSources() {
            return !pageTitles.isEmpty() || !rawTitles.isEmpty() || !sourceFiles.isEmpty() || !sections.isEmpty();
        }

        int sourceCount() {
            return pageTitles.size() + rawTitles.size() + sourceFiles.size();
        }

        List<String> citedSources(String preview) {
            if (preview == null || preview.isBlank()) {
                return List.of();
            }
            String normalizedPreview = normalizeText(preview);
            Set<String> cited = new java.util.LinkedHashSet<>();
            collectCitedEntries(cited, normalizedPreview, pageTitles, "page=");
            collectCitedEntries(cited, normalizedPreview, rawTitles, "raw=");
            collectCitedEntries(cited, normalizedPreview, sourceFiles, "file=");
            return List.copyOf(cited);
        }

        List<String> retrievedTitles() {
            Set<String> entries = new java.util.LinkedHashSet<>();
            pageTitles.stream().limit(4).forEach(title -> entries.add("page=" + title));
            rawTitles.stream().limit(4).forEach(title -> entries.add("raw=" + title));
            return List.copyOf(entries);
        }

        List<String> sourceFiles() {
            return List.copyOf(sourceFiles.stream().limit(6).toList());
        }

        List<String> sections() {
            return List.copyOf(sections.stream().limit(6).toList());
        }

        void appendSummaryTo(Set<String> output) {
            if (output == null) {
                return;
            }
            pageTitles.stream().limit(3).forEach(title -> output.add("page=" + title));
            rawTitles.stream().limit(3).forEach(title -> output.add("raw=" + title));
            sourceFiles.stream().limit(2).forEach(file -> output.add("file=" + file));
            sections.stream().limit(2).forEach(section -> output.add("section=" + section));
        }

        private static void collectCitedEntries(Set<String> cited,
                                                String normalizedPreview,
                                                Set<String> candidates,
                                                String prefix) {
            for (String candidate : candidates) {
                if (cited.size() >= 6) {
                    return;
                }
                String normalized = normalizeText(candidate);
                String basename = normalizeText(extractBasename(candidate));
                if ((!normalized.isEmpty() && normalizedPreview.contains(normalized))
                        || (!basename.isEmpty() && basename.length() >= 3 && normalizedPreview.contains(basename))) {
                    cited.add(prefix + candidate);
                }
            }
        }

        private static String extractBasename(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            String normalized = value.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            return slash >= 0 ? normalized.substring(slash + 1) : normalized;
        }

        private static void addNormalized(Set<String> target, String value) {
            if (target == null || value == null) {
                return;
            }
            String normalized = value.trim();
            if (!normalized.isEmpty()) {
                target.add(normalized);
            }
        }
    }

    private void hydrateApprovals(HarnessRun run) {
        if (run == null || run.getApprovals().isEmpty()) return;
        List<String> pendingIds = run.getApprovals().stream()
                .map(HarnessApproval::getId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        if (pendingIds.isEmpty()) return;
        List<ToolApprovalEntity> entities = toolApprovalMapper.selectList(
                new LambdaQueryWrapper<ToolApprovalEntity>()
                        .in(ToolApprovalEntity::getPendingId, pendingIds)
                        .eq(ToolApprovalEntity::getDeleted, 0));
        if (entities.isEmpty()) return;
        Map<String, ToolApprovalEntity> byPendingId = entities.stream()
                .collect(Collectors.toMap(ToolApprovalEntity::getPendingId, entity -> entity, (left, right) -> left));
        for (HarnessApproval approval : run.getApprovals()) {
            ToolApprovalEntity entity = byPendingId.get(approval.getId());
            if (entity == null) continue;
            approval.setStatus(entity.getStatus() != null ? entity.getStatus().toLowerCase(Locale.ROOT) : approval.getStatus());
            approval.setResolvedBy(entity.getResolvedBy());
            approval.setScope(entity.getGrantScope());
            if (entity.getResolvedAt() != null) {
                approval.setResolvedAt(entity.getResolvedAt());
            }
            if (entity.getCreatedAt() != null && approval.getRequestedAt() == null) {
                approval.setRequestedAt(entity.getCreatedAt());
            }
            approval.getMetadata().put("dbStatus", entity.getStatus());
            approval.getMetadata().put("summary", entity.getSummary());
            approval.getMetadata().put("maxSeverity", entity.getMaxSeverity());
        }
    }

    private static String asString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        String text = String.valueOf(value);
        return text.isBlank() ? defaultValue : text;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String nonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static LocalDateTime toLocalDateTime(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }
}
