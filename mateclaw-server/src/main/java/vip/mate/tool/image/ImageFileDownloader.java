package vip.mate.tool.image;

import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * 图片文件下载器 — 从 provider CDN 下载图片到本地存储，或解码 Base64 图片
 *
 * @author MateClaw Team
 */
@Slf4j
@Component
public class ImageFileDownloader {

    private static final Path UPLOAD_ROOT = Paths.get("data", "chat-uploads");

    /**
     * 从 URL 下载图片到本地
     */
    public Path download(String imageUrl, String conversationId, String taskId, int index) throws IOException {
        Path dir = UPLOAD_ROOT.resolve(conversationId);
        Files.createDirectories(dir);

        String extension = guessExtension(imageUrl);
        String fileName = "image_" + taskId + "_" + index + extension;
        Path targetFile = dir.resolve(fileName);

        log.info("[ImageDownloader] Downloading image from {} to {}", imageUrl, targetFile);
        long size = HttpUtil.downloadFile(imageUrl, targetFile.toFile());
        log.info("[ImageDownloader] Downloaded {} bytes to {}", size, targetFile);

        return targetFile;
    }

    /**
     * 将 Base64 编码的图片保存到本地
     */
    public Path saveBase64(String base64Data, String conversationId, String taskId, int index) throws IOException {
        Path dir = UPLOAD_ROOT.resolve(conversationId);
        Files.createDirectories(dir);

        String fileName = "image_" + taskId + "_" + index + ".png";
        Path targetFile = dir.resolve(fileName);

        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        Files.write(targetFile, imageBytes);
        log.info("[ImageDownloader] Saved base64 image ({} bytes) to {}", imageBytes.length, targetFile);

        return targetFile;
    }

    /**
     * 构造文件的 API 访问 URL
     */
    public String toServingUrl(String conversationId, Path localPath) {
        return "/api/v1/chat/files/" + conversationId + "/" + localPath.getFileName().toString();
    }

    private String guessExtension(String url) {
        String lower = url.toLowerCase().split("\\?")[0];
        if (lower.endsWith(".png")) return ".png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
        if (lower.endsWith(".webp")) return ".webp";
        if (lower.endsWith(".gif")) return ".gif";
        return ".png";
    }
}
