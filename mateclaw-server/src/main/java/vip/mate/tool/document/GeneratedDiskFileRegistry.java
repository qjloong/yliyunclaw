package vip.mate.tool.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GeneratedDiskFileRegistry {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Value("${mate.generated-files.token-ttl-seconds:604800}")
    private long ttlSeconds;

    public record Entry(Path path,
                        Long workspaceId,
                        String conversationId,
                        String workspaceBasePath,
                        long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public String register(Path absolutePath,
                           Long workspaceId,
                           String conversationId,
                           String workspaceBasePath) {
        evictExpired();
        String id = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + Duration.ofSeconds(Math.max(60, ttlSeconds)).toMillis();
        entries.put(id, new Entry(
                absolutePath.toAbsolutePath().normalize(),
                workspaceId,
                conversationId,
                workspaceBasePath,
                expiresAt));
        return id;
    }

    public Optional<Entry> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Entry entry = entries.get(id);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expired()) {
            entries.remove(id, entry);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private void evictExpired() {
        entries.entrySet().removeIf(entry -> entry.getValue().expired());
    }
}
