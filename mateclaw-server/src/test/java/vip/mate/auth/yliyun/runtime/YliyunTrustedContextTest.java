package vip.mate.auth.yliyun.runtime;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YliyunTrustedContextTest {

    @Test
    void acceptsAValidFrozenContext() {
        long now = Instant.now().getEpochSecond();
        YliyunTrustedContext context = context(now - 1, now + 59);
        assertDoesNotThrow(() -> context.validate(Instant.ofEpochSecond(now)));
    }

    @Test
    void rejectsExpiredContextWithContractCode() {
        long now = Instant.now().getEpochSecond();
        YliyunRuntimeAuthException ex = assertThrows(
                YliyunRuntimeAuthException.class,
                () -> context(now - 120, now - 1).validate(Instant.ofEpochSecond(now)));
        assertEquals("CONTEXT_EXPIRED", ex.getErrorCode());
    }

    @Test
    void rejectsUnknownCapabilities() {
        long now = Instant.now().getEpochSecond();
        YliyunTrustedContext context = new YliyunTrustedContext(
                1001, 2001, "ws_10", "GOAL", "GOAL_PROJECT", "3001",
                null, null, "550e8400-e29b-41d4-a716-446655440000",
                List.of("OWNER"), List.of("TENANT_ADMIN"), 12, now, now + 60);
        YliyunRuntimeAuthException ex = assertThrows(
                YliyunRuntimeAuthException.class,
                () -> context.validate(Instant.ofEpochSecond(now)));
        assertEquals("CONTEXT_MISSING", ex.getErrorCode());
    }

    private static YliyunTrustedContext context(long issuedAt, long expiresAt) {
        return new YliyunTrustedContext(
                1001, 2001, "ws_10", "GOAL", "GOAL_PROJECT", "3001",
                null, "run_x1", "550e8400-e29b-41d4-a716-446655440000",
                List.of("OWNER"), List.of("PROJECT_READ"), 12,
                issuedAt, expiresAt);
    }
}
