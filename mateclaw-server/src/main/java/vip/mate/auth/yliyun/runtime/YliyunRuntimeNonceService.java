package vip.mate.auth.yliyun.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vip.mate.auth.sso.model.SsoStateEntity;
import vip.mate.auth.sso.repository.SsoStateMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Cluster-safe nonce consumption backed by the existing sso_state table. */
@Service
public class YliyunRuntimeNonceService {

    private static final String KIND = "yliyun-runtime";
    private final SsoStateMapper stateMapper;

    public YliyunRuntimeNonceService(SsoStateMapper stateMapper) {
        this.stateMapper = stateMapper;
    }

    public void consume(String nonce) {
        if (nonce == null || nonce.length() < 8 || nonce.length() > 128
                || !nonce.matches("[A-Za-z0-9._:-]+")) {
            throw new YliyunRuntimeAuthException(
                    "AUTH_HEADER_MISSING", 401, "X-Yly-Nonce is invalid",
                    "auth.request_signature",
                    "Generate a unique nonce for every request", false);
        }
        SsoStateEntity row = new SsoStateEntity();
        row.setToken(YliyunRuntimeCrypto.sha256Hex(
                (KIND + ":" + nonce).getBytes(StandardCharsets.UTF_8)));
        row.setKind(KIND);
        row.setProvider("yliyun");
        row.setConsumed(1);
        row.setCreatedAt(LocalDateTime.now());
        try {
            stateMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new YliyunRuntimeAuthException(
                    "AUTH_NONCE_REPLAYED", 401, "The request nonce has already been consumed",
                    "auth.request_signature",
                    "Generate a new nonce and sign the request again", false);
        }
    }

    @Scheduled(fixedDelayString = "${mateclaw.runtime.yliyun.nonce-purge-delay-ms:3600000}")
    public void purgeExpired() {
        stateMapper.delete(new LambdaQueryWrapper<SsoStateEntity>()
                .eq(SsoStateEntity::getKind, KIND)
                .lt(SsoStateEntity::getCreatedAt, LocalDateTime.now().minusHours(1)));
    }
}
