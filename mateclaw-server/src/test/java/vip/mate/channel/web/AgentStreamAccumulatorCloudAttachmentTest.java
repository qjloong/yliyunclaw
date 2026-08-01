package vip.mate.channel.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.agent.AgentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStreamAccumulatorCloudAttachmentTest {

    @Test
    void persistsCloudReadAsVisualSegmentButNotProviderToolCall() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> broadcasts = new ArrayList<>();
        AgentStreamAccumulator accumulator = new AgentStreamAccumulator(
                objectMapper,
                new AgentStreamAccumulator.Sink() {
                    @Override
                    public void broadcast(String conversationId, String eventName, Object payload) {
                        broadcasts.add(eventName);
                    }

                    @Override
                    public void updatePhase(String conversationId, String phase) {
                    }
                });

        accumulator.accept(AgentService.StreamDelta.event(
                "cloud_attachment_started",
                Map.of(
                        "toolCallId", "cloud-1",
                        "toolName", "yliyun_attachment_read",
                        "arguments", "{\"fileId\":17485}")),
                "conv-1");
        accumulator.accept(AgentService.StreamDelta.event(
                "cloud_attachment_completed",
                Map.of(
                        "toolCallId", "cloud-1",
                        "toolName", "yliyun_attachment_read",
                        "result", "已读取项目文档.md",
                        "success", true)),
                "conv-1");

        JsonNode metadata = objectMapper.readTree(accumulator.toMetadataJson());
        JsonNode segment = metadata.path("segments").get(0);
        assertThat(segment.path("type").asText()).isEqualTo("tool_call");
        assertThat(segment.path("status").asText()).isEqualTo("completed");
        assertThat(segment.path("toolName").asText()).isEqualTo("yliyun_attachment_read");
        assertThat(metadata.has("toolCalls")).isFalse();
        assertThat(accumulator.toAssistantParts()).isEmpty();
        assertThat(broadcasts).containsExactly(
                "cloud_attachment_started",
                "cloud_attachment_completed");
    }
}
