package vip.mate.auth.yliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.agent.context.ChatOrigin;
import vip.mate.tool.mcp.model.McpServerEntity;
import vip.mate.tool.mcp.runtime.McpClientManager;
import vip.mate.tool.mcp.service.McpServerService;
import vip.mate.workspace.conversation.model.MessageContentPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YliyunCloudAttachmentServiceTest {

    @Mock
    private McpServerService mcpServerService;
    @Mock
    private McpClientManager mcpClientManager;
    @Mock
    private YliyunCloudResourceRefService resourceRefService;
    @Mock
    private YliyunCloudResourceStatusService resourceStatusService;

    private YliyunCloudAttachmentService service;
    private ChatOrigin origin;

    @BeforeEach
    void setUp() {
        service = new YliyunCloudAttachmentService(
                mcpServerService, mcpClientManager, new ObjectMapper(), resourceRefService,
                resourceStatusService);
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

    @Test
    void resolvesOpaqueResourceRefBeforeCallingSharedMcpRuntime() {
        McpServerEntity server = new McpServerEntity();
        server.setId(7L);
        when(mcpServerService.getByName("yliyun-mcp")).thenReturn(server);
        when(resourceRefService.supports("yliyun-ref://opaque.signature")).thenReturn(true);
        when(resourceRefService.resolvePath("yliyun-ref://opaque.signature", origin))
                .thenReturn(new YliyunCloudResourceRefService.ResolvedResourceRef(
                        "file", "17485", "签名文档.md", null,
                        "current-preview", "text/markdown"));
        when(mcpClientManager.callTool(eq(7L), eq("file.read"), any(), any()))
                .thenReturn(McpClientManager.ToolCallResult.success(
                        "{\"content\":\"签名引用读取成功\"}", 6));

        MessageContentPart file = new MessageContentPart();
        file.setType("file");
        file.setPath("yliyun-ref://opaque.signature");
        service.enrich(List.of(file), origin);

        assertThat(file.getFileName()).isEqualTo("签名文档.md");
        assertThat(file.getContentType()).isEqualTo("text/markdown");
        assertThat(file.getCaption()).contains("签名引用读取成功");
        verify(resourceStatusService).requireCurrent(any(), eq(origin));
        verify(mcpClientManager).callTool(eq(7L), eq("file.read"), any(), any());
    }

    @Test
    void progressivelySummarizesAndReadsFiveRelevantFolderFiles() {
        McpServerEntity server = new McpServerEntity();
        server.setId(7L);
        when(mcpServerService.getByName("yliyun-mcp")).thenReturn(server);
        when(mcpClientManager.callTool(eq(7L), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String tool = invocation.getArgument(1);
                    Map<String, Object> arguments = invocation.getArgument(2);
                    if ("file.list".equals(tool)) {
                        return McpClientManager.ToolCallResult.success("""
                                {"total":8,"items":[
                                  {"id":1,"name":"例会记录.md","type":"file","size":100,"mimeType":"text/markdown","modifiedAt":"2026-07-01"},
                                  {"id":2,"name":"项目预算.xlsx","type":"file","size":200,"mimeType":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","modifiedAt":"2026-08-03"},
                                  {"id":3,"name":"风险清单.md","type":"file","size":100,"mimeType":"text/markdown","modifiedAt":"2026-08-02"},
                                  {"id":4,"name":"发布计划.md","type":"file","size":100,"mimeType":"text/markdown","modifiedAt":"2026-07-30"},
                                  {"id":5,"name":"架构设计.md","type":"file","size":100,"mimeType":"text/markdown","modifiedAt":"2026-07-29"},
                                  {"id":6,"name":"旧资料.txt","type":"file","size":100,"mimeType":"text/plain","modifiedAt":"2025-01-01"},
                                  {"id":7,"name":"临时说明.txt","type":"file","size":100,"mimeType":"text/plain","modifiedAt":"2025-01-02"},
                                  {"id":8,"name":"子目录","type":"directory","size":0,"mimeType":"","modifiedAt":"2026-08-01"}
                                ]}
                                """, 3);
                    }
                    long fileId = ((Number) arguments.get("fileId")).longValue();
                    if ("file.summarize".equals(tool)) {
                        String preview = fileId == 2 ? "项目预算与成本明细"
                                : fileId == 3 ? "项目风险、影响和缓解措施"
                                : "普通项目资料";
                        return McpClientManager.ToolCallResult.success(
                                "{\"fileName\":\"file-" + fileId
                                        + "\",\"preview\":\"" + preview
                                        + "\",\"headings\":[],\"keywords\":[]}", 2);
                    }
                    if ("file.read".equals(tool)) {
                        return McpClientManager.ToolCallResult.success(
                                "{\"content\":\"content-" + fileId + "\"}", 4);
                    }
                    throw new AssertionError("unexpected tool: " + tool);
                });

        List<String> events = new ArrayList<>();
        MessageContentPart folder = new MessageContentPart();
        folder.setType("file");
        folder.setFileName("项目资料");
        folder.setPath("yliyun://folder/200");
        service.enrich(List.of(MessageContentPart.text("分析项目预算和风险"), folder), origin,
                new YliyunCloudAttachmentService.TraceListener() {
                    @Override
                    public void onStarted(String id, String tool, String arguments) {
                        events.add("start:" + tool);
                    }

                    @Override
                    public void onCompleted(String id, String tool, String result, boolean success) {
                        events.add("complete:" + tool + ":" + success + ":" + result);
                    }
                });

        assertThat(folder.getCaption())
                .contains("文件夹目录")
                .contains("本轮按提问相关性读取的文件")
                .contains("### 项目预算.xlsx")
                .contains("### 风险清单.md")
                .contains("content-2")
                .contains("content-3")
                .doesNotContain("content-6")
                .doesNotContain("content-7");
        verify(mcpClientManager, times(7))
                .callTool(eq(7L), eq("file.summarize"), any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> readArguments = ArgumentCaptor.forClass(Map.class);
        verify(mcpClientManager, times(5))
                .callTool(eq(7L), eq("file.read"), readArguments.capture(), any());
        assertThat(readArguments.getAllValues().stream()
                .map(value -> ((Number) value.get("fileId")).longValue()))
                .contains(2L, 3L)
                .doesNotContain(6L, 7L);
        assertThat(events).anyMatch(event -> event.startsWith("start:yliyun_attachment_list"));
        assertThat(events).anyMatch(event -> event.startsWith("start:yliyun_attachment_summarize"));
        assertThat(events).anyMatch(event -> event.startsWith("start:yliyun_attachment_read_selected"));
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
