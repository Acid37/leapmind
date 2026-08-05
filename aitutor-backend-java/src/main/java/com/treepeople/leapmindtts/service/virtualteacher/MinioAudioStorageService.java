package com.treepeople.leapmindtts.service.virtualteacher;

import com.treepeople.leapmindtts.config.VirtualTeacherProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(prefix = "virtual-teacher.storage", name = "type", havingValue = "minio")
public class MinioAudioStorageService implements AudioStorageService {
    private final MinioClient client;
    private final String bucket;

    public MinioAudioStorageService(VirtualTeacherProperties properties) {
        VirtualTeacherProperties.Storage storage = properties.getStorage();
        this.bucket = storage.getBucket();
        this.client = MinioClient.builder()
                .endpoint(storage.getEndpoint())
                .credentials(storage.getAccessKey(), storage.getSecretKey())
                .build();
    }

    @PostConstruct
    public void initializeBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化 MinIO Bucket 失败", e);
        }
    }

    @Override
    public void store(String objectKey, byte[] data, String contentType) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(input, data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("保存音频到 MinIO 失败", e);
        }
    }

    @Override
    public Optional<byte[]> load(String objectKey) {
        try (var input = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return Optional.of(input.readAllBytes());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public String createReadUrl(String objectKey) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(24, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("生成 MinIO 音频地址失败", e);
        }
    }
}
