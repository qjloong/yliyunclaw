package vip.mate.setting.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vip.mate.setting.contract.EffectiveSettingsContract;
import vip.mate.setting.contract.SettingFieldClass;
import vip.mate.setting.contract.SettingMergeStrategy;
import vip.mate.setting.contract.SettingSource;
import vip.mate.workspace.core.model.WorkspacePolicy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WP-1 unit tests for {@link WorkspaceSettingsResolver}.
 */
class WorkspaceSettingsResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkspaceSettingsResolver resolver = new WorkspaceSettingsResolver(objectMapper);

    @Test
    void resolveWorkspaceSettingsJson_classifiesPolicyKeysExplicitly() {
        String json = """
                {"projectPermissionMode":"full","workspacePolicy":{"sandboxMode":"read-only"},"language":"zh-CN"}
                """;

        Map<String, EffectiveSettingsContract.ResolutionResult<?>> result =
                resolver.resolveWorkspaceSettingsJson(json, 1L);

        assertEquals("full", result.get("projectPermissionMode").value());
        assertEquals(SettingFieldClass.SAFETY_POLICY, result.get("projectPermissionMode").fieldClass());
        assertEquals(SettingMergeStrategy.RESTRICTIVE_MERGE, result.get("projectPermissionMode").mergeStrategy());

        assertNotNull(result.get("workspacePolicy"));
        assertEquals(SettingFieldClass.SAFETY_POLICY, result.get("workspacePolicy").fieldClass());

        assertEquals("zh-CN", result.get("language").value());
        assertEquals(SettingFieldClass.PRODUCT_SETTING, result.get("language").fieldClass());
    }

    @Test
    void resolveWorkspacePolicy_extractsNormalizedPolicy() {
        String json = """
                {"workspacePolicy":{"sandboxMode":"full-access","approvalPolicy":"strict","networkPolicy":"restricted"}}
                """;

        EffectiveSettingsContract.ResolutionResult<WorkspacePolicy> result =
                resolver.resolveWorkspacePolicy(json, 1L);

        assertNotNull(result.value());
        assertEquals("full-access", result.value().getSandboxMode());
        assertEquals("strict", result.value().getApprovalPolicy());
        assertEquals("restricted", result.value().getNetworkPolicy());
        assertEquals(SettingFieldClass.SAFETY_POLICY, result.fieldClass());
    }

    @Test
    void resolveProjectPermissionMode_extractsMode() {
        String json = "{\"projectPermissionMode\":\"limited\"}";

        EffectiveSettingsContract.ResolutionResult<String> result =
                resolver.resolveProjectPermissionMode(json, 1L);

        assertEquals("limited", result.value());
        assertEquals(SettingSource.WORKSPACE_SHARED_SETTINGS, result.source());
    }

    @Test
    void resolveWorkspaceSettingsJson_returnsEmptyForNullInput() {
        Map<String, EffectiveSettingsContract.ResolutionResult<?>> result =
                resolver.resolveWorkspaceSettingsJson(null, 1L);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveWorkspaceSettingsJson_returnsEmptyForBlankInput() {
        Map<String, EffectiveSettingsContract.ResolutionResult<?>> result =
                resolver.resolveWorkspaceSettingsJson("   ", 1L);
        assertTrue(result.isEmpty());
    }
}
