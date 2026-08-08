package com.treepeople.leapmindtts.service.virtualteacher;

import com.treepeople.leapmindtts.config.VirtualTeacherProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "virtual-teacher.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalAudioStorageService implements AudioStorageService {
    private final Path root;
    private final String publicBaseUrl;

    public LocalAudioStorageService(VirtualTeacherProperties properties) {
        String configuredPath = properties.getStorage().getLocalDir()
                .replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
        this.root = Paths.get(configuredPath).toAbsolutePath().normalize();
        this.publicBaseUrl = properties.getStorage().getPublicBaseUrl();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建本地 TTS 存储目录", e);
        }
    }

    @Override
    public void store(String objectKey, byte[] data, String contentType) {
        try {
            Files.write(resolve(objectKey), data);
        } catch (IOException e) {
            throw new IllegalStateException("保存 TTS 音频失败", e);
        }
    }

    @Override
    public Optional<byte[]> load(String objectKey) {
        Path path = resolve(objectKey);
        if (!Files.exists(path)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new IllegalStateException("读取 TTS 音频失败", e);
        }
    }

    @Override
    public String createReadUrl(String objectKey) {
        String path = "/api/virtual-teacher/audio/" + objectKey;
        return publicBaseUrl == null || publicBaseUrl.isBlank()
                ? path
                : publicBaseUrl.replaceAll("/$", "") + path;
    }

    private Path resolve(String objectKey) {
        if (objectKey == null || !objectKey.matches("[a-f0-9]{64}\\.wav")) {
            throw new IllegalArgumentException("非法音频对象键");
        }
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("非法音频对象路径");
        }
        return path;
    }
}
