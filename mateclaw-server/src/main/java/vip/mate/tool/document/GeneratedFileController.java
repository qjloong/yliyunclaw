package vip.mate.tool.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Serves bytes produced by tools and stashed in {@link GeneratedFileCache}.
 *
 * <p>Endpoint is intentionally unauthenticated; the UUID in the URL is the only
 * access credential. Entries expire after {@link GeneratedFileCache#TTL}.
 */
@Tag(name = "Generated Files")
@RestController
@RequestMapping("/api/v1/files/generated")
@RequiredArgsConstructor
public class GeneratedFileController {

    private final GeneratedFileCache cache;
    private final GeneratedFileDiskTokenService diskTokenService;
    private final GeneratedDiskFileRegistry diskFileRegistry;

    @Value("${mate.generated-files.output-dir:output}")
    private String outputDir;

    @Operation(summary = "Download a tool-generated file by its one-time id")
    @GetMapping("/{id:.+}")
    public ResponseEntity<?> download(@PathVariable String id) {
        return cache.get(id)
                .<ResponseEntity<?>>map(entry -> {
                    String encodedName = URLEncoder.encode(entry.filename(), StandardCharsets.UTF_8)
                            .replace("+", "%20");
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType(entry.mimeType()));
                    // RFC 5987 filename* lets non-ASCII names round-trip in browsers.
                    headers.add(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + sanitizeAscii(entry.filename())
                                    + "\"; filename*=UTF-8''" + encodedName);
                    headers.setContentLength(entry.bytes().length);
                    return ResponseEntity.ok().headers(headers).body(entry.bytes());
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("error", "File not found or expired")));
    }

    @Operation(summary = "Preview a safe generated file by its one-time id")
    @GetMapping("/{id:.+}/inline")
    public ResponseEntity<?> preview(@PathVariable String id) {
        return cache.get(id)
                .<ResponseEntity<?>>map(entry -> {
                    String mimeType = entry.mimeType() != null ? entry.mimeType().toLowerCase() : "";
                    String filename = entry.filename() != null ? entry.filename().toLowerCase() : "";
                    if (!isInlinePreviewAllowed(mimeType, filename)) {
                        return ResponseEntity.status(415)
                                .body(Map.of("error", "Preview is not allowed for this generated file type"));
                    }
                    String encodedName = URLEncoder.encode(entry.filename(), StandardCharsets.UTF_8)
                            .replace("+", "%20");
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType(resolveInlineMimeType(mimeType, filename)));
                    headers.add(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + sanitizeAscii(entry.filename())
                                    + "\"; filename*=UTF-8''" + encodedName);
                    headers.add("X-Content-Type-Options", "nosniff");
                    if (isHtml(mimeType, filename)) {
                        headers.add("Content-Security-Policy",
                                "default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; sandbox");
                    }
                    headers.setContentLength(entry.bytes().length);
                    return ResponseEntity.ok().headers(headers).body(entry.bytes());
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("error", "File not found or expired")));
    }

    @Operation(summary = "Download a tool-generated file from disk by signed token")
    @GetMapping("/disk/{token}")
    public ResponseEntity<?> downloadDisk(@PathVariable String token) {
        return buildDiskResponse(token, false);
    }

    @Operation(summary = "Preview a tool-generated file from disk by signed token")
    @GetMapping("/disk/{token}/inline")
    public ResponseEntity<?> previewDisk(@PathVariable String token) {
        return buildDiskResponse(token, true);
    }

    private ResponseEntity<?> buildDiskResponse(String token, boolean inline) {
        Path outputRoot = Paths.get(outputDir).toAbsolutePath().normalize();
        DiskFileRef diskFile = resolveDiskFile(token);
        if (diskFile == null) {
            return ResponseEntity.status(404).body(Map.of("error", "File not found or token expired"));
        }
        Path file = diskFile.path();
        if (!isAllowedDiskFile(file, outputRoot, diskFile.workspaceBasePath())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.status(404).body(Map.of("error", "File not found"));
        }

        try {
            String filename = file.getFileName().toString();
            String mime = detectMimeType(file, filename);
            String filenameLower = filename.toLowerCase();
            if (inline && !isInlinePreviewAllowed(mime.toLowerCase(), filenameLower)) {
                return ResponseEntity.status(415)
                        .body(Map.of("error", "Preview is not allowed for this generated file type"));
            }

            String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(inline ? resolveInlineMimeType(mime, filenameLower) : mime));
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    (inline ? "inline" : "attachment") + "; filename=\"" + sanitizeAscii(filename)
                            + "\"; filename*=UTF-8''" + encodedName);
            headers.add("X-Content-Type-Options", "nosniff");
            if (inline && isHtml(mime.toLowerCase(), filenameLower)) {
                headers.add("Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; sandbox");
            }
            headers.setContentLength(Files.size(file));

            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to stream generated file"));
        }
    }

    private DiskFileRef resolveDiskFile(String idOrToken) {
        GeneratedDiskFileRegistry.Entry entry = diskFileRegistry.get(idOrToken).orElse(null);
        if (entry != null) {
            return new DiskFileRef(entry.path(), entry.workspaceBasePath());
        }
        GeneratedFileDiskTokenService.DiskToken diskToken = diskTokenService.verify(idOrToken).orElse(null);
        if (diskToken != null) {
            return new DiskFileRef(diskToken.path(), diskToken.workspaceBasePath());
        }
        return null;
    }

    private record DiskFileRef(Path path, String workspaceBasePath) {
    }

    private boolean isAllowedDiskFile(Path file, Path outputRoot, String workspaceBasePath) {
        if (file.startsWith(outputRoot)) {
            return true;
        }
        if (workspaceBasePath != null && !workspaceBasePath.isBlank()) {
            try {
                Path workspaceOutputRoot = Paths.get(workspaceBasePath)
                        .toAbsolutePath()
                        .normalize()
                        .resolve(outputDir)
                        .normalize();
                return file.startsWith(workspaceOutputRoot);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String detectMimeType(Path file, String filename) {
        try {
            String probe = Files.probeContentType(file);
            if (probe != null && !probe.isBlank()) {
                return probe;
            }
        } catch (Exception ignored) {
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html;charset=utf-8";
        if (lower.endsWith(".md")) return "text/markdown;charset=utf-8";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain;charset=utf-8";
        if (lower.endsWith(".json")) return "application/json;charset=utf-8";
        if (lower.endsWith(".csv")) return "text/csv;charset=utf-8";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private boolean isInlinePreviewAllowed(String mimeType, String filename) {
        return isHtml(mimeType, filename)
                || mimeType.startsWith("text/")
                || filename.endsWith(".md")
                || filename.endsWith(".json")
                || filename.endsWith(".csv")
                || filename.endsWith(".log");
    }

    private boolean isHtml(String mimeType, String filename) {
        return mimeType.contains("text/html") || filename.endsWith(".html") || filename.endsWith(".htm");
    }

    private String resolveInlineMimeType(String mimeType, String filename) {
        if (isHtml(mimeType, filename)) {
            return "text/html;charset=utf-8";
        }
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType;
        }
        return "text/plain;charset=utf-8";
    }

    private String sanitizeAscii(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            sb.append(c < 0x20 || c >= 0x7F || c == '"' || c == '\\' ? '_' : c);
        }
        return sb.toString();
    }
}
