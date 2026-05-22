package vip.mate.policy.resolver;

import org.junit.jupiter.api.Test;
import vip.mate.policy.contract.PolicyApplicationMode;
import vip.mate.policy.contract.PolicyDimension;
import vip.mate.policy.contract.PolicyScope;
import vip.mate.policy.contract.EffectivePolicyContract;
import vip.mate.workspace.core.model.WorkspacePolicy;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WP-2 unit tests for {@link PolicyMergeHelper}.
 */
class PolicyMergeHelperTest {

    @Test
    void tightenMode_templateCannotWeakenWorkspaceSandbox() {
        WorkspacePolicy workspace = policy("read-only", "default", "inherit", null, null, null);
        WorkspacePolicy template = policy("full-access", "default", "inherit", null, null, null);

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        assertEquals("read-only", result.effectivePolicy().getSandboxMode());
    }

    @Test
    void tightenMode_templateCanTightenWorkspaceSandbox() {
        WorkspacePolicy workspace = policy("full-access", "default", "inherit", null, null, null);
        WorkspacePolicy template = policy("read-only", "default", "inherit", null, null, null);

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        assertEquals("read-only", result.effectivePolicy().getSandboxMode());
    }

    @Test
    void tightenMode_templateCannotWeakenApprovalPolicy() {
        WorkspacePolicy workspace = policy("workspace-write", "strict", "inherit", null, null, null);
        WorkspacePolicy template = policy("workspace-write", "default", "inherit", null, null, null);

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        assertEquals("strict", result.effectivePolicy().getApprovalPolicy());
    }

    @Test
    void tightenMode_templateCanTightenApprovalPolicy() {
        WorkspacePolicy workspace = policy("workspace-write", "default", "inherit", null, null, null);
        WorkspacePolicy template = policy("workspace-write", "strict", "inherit", null, null, null);

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        assertEquals("strict", result.effectivePolicy().getApprovalPolicy());
    }

    @Test
    void tightenMode_templateCannotWeakenNetworkPolicy() {
        WorkspacePolicy workspace = policy("workspace-write", "default", "disabled", null, null, null);
        WorkspacePolicy template = policy("workspace-write", "default", "inherit", null, null, null);

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        assertEquals("disabled", result.effectivePolicy().getNetworkPolicy());
    }

    @Test
    void allowedPathsAreUnioned() {
        WorkspacePolicy workspace = policy("workspace-write", "default", "inherit",
                List.of("/a", "/b"), null, null);
        WorkspacePolicy template = policy("workspace-write", "default", "inherit",
                List.of("/b", "/c"), null, null);

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        List<String> allowed = result.effectivePolicy().getAllowedPaths();
        assertTrue(allowed.contains("/a"));
        assertTrue(allowed.contains("/b"));
        assertTrue(allowed.contains("/c"));
    }

    @Test
    void deniedPathsAreUnioned() {
        WorkspacePolicy workspace = policy("workspace-write", "default", "inherit",
                null, List.of("/x"), null);
        WorkspacePolicy template = policy("workspace-write", "default", "inherit",
                null, List.of("/y"), null);

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        List<String> denied = result.effectivePolicy().getDeniedPaths();
        assertTrue(denied.contains("/x"));
        assertTrue(denied.contains("/y"));
    }

    @Test
    void riskOverrides_workspaceWinsOnKeyCollision() {
        WorkspacePolicy workspace = policy("workspace-write", "default", "inherit",
                null, null, Map.of("file-write", "high"));
        WorkspacePolicy template = policy("workspace-write", "default", "inherit",
                null, null, Map.of("file-write", "low", "network", "medium"));

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        Map<String, String> overrides = result.effectivePolicy().getRiskOverrides();
        assertEquals("high", overrides.get("file-write")); // workspace wins
        assertEquals("medium", overrides.get("network"));  // template fills gap
    }

    @Test
    void provenanceIsPopulated() {
        WorkspacePolicy workspace = policy("read-only", "strict", "disabled", null, null, null);
        WorkspacePolicy template = policy("full-access", "default", "inherit", null, null, null);

        EffectivePolicyContract.PolicyResolutionResult result =
                PolicyMergeHelper.merge(workspace, template, PolicyScope.TEMPLATE);

        assertTrue(result.provenance().containsKey(PolicyDimension.SANDBOX));
        assertEquals(PolicyScope.WORKSPACE, result.provenance().get(PolicyDimension.SANDBOX).winningScope());
        assertEquals(PolicyApplicationMode.TIGHTEN, result.provenance().get(PolicyDimension.SANDBOX).appliedMode());
    }

    private static WorkspacePolicy policy(String sandbox, String approval, String network,
                                          List<String> allowedPaths, List<String> deniedPaths,
                                          Map<String, String> riskOverrides) {
        WorkspacePolicy p = new WorkspacePolicy();
        p.setSandboxMode(sandbox);
        p.setApprovalPolicy(approval);
        p.setNetworkPolicy(network);
        p.setAllowedPaths(allowedPaths);
        p.setDeniedPaths(deniedPaths);
        p.setRiskOverrides(riskOverrides);
        return p;
    }
}
