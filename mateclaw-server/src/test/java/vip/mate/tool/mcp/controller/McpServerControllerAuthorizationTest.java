package vip.mate.tool.mcp.controller;

import org.junit.jupiter.api.Test;
import vip.mate.workspace.core.annotation.RequireGlobalAdmin;
import vip.mate.workspace.core.annotation.RequireWorkspaceRole;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerControllerAuthorizationTest {

    private static final Set<String> MANAGEMENT_METHODS = Set.of(
            "list", "get", "create", "update", "delete", "toggle",
            "setDisclosureTier", "test", "listTools", "refresh");

    @Test
    void everyManagementEndpointRequiresGlobalAdministrator() {
        for (Method method : McpServerController.class.getDeclaredMethods()) {
            if (!MANAGEMENT_METHODS.contains(method.getName())) {
                continue;
            }
            assertTrue(method.isAnnotationPresent(RequireGlobalAdmin.class),
                    () -> method.getName() + " must require the platform global administrator");
            assertFalse(method.isAnnotationPresent(RequireWorkspaceRole.class),
                    () -> method.getName() + " must not accept a workspace administrator");
        }
    }
}
