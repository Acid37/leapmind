package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储 Service 接口（许沣睿 - MinIO）
 */
public interface FileStorageService {

    FileUploadResponse uploadFile(MultipartFile file, String courseId);

    String getFileUrl(String filePath);

    void deleteFile(String filePath);
}
