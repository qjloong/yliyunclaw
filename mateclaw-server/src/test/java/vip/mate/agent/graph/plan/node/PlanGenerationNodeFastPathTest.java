package vip.mate.agent.graph.plan.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import vip.mate.agent.AgentToolSet;
import vip.mate.agent.GraphEventPublisher;
import vip.mate.agent.graph.NodeStreamingChatHelper;
import vip.mate.planning.model.PlanEntity;
import vip.mate.planning.service.PlanningService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vip.mate.agent.graph.plan.state.PlanStateKeys.DIRECT_ANSWER;
import static vip.mate.agent.graph.plan.state.PlanStateKeys.GOAL;
import static vip.mate.agent.graph.plan.state.PlanStateKeys.NEEDS_PLANNING;
import static vip.mate.agent.graph.plan.state.PlanStateKeys.PLAN_ID;
import static vip.mate.agent.graph.plan.state.PlanStateKeys.PLAN_STEPS;
import static vip.mate.agent.graph.state.MateClawStateKeys.CONVERSATION_ID;
import static vip.mate.agent.graph.state.MateClawStateKeys.PENDING_EVENTS;
import static vip.mate.agent.graph.state.MateClawStateKeys.SYSTEM_PROMPT;
import static vip.mate.agent.graph.state.MateClawStateKeys.TRACE_ID;

class PlanGenerationNodeFastPathTest {

    private ChatModel chatModel;
    private PlanningService planningService;
    private NodeStreamingChatHelper streamingHelper;
    private AgentToolSet toolSet;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        planningService = mock(PlanningService.class);
        streamingHelper = mock(NodeStreamingChatHelper.class);
        toolSet = mock(AgentToolSet.class);
        when(toolSet.callbacks()).thenReturn(List.of());
    }

    @Test
    @DisplayName("Simple request skips silent triage and falls into a single-step plan")
    void simpleRequest_skipsSilentTriage() throws Exception {
        PlanEntity plan = new PlanEntity();
        plan.setId(123L);
        when(planningService.createPlan("agent-1", "1+1", List.of("1+1"))).thenReturn(plan);

        Map<String, Object> output = createNode().apply(buildState("1+1"));

        verify(streamingHelper, never()).streamCallSilent(any(), any(), anyString(), anyString());
        verify(planningService).createPlan("agent-1", "1+1", List.of("1+1"));
        assertEquals(true, output.get(NEEDS_PLANNING));
        assertEquals(123L, output.get(PLAN_ID));
        assertEquals(List.of("1+1"), output.get(PLAN_STEPS));
        @SuppressWarnings("unchecked")
        List<GraphEventPublisher.GraphEvent> events =
                (List<GraphEventPublisher.GraphEvent>) output.get(PENDING_EVENTS);
        assertTrue(events.stream().anyMatch(event -> GraphEventPublisher.EVENT_PERF_SUMMARY.equals(event.type())
                && Boolean.TRUE.equals(event.data().get("triage_skipped"))),
                "fast path should emit a triage_skipped perf event");
    }

    @Test
    @DisplayName("Explicit multi-step request still goes through silent triage")
    void explicitMultiStepRequest_stillRunsSilentTriage() throws Exception {
        String triageJson = "{\"needs_planning\":false,\"direct_answer\":\"好的\"}";
        NodeStreamingChatHelper.StreamResult result = new NodeStreamingChatHelper.StreamResult(
                triageJson,
                "",
                new AssistantMessage(triageJson),
                List.of(),
                false,
                20,
                8
        );
        when(streamingHelper.streamCallSilent(any(), any(), eq("conv-1"), eq("plan_generation")))
                .thenReturn(result);

        Map<String, Object> output = createNode().apply(buildState("先查 A 再查 B 然后对比差异"));

        verify(streamingHelper).broadcastProgress("conv-1", "分析中...");
        verify(streamingHelper).streamCallSilent(any(), any(), eq("conv-1"), eq("plan_generation"));
        verify(streamingHelper).broadcastContent("conv-1", "好的");
        verify(planningService, never()).createPlan(anyString(), anyString(), any());
        assertEquals(false, output.get(NEEDS_PLANNING));
        assertEquals("好的", output.get(DIRECT_ANSWER));
    }

    private PlanGenerationNode createNode() {
        return new PlanGenerationNode(chatModel, planningService, streamingHelper, null, toolSet);
    }

    private OverAllState buildState(String goal) {
        Map<String, Object> map = new HashMap<>();
        map.put(GOAL, goal);
        map.put(CONVERSATION_ID, "conv-1");
        map.put(TRACE_ID, "agent-1");
        map.put(SYSTEM_PROMPT, "you are a helpful assistant");
        return new OverAllState(map);
    }
}
