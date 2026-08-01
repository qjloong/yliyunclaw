package vip.mate.llm.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vip.mate.MateClawApplication;
import vip.mate.auth.model.UserEntity;
import vip.mate.auth.yliyun.YliyunUserMappingService;
import vip.mate.exception.MateClawException;
import vip.mate.llm.model.CreateCustomProviderRequest;
import vip.mate.llm.model.ModelInfoDTO;
import vip.mate.llm.model.ProviderConfigRequest;
import vip.mate.llm.service.ModelConfigService;
import vip.mate.llm.service.ModelProviderService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:workspace_model_isolation_${random.uuid};MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.ai.dashscope.api-key=test-key",
        "spring.main.web-application-type=none",
        "mateclaw.goal.enabled=false",
        "mateclaw.setting.key=workspace-isolation-test-key"
})
class WorkspaceModelIsolationIntegrationTest {

    private static final String PROVIDER_ID = "tenant-shared-provider";

    @Autowired private WorkspaceModelScope scope;
    @Autowired private ModelProviderService providerService;
    @Autowired private ModelConfigService modelConfigService;
    @Autowired private YliyunUserMappingService yliyunUserMappingService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void providerCredentialsAndModelsAreIsolatedByWorkspace() {
        provision(9101L, "secret-a", "model-a");
        provision(9102L, "secret-b", "model-b");

        scope.withWorkspace(9101L, () -> {
            assertEquals("secret-a",
                    providerService.getProviderConfig(PROVIDER_ID).getApiKey());
            assertEquals(List.of("model-a"),
                    modelConfigService.listModelsByProvider(PROVIDER_ID).stream()
                            .map(model -> model.getModelName())
                            .toList());
            assertTrue(providerService.listCatalog().stream()
                    .noneMatch(provider -> "anthropic-claude-code".equals(provider.getId())));
            assertThrows(MateClawException.class,
                    () -> providerService.getProviderConfig("anthropic-claude-code"),
                    "host-scoped credentials must not be exposed to tenant workspaces");
        });

        scope.withWorkspace(9102L, () -> {
            assertEquals("secret-b",
                    providerService.getProviderConfig(PROVIDER_ID).getApiKey());
            assertEquals(List.of("model-b"),
                    modelConfigService.listModelsByProvider(PROVIDER_ID).stream()
                            .map(model -> model.getModelName())
                            .toList());
        });

        assertThrows(MateClawException.class,
                () -> providerService.getProviderConfig(PROVIDER_ID),
                "workspace-owned custom providers must not leak into the global catalog");

        assertEquals(2, jdbc.queryForObject(
                "select count(*) from mate_workspace_model_provider where provider_id = ?",
                Integer.class, PROVIDER_ID));
        List<String> encrypted = jdbc.queryForList(
                "select api_key_encrypted from mate_workspace_model_provider "
                        + "where provider_id = ? order by workspace_id",
                String.class, PROVIDER_ID);
        assertEquals(2, encrypted.size());
        assertTrue(encrypted.stream().allMatch(value -> value.startsWith("enc:v1:")));
        assertNotEquals(encrypted.get(0), encrypted.get(1));
    }

    @Test
    void signedTenantAdminRoleOwnsAndAdministersTheTenantWorkspace() {
        UserEntity firstAdmin = yliyunUserMappingService.findOrCreateUser(
                "admin-1", "tenant-role-test", "租户管理员", "admin-1",
                "一粒云", true);
        long workspaceId = jdbc.queryForObject(
                "select workspace_id from mc_workspace_user where user_id = ?",
                Long.class, firstAdmin.getId());

        assertEquals("owner", mappedRole(firstAdmin.getId()));
        assertEquals("owner", memberRole(workspaceId, firstAdmin.getId()));
        assertEquals(firstAdmin.getId(), jdbc.queryForObject(
                "select owner_id from mate_workspace where id = ?",
                Long.class, workspaceId));

        UserEntity secondAdmin = yliyunUserMappingService.findOrCreateUser(
                "admin-2", "tenant-role-test", "第二管理员", "admin-2",
                "一粒云", true);
        UserEntity member = yliyunUserMappingService.findOrCreateUser(
                "member-1", "tenant-role-test", "普通成员", "member-1",
                "一粒云", false);

        assertEquals("admin", mappedRole(secondAdmin.getId()));
        assertEquals("admin", memberRole(workspaceId, secondAdmin.getId()));
        assertEquals("member", mappedRole(member.getId()));
        assertEquals("member", memberRole(workspaceId, member.getId()));

        // The cloud is authoritative: a demoted owner becomes a member and an
        // existing tenant admin is promoted so the workspace never loses admin control.
        yliyunUserMappingService.findOrCreateUser(
                "admin-1", "tenant-role-test", "租户管理员", "admin-1",
                "一粒云", false);
        assertEquals("member", mappedRole(firstAdmin.getId()));
        assertEquals("owner", mappedRole(secondAdmin.getId()));
        assertEquals("owner", memberRole(workspaceId, secondAdmin.getId()));
        assertEquals(secondAdmin.getId(), jdbc.queryForObject(
                "select owner_id from mate_workspace where id = ?",
                Long.class, workspaceId));
    }

    private void provision(long workspaceId, String apiKey, String modelId) {
        scope.withWorkspace(workspaceId, () -> {
            CreateCustomProviderRequest create = new CreateCustomProviderRequest();
            create.setId(PROVIDER_ID);
            create.setName("Tenant Provider");
            create.setDefaultBaseUrl("https://llm.example.test/v1");
            create.setProtocol("openai-compatible");
            create.setRequireApiKey(true);
            create.setModels(List.of(new ModelInfoDTO(modelId, modelId)));
            providerService.createCustomProvider(create);

            ProviderConfigRequest config = new ProviderConfigRequest();
            config.setApiKey(apiKey);
            config.setBaseUrl("https://llm.example.test/v1");
            config.setProtocol("openai-compatible");
            config.setGenerateKwargs(Map.of());
            config.setRequireApiKey(true);
            providerService.updateProviderConfig(PROVIDER_ID, config);
        });
    }

    private String mappedRole(Long userId) {
        return jdbc.queryForObject(
                "select role from mc_workspace_user where user_id = ? and deleted = 0",
                String.class, userId);
    }

    private String memberRole(long workspaceId, Long userId) {
        return jdbc.queryForObject(
                "select role from mate_workspace_member "
                        + "where workspace_id = ? and user_id = ? and deleted = 0",
                String.class, workspaceId, userId);
    }
}
