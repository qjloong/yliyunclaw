package vip.mate.llm.workspace;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workspace-owned provider configuration and credentials.
 */
@Data
@TableName("mate_workspace_model_provider")
public class WorkspaceModelProviderEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long workspaceId;
    private String providerId;
    private String name;
    private String apiKeyPrefix;
    private String chatModel;
    /** AES-GCM encrypted via SettingCrypto. */
    private String apiKeyEncrypted;
    private String baseUrl;
    private String generateKwargs;
    private Boolean isCustom;
    private Boolean isLocal;
    private Boolean supportModelDiscovery;
    private Boolean supportConnectionCheck;
    private Boolean freezeUrl;
    private Boolean requireApiKey;
    private String authType;
    /** AES-GCM encrypted via SettingCrypto. */
    private String oauthAccessTokenEncrypted;
    /** AES-GCM encrypted via SettingCrypto. */
    private String oauthRefreshTokenEncrypted;
    private Long oauthExpiresAt;
    private String oauthAccountId;
    private Integer fallbackPriority;
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
