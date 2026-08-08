package com.treepeople.leapmindtts.service.lesson.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

@Slf4j
@Service("lessonPptxExportServiceImpl")
@RequiredArgsConstructor
public class PptxExportServiceImpl {
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * 苏杰调用此方法导出PPT
     */
    public String exportPptxByPrepId(Long prepId) throws Exception {
        log.info("开始执行备课 {} 的 PPTX 导出逻辑...", prepId);
        // 1. 查询数据库PPT结构JSON
        String pptJson;
        try {
            pptJson = jdbcTemplate.queryForObject(
                    "SELECT ppt_structure FROM teaching_contents WHERE prep_id = ?",
                    String.class, prepId
            );
        } catch (Exception e) {
            log.error("查询备课{}PPT结构失败", prepId, e);
            throw new RuntimeException("未查询到备课PPT数据");
        }
        if (pptJson == null || pptJson.isBlank()) {
            throw new RuntimeException("备课PPT结构为空，无法导出");
        }

        // 2. POI 渲染PPT
        XMLSlideShow ppt = new XMLSlideShow();
        JsonNode root = objectMapper.readTree(pptJson);
        ArrayNode pageArray = (ArrayNode) root.get("pages");
        if (pageArray != null && pageArray.size() > 0) {
            for (JsonNode page : pageArray) {
                XSLFSlide slide = ppt.createSlide();
                // 渲染标题
                if (page.has("title")) {
                    XSLFTextBox titleBox = slide.createTextBox();
                    titleBox.setAnchor(new Rectangle(40, 30, 660, 60));
                    XSLFTextParagraph p = titleBox.addNewTextParagraph();
                    XSLFTextRun run = p.addNewTextRun();
                    run.setText(page.get("title").asText());
                    run.setFontSize(26D);
                    run.setBold(true);
                }
                // 渲染正文
                if (page.has("content")) {
                    XSLFTextBox contentBox = slide.createTextBox();
                    contentBox.setAnchor(new Rectangle(40, 110, 660, 380));
                    XSLFTextParagraph p = contentBox.addNewTextParagraph();
                    XSLFTextRun run = p.addNewTextRun();
                    run.setText(page.get("content").asText());
                    run.setFontSize(16D);
                }
                // 渲染旁白提示
                if (page.has("narrationText") && !page.get("narrationText").asText().isBlank()) {
                    XSLFTextBox voiceBox = slide.createTextBox();
                    voiceBox.setAnchor(new Rectangle(40, 500, 660, 40));
                    XSLFTextParagraph p = voiceBox.addNewTextParagraph();
                    XSLFTextRun run = p.addNewTextRun();
                    run.setText("配音旁白：" + page.get("narrationText").asText());
                    run.setFontSize(12D);
                    run.setItalic(true);
                }
            }
        }

        // 3. 转字节流
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ppt.write(bos);
        ppt.close();
        byte[] pptBytes = bos.toByteArray();

        // 4. MinIO上传
        String fileKey = "prep-ppt/" + UUID.randomUUID() + "_" + prepId + ".pptx";
        try (ByteArrayInputStream bis = new ByteArrayInputStream(pptBytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileKey)
                    .stream(bis, pptBytes.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                    .build());
        }

        // 5. 生成1天有效期签名下载链接（私有桶可用）
        String downloadUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucketName)
                .object(fileKey)
                .expiry(60 * 60 * 24)
                .build());

        // 6. 更新数据库保存下载链接
        jdbcTemplate.update(
                "UPDATE teaching_contents SET ppt_download_url = ? WHERE prep_id = ?",
                downloadUrl, prepId
        );

        log.info("备课 {} PPTX 导出完成，临时下载链接: {}", prepId, downloadUrl);
        return downloadUrl;
    }
}
