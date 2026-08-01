package vip.mate.llm.workspace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vip.mate.tool.builtin.ToolExecutionContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Resolves the workspace whose model/provider configuration is active.
 *
 * <p>HTTP management requests use {@code X-Workspace-Id}; agent construction
 * can open an explicit scope; asynchronous tool calls inherit the workspace
 * stored in {@link ToolExecutionContext}. Legacy/system jobs without any
 * workspace context continue to use workspace {@code 1}.</p>
 */
@Component
public class WorkspaceModelScope {

    public static final long DEFAULT_WORKSPACE_ID = 1L;

    private final ThreadLocal<Deque<Long>> overrides =
            ThreadLocal.withInitial(ArrayDeque::new);

    public long currentWorkspaceId() {
        Deque<Long> stack = overrides.get();
        if (!stack.isEmpty()) {
            return stack.peek();
        }
        Long toolWorkspaceId = ToolExecutionContext.workspaceId();
        if (isValid(toolWorkspaceId)) {
            return toolWorkspaceId;
        }
        Long requestWorkspaceId = requestWorkspaceId();
        return isValid(requestWorkspaceId) ? requestWorkspaceId : DEFAULT_WORKSPACE_ID;
    }

    public Long requestWorkspaceId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String value = request.getHeader("X-Workspace-Id");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long workspaceId = Long.parseLong(value.trim());
            return workspaceId > 0 ? workspaceId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public Scope open(Long workspaceId) {
        long effective = isValid(workspaceId) ? workspaceId : DEFAULT_WORKSPACE_ID;
        Deque<Long> stack = overrides.get();
        stack.push(effective);
        return () -> {
            Deque<Long> current = overrides.get();
            if (!current.isEmpty()) {
                current.pop();
            }
            if (current.isEmpty()) {
                overrides.remove();
            }
        };
    }

    public <T> T withWorkspace(Long workspaceId, Supplier<T> action) {
        try (Scope ignored = open(workspaceId)) {
            return action.get();
        }
    }

    public void withWorkspace(Long workspaceId, Runnable action) {
        try (Scope ignored = open(workspaceId)) {
            action.run();
        }
    }

    private boolean isValid(Long workspaceId) {
        return workspaceId != null && workspaceId > 0;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
