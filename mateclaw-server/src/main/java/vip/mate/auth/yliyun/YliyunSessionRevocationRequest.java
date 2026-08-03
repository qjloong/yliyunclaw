package vip.mate.auth.yliyun;

import lombok.Data;

@Data
public class YliyunSessionRevocationRequest {
    private String appKey;
    private String tenantId;
    private Integer configVersion;
    private Long issuedAt;
    private String nonce;
}
