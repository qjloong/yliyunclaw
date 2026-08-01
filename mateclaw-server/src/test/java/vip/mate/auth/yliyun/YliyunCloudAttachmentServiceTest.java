package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.tool.mcp.model.McpServerEntity;
import vip.mate.tool.mcp.runtime.McpClientManager;
import vip.mate.tool.mcp.service.McpServerService;
import vip.mate.workspace.conversation.model.MessageContentPart;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YliyunCloudAttachmentServiceTest {

    @Mock
    private McpServerService mcpServerService;
    @Mock
    private McpClientManager mcpClientManager;

    private YliyunCloudAttachmentService service;
    private ChatOrigin origin;

    @BeforeEach
    void setUp() {
        service = new YliyunCloudAttachmentService(
                mcpServerService, mcpClientManager, new ObjectMapper());
        origin = ChatOrigin.web("conv-1", "yliyun_1_100", 9L, null, null, 42L);
    }

    @Test
    void readsCloudFileDeterministicallyAndPersistsContentOnPart() {
        McpServerEntity server = new McpServerEntity();
        server.setId(7L);
        when(mcpServerService.getByName("yliyun-mcp")).thenReturn(server);
        when(mcpClientManager.callTool(eq(7L), eq("file.read"), any(), any()))
                .thenReturn(McpClientManager.ToolCallResult.success(
                        """
                        {"content":"项目目标：完成云盘与智能助手集成。","fileName":"项目文档.md","charCount":19,"truncated":false}
                        """, 12));

        MessageContentPart file = cloudFile("17485", "项目文档.md");
        service.enrich(List.of(MessageContentPart.text("总结关键要点"), file), origin);

        assertThat(file.getCaption()).contains("完成云盘与智能助手集成");
        verify(mcpClientManager).callTool(eq(7L), eq("file.read"), any(), any());
    }

    @Test
    void surfacesMcpFailureInsteadOfLettingModelGuess() {
        McpServerEntity server = new McpServerEntity();
        server.setId(7L);
        when(mcpServerService.getByName("yliyun-mcp")).thenReturn(server);
        when(mcpClientManager.callTool(eq(7L), eq("file.read"), any(), any()))
                .thenReturn(McpClientManager.ToolCallResult.failure(
                        "MCP_TOOL_ERROR", "没有文件读取权限", "mcp.tool", 8));

        MessageContentPart file = cloudFile("17485", "项目文档.md");

        assertThatThrownBy(() -> service.enrich(List.of(file), origin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("项目文档.md")
                .hasMessageContaining("没有文件读取权限");
        assertThat(file.getCaption()).isNull();
    }

    @Test
    void reportsDeterministicReadAsUiTraceWithoutExposingFileContent() {
        McpServerEntity server = new McpServerEntity();
        server.setId(7L);
        when(mcpServerService.getByName("yliyun-mcp")).thenReturn(server);
        when(mcpClientManager.callTool(eq(7L), eq("file.read"), any(), any()))
                .thenReturn(McpClientManager.ToolCallResult.success(
                        """
                        {"content":"仅用于模型上下文的敏感正文","fileName":"项目文档.md"}
                        """, 9));

        List<String> events = new ArrayList<>();
        MessageContentPart file = cloudFile("17485", "项目文档.md");
        service.enrich(List.of(file), origin, new YliyunCloudAttachmentService.TraceListener() {
            @Override
            public void onStarted(String toolCallId, String toolName, String arguments) {
                events.add("start:" + toolName + ":" + arguments);
            }

            @Override
            public void onCompleted(
                    String toolCallId,
                    String toolName,
                    String result,
                    boolean success) {
                events.add("complete:" + success + ":" + result);
            }
        });

        assertThat(events).hasSize(2);
        assertThat(events.get(0))
                .contains("start:yliyun_attachment_read")
                .contains("\"fileId\":17485");
        assertThat(events.get(1))
                .contains("complete:true")
                .contains("项目文档.md")
                .doesNotContain("敏感正文");
    }

    private MessageContentPart cloudFile(String id, String name) {
        MessageContentPart part = new MessageContentPart();
        part.setType("file");
        part.setFileName(name);
        part.setPath("yliyun://file/" + id);
        part.setCaption("client supplied text must be discarded");
        return part;
    }
}
