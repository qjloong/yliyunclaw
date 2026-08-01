package vip.mate.auth.yliyun;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vip.mate.auth.sso.model.SsoStateEntity;
import vip.mate.auth.sso.repository.SsoStateMapper;
import vip.mate.exception.MateClawException;

import java.time.LocalDateTime;

/**
 * Cluster-safe single-use store for Yliyun ticket jti values.
 *
 * <p>Reuses the existing durable SSO nonce table rather than an in-memory map,
 * so ticket exchange remains one-time when requests land on different nodes.
 */
@Service
@RequiredArgsConstructor
public class YliyunTicketReplayService {

    private static final String KIND = "yliyun";
    private final SsoStateMapper stateMapper;

    public void consume(String jti) {
        if (jti == null || jti.isBlank() || jti.length() > 128) {
            throw new MateClawException("err.auth.yliyun.missing_jti",
                    401, "ticket 缺少有效 jti");
        }
        SsoStateEntity consumed = new SsoStateEntity();
        consumed.setToken(jti);
        consumed.setKind(KIND);
        consumed.setProvider("yliyun");
        consumed.setConsumed(1);
        consumed.setCreatedAt(LocalDateTime.now());
        try {
            stateMapper.insert(consumed);
        } catch (DuplicateKeyException e) {
            throw new MateClawException("err.auth.yliyun.replay",
                    401, "ticket 已被使用（疑似重放）");
        }
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpired() {
        // Ticket TTL is five minutes; retain one hour for clock skew/audit.
        stateMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SsoStateEntity>()
                .eq(SsoStateEntity::getKind, KIND)
                .lt(SsoStateEntity::getCreatedAt, LocalDateTime.now().minusHours(1)));
    }
}
