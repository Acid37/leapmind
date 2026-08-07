package com.treepeople.leapmindtts.service.lesson.impl;

import com.treepeople.leapmindtts.pojo.dto.FileUploadResponse;
import com.treepeople.leapmindtts.service.lesson.FileStorageService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件存储实现（许沣睿）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageServiceImpl implements FileStorageService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".png", ".jpg", ".jpeg", ".txt"
    ));

    private final MinioClient minioClient;

    @Value("${minio.teaching-bucket:leapmind-teaching}")
    private String bucketName;

    @Override
    public FileUploadResponse uploadFile(MultipartFile file, String courseId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制，最大允许50MB，当前文件大小："
                    + String.format("%.2f", file.getSize() / (1024.0 * 1024.0)) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileExtension);
        }

        String filePath = "teaching/" + courseId + "/" + UUID.randomUUID().toString() + fileExtension;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("上传文件到MinIO失败: courseId={}, fileName={}", courseId, originalFilename, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }

        log.info("文件上传成功: courseId={}, filePath={}, size={}KB",
                courseId, filePath, file.getSize() / 1024);

        return FileUploadResponse.builder()
                .filePath(filePath)
                .fileName(originalFilename)
                .fileSize(file.getSize())
                .fileType(getFileTypeFromExtension(fileExtension))
                .build();
    }

    @Override
    public String getFileUrl(String filePath) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(filePath)
                            .method(Method.GET)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("获取文件URL失败: filePath={}", filePath, e);
            throw new RuntimeException("获取文件URL失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .build());
        } catch (Exception e) {
            log.error("删除文件失败: filePath={}", filePath, e);
            throw new RuntimeException("删除文件失败: " + e.getMessage(), e);
        }
    }

    private String getFileTypeFromExtension(String ext) {
        switch (ext) {
            case ".pdf": return "PDF";
            case ".doc":
            case ".docx": return "WORD";
            case ".ppt":
            case ".pptx": return "PPT";
            case ".png":
            case ".jpg":
            case ".jpeg": return "IMAGE";
            case ".txt": return "TEXT";
            default: return "OTHER";
        }
    }
}
