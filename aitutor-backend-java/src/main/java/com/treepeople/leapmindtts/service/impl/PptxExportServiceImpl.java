package com.treepeople.leapmindtts.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.TeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.PptStructureDTO;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.service.PptxExportService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PptxExportServiceImpl implements PptxExportService {

    private final TeachingContentMapper teachingContentMapper;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket-name:leapmind}")
    private String bucketName;

    @Value("${minio.endpoint:http://127.0.0.1:9000}")
    private String endpoint;

    private static final String PPT_OBJECT_PREFIX = "ppt-exports/";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final int SLIDE_W = 960;
    private static final int SLIDE_H = 540;

    public String export(Long prepId) {
        log.info("导出PPT, prepId={}", prepId);
        try {
            TeachingContent content = teachingContentMapper.selectByPrepId(prepId);
            if (content == null) throw new IllegalArgumentException("备课内容不存在: " + prepId);
            String url = exportFromTeachingContent(content);

            // 将下载 URL 回写到 teaching_contents 已有的 ppt_download_url 列（不新增列、不新增表）
            try {
                content.setPptDownloadUrl(url);
                teachingContentMapper.updateById(content);
                log.info("已更新 teaching_contents.ppt_download_url, id={}, url={}", content.getId(), url);
            } catch (Exception dbEx) {
                log.warn("PPT URL 回写数据库失败（不影响用户下载）, prepId={}, err={}", prepId, dbEx.getMessage());
            }

            return url;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("PPT导出失败, prepId={}", prepId, e);
            throw new RuntimeException("PPT导出失败: " + e.getMessage(), e);
        }
    }

    public String exportFromTeachingContent(TeachingContent content) {
        if (content == null) throw new IllegalArgumentException("TeachingContent 不能为 null");
        String json = content.getPptStructure();
        if (json == null || json.isEmpty()) throw new IllegalArgumentException("PPT结构数据为空（ppt_structure 列为空）");
        return exportFromJson(json, content.getTemplateId(), content.getTitle());
    }

    public String exportFromJson(String json, Long templateId, String fileName) {
        try {
            // 约定：入参 JSON 必须已是 Python 端 convert_keys_camel 处理后的格式：
            //   顶层 {"pptId":N,"slides":[...]} 对象，所有键 camelCase（递归到嵌套层）
            // Java 端不做任何 snake→camel 转换，仅在解析失败且 JSON 以 [ 开头时
            // 进行"纯数组 → 对象"的内存包装兜底（不改 DB / 不改 Python）
            PptStructureDTO structure = PptStructureDTO.parse(objectMapper, json);
            return exportFromStructure(structure, templateId, fileName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析PPT结构失败（请确认入参已是Python端 convert_keys_camel 后的 camelCase + 顶层对象格式）", e);
            throw new RuntimeException("解析PPT结构失败: " + e.getMessage(), e);
        }
    }
  
    public String exportFromStructure(PptStructureDTO structure, Long templateId, String fileName) {
        try {
            byte[] pptxBytes = generatePptxBytes(structure, templateId);
            String objectName = buildObjectName(fileName, structure.getTitle());
            String contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            String url = uploadToMinio(objectName, pptxBytes, contentType);
            log.info("PPT导出成功, url={}", url);
            return url;
        } catch (Exception e) {
            log.error("PPT导出失败", e);
            throw new RuntimeException("PPT导出失败: " + e.getMessage(), e);
        }
    }

    public byte[] generatePptxBytes(PptStructureDTO structure, Long templateId) {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PptStructureDTO.TemplateConfig cfg = resolveConfig(structure);
            ppt.setPageSize(new Dimension(SLIDE_W, SLIDE_H));

            // ——— 兼容 Python generate-ppt 返回的 {pptId, slides} 顶层格式 ———
            // 如果没传 title/description，从 type=cover 的 slide 里提取
            List<PptStructureDTO.SlideDTO> slides = structure.getSlides();
            if (slides != null && !slides.isEmpty()) {
                PptStructureDTO.SlideDTO cover = slides.stream()
                        .filter(s -> "cover".equalsIgnoreCase(s.getType()))
                        .findFirst()
                        .orElse(null);
                if (cover != null) {
                    if ((structure.getTitle() == null || structure.getTitle().isBlank())
                            && cover.getTitle() != null) {
                        structure.setTitle(cover.getTitle());
                    }
                    if ((structure.getDescription() == null || structure.getDescription().isBlank())
                            && cover.getBulletPoints() != null && !cover.getBulletPoints().isEmpty()) {
                        structure.setDescription(String.join(" · ", cover.getBulletPoints()));
                    }
                }
            }

            buildTitleSlide(ppt, structure, cfg);

            if (slides != null) {
                // ——— 每页先 maybeSplit 拆超长页，然后扁平化（可能 1→N 页）再画 ———
                java.util.List<PptStructureDTO.SlideDTO> expanded = new java.util.ArrayList<>();
                for (PptStructureDTO.SlideDTO s : slides) {
                    if ("cover".equalsIgnoreCase(s.getType())) continue; // cover 已被 buildTitleSlide 用掉，不再画
                    expanded.addAll(maybeSplit(s));
                }
                for (PptStructureDTO.SlideDTO page : expanded) {
                    buildContentSlide(ppt, page, cfg);
                }
            }

            buildEndSlide(ppt, structure, cfg);

            ppt.write(out);
            log.info("PPTX生成成功, size={}", out.size());
            return out.toByteArray();
        } catch (Exception e) {
            log.error("生成PPTX失败", e);
            throw new RuntimeException("生成PPTX失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getTempFilePath(Long prepId) {
        String datePath = LocalDateTime.now().format(DATE_FMT);
        return PPT_OBJECT_PREFIX + datePath + "/prep-" + prepId + "-" + UUID.randomUUID().toString().substring(0, 8) + ".pptx";
    }

    private String uploadToMinio(String objectName, byte[] data, String contentType) {
        try (InputStream is = new ByteArrayInputStream(data)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(is, -1, 10485760)
                            .contentType(contentType)
                            .build()
            );
            String url = String.format("%s/%s/%s", endpoint, bucketName, objectName);
            log.info("文件上传成功, url={}", url);
            return url;
        } catch (Exception e) {
            log.error("文件上传失败, objectName={}", objectName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    private PptStructureDTO.TemplateConfig resolveConfig(PptStructureDTO structure) {
        PptStructureDTO.TemplateConfig cfg = structure.getTemplateConfig();
        if (cfg == null) {
            cfg = PptStructureDTO.TemplateConfig.builder()
                    .primaryColor("#546E7A")        // 封面/结束页：深灰蓝色（白字清晰）
                    .secondaryColor("#FFFFFF")      // 内容页标题：白色（适配深灰背景）
                    .backgroundColor("#37474F")     // 中间内容页：深灰背景（护眼 + 白字对比度高）
                    .titleFont("微软雅黑")
                    .contentFont("微软雅黑")
                    .titleFontSize(36)
                    .contentFontSize(20)
                    .build();
        }
        if (cfg.getSecondaryColor() == null || cfg.getSecondaryColor().isEmpty()) {
            cfg.setSecondaryColor("#FFFFFF");
        }
        return cfg;
    }

    /**
     * 单页内容密度分级（0 最松 → 3 最密）。
     * 密度越高：字号自动降得越多，越容易触发拆页。
     *
     * 【更激进的判定阈值（解决"还有几页超出"）】
     *   PPT 页面高只有 540px，正文区大约 380px，16号字行高≈21px，
     *   因此正文能容纳的行数≈16~18行——不要等真的塞不下才判定为高密度。
     */
    private int computeDensityLevel(PptStructureDTO.SlideDTO s) {
        int bulletChars = 0, bulletCount = 0;
        if (s.getBulletPoints() != null) {
            bulletCount = s.getBulletPoints().size();
            for (String b : s.getBulletPoints()) bulletChars += (b == null ? 0 : b.length());
        }
        int titleChars = s.getTitle() == null ? 0 : s.getTitle().length();
        int formulaChars = s.getFormula() == null ? 0 : s.getFormula().length();
        int highlightChars = 0;
        if (s.getHighlightPoints() != null) for (String h : s.getHighlightPoints()) highlightChars += (h == null ? 0 : h.length());
        int interChars = 0;
        PptStructureDTO.InteractionDTO inter = s.getInteraction();
        if (inter != null) {
            interChars += inter.getQuestion() == null ? 0 : inter.getQuestion().length();
            if (inter.getOptions() != null) for (String o : inter.getOptions()) interChars += (o == null ? 0 : o.length());
            interChars += inter.getAnswer() == null ? 0 : inter.getAnswer().length();
        }
        boolean hasImage = s.getImageSuggestion() != null && !s.getImageSuggestion().isEmpty();

        int totalChars = titleChars + bulletChars + formulaChars + highlightChars + interChars;

        // 【阈值大幅收紧：宁可多拆几页，也不要硬塞进一页导致叠字】
        int threshold1 = hasImage ? 140 : 220;  // L1：有图>140 / 无图>220 就开始降字号
        int threshold2 = hasImage ? 240 : 380;  // L2：有图>240 / 无图>380
        int threshold3 = hasImage ? 340 : 560;  // L3：有图>340 / 无图>560 → 触发拆页
        int bulletL1 = hasImage ? 3 : 4;        // bullet 数量阈值更紧：有图>3 / 无图>4 就 L1
        int bulletL2 = hasImage ? 5 : 7;        // bullet>5(有图) / >7(无图) 就 L2
        int bulletL3 = hasImage ? 7 : 9;        // bullet>7(有图) / >9(无图) 就 L3 → 强制拆页

        int level = 0;
        if (totalChars > threshold1 || bulletCount > bulletL1) level = 1;
        if (totalChars > threshold2 || bulletCount > bulletL2) level = 2;
        if (totalChars > threshold3 || bulletCount > bulletL3) level = 3;
        return level;
    }

    /** 根据密度级返回本页专用的字号
     *  注：实际正文绘制时还会按「行数是否溢出文本框」二次自适应缩小（见 computeAdaptiveFontSize）
     */
    private PptStructureDTO.TemplateConfig deriveFontsForDensity(PptStructureDTO.TemplateConfig global, int level) {
        PptStructureDTO.TemplateConfig page = PptStructureDTO.TemplateConfig.builder()
                .primaryColor(global.getPrimaryColor())
                .secondaryColor(global.getSecondaryColor())
                .backgroundColor(global.getBackgroundColor())
                .titleFont(global.getTitleFont())
                .contentFont(global.getContentFont())
                .titleFontSize(32)
                .contentFontSize(18) // 初始 18pt，后续自适应
                .build();
        return page;
    }

    // ============================================================
    //  【行数过多自动缩小字号】核心工具方法
    // ============================================================

    /** 估算：给定字号和文本框宽度，正文按宽度折行后的总视觉行数
     *  中文字宽约等于字号（磅）；按 1pt = 1.333px 换算，每行可放  textBoxWidthPx / (fontSizePt * 1.35) 个中文字
     */
    private int estimateWrappedLineCount(String text, int fontSizePt, int textBoxWidthPx) {
        if (text == null || text.isEmpty()) return 0;
        if (fontSizePt < 8) fontSizePt = 8;
        // 每行约能放多少个中文字（保守估计 1.38 倍字宽，留余量）
        double charsPerLine = textBoxWidthPx / (fontSizePt * 1.38);
        if (charsPerLine < 6) charsPerLine = 6;

        String[] hardLines = text.split("\n", -1);
        int totalVisualLines = 0;
        for (String line : hardLines) {
            if (line.isEmpty()) {
                totalVisualLines += 1; // 纯空行占 1 行
                continue;
            }
            // 按字符数估算折几行（中文 1 字、英文/半角 0.6 字来换算）
            double effectiveChars = 0.0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c <= 0x7F) effectiveChars += 0.6;
                else effectiveChars += 1.0;
            }
            int wrapped = (int) Math.ceil(effectiveChars / charsPerLine);
            if (wrapped < 1) wrapped = 1;
            totalVisualLines += wrapped;
        }
        return totalVisualLines;
    }

    /** 给定文本框尺寸，从 startPt 开始往下试，返回能装下全部内容的最大字号（不小于 minPt 保底） */
    private int computeAdaptiveFontSize(String text, int boxW, int boxH,
                                        int startPt, int minPt,
                                        double lineSpacingPct) {
        double lineSpacingMul = Math.max(1.0, lineSpacingPct / 100.0); // 120% → 1.2
        double ptToPx = 4.0 / 3.0;                                      // 1pt = 1.333px
        // 每行视觉总高（px）= fontSizePt × ptToPx × lineSpacingMul
        for (int pt = startPt; pt >= minPt; pt--) {
            int estimatedLines = estimateWrappedLineCount(text, pt, boxW);
            double lineHeightPx = pt * ptToPx * lineSpacingMul;
            // 总高 = 行数 × 行高 + 段后距总量（段数 × pt×0.2，粗略估计段数≈行数/1.4）
            double approxParaCount = Math.max(1.0, estimatedLines / 1.4);
            double spaceAfterTotalPx = approxParaCount * (pt * 0.2) * ptToPx;
            double totalHeightPx = estimatedLines * lineHeightPx + spaceAfterTotalPx;
            // 加 6px 顶部/底部安全边距
            if (totalHeightPx + 6.0 <= boxH) {
                return pt;
            }
        }
        return minPt; // 缩到最小号仍塞不下 → 保底，避免再小看不清
    }

    /**
     * slide 自动拆页：每页最多 4 条要点，字符无图>450 拆
     */
    private static final int SPLIT_BULLETS_PER_PAGE = 4;
    private static final int HARD_MAX_CHARS_WITH_IMAGE = 340;
    private static final int HARD_MAX_CHARS_NO_IMAGE = 450;

    private java.util.List<PptStructureDTO.SlideDTO> maybeSplit(PptStructureDTO.SlideDTO s) {
        java.util.List<String> all = s.getBulletPoints() == null ? java.util.List.of() : new java.util.ArrayList<>(s.getBulletPoints());
        int bullets = all.size();
        boolean hasImage = s.getImageSuggestion() != null && !s.getImageSuggestion().isEmpty();
        int totalChars = 0;
        if (s.getTitle() != null) totalChars += s.getTitle().length();
        for (String b : all) totalChars += (b == null ? 0 : b.length());
        if (s.getFormula() != null) totalChars += s.getFormula().length();
        if (s.getHighlightPoints() != null) for (String h : s.getHighlightPoints()) totalChars += (h == null ? 0 : h.length());
        PptStructureDTO.InteractionDTO inter = s.getInteraction();
        if (inter != null) {
            if (inter.getQuestion() != null) totalChars += inter.getQuestion().length();
            if (inter.getOptions() != null) for (String o : inter.getOptions()) totalChars += (o == null ? 0 : o.length());
            if (inter.getAnswer() != null) totalChars += inter.getAnswer().length();
        }
        int hardMax = hasImage ? HARD_MAX_CHARS_WITH_IMAGE : HARD_MAX_CHARS_NO_IMAGE;

        // 疯狂激进：bullet>2就拆、字符>240就拆、bullet数*字符>500就拆（任一满足）
        boolean needSplit = bullets > SPLIT_BULLETS_PER_PAGE || totalChars >= hardMax;
        if (!needSplit || all.isEmpty()) {
            return java.util.Collections.singletonList(s);
        }

        java.util.List<java.util.List<String>> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < all.size(); i += SPLIT_BULLETS_PER_PAGE) {
            chunks.add(new java.util.ArrayList<>(all.subList(i, Math.min(i + SPLIT_BULLETS_PER_PAGE, all.size()))));
        }
        // 如果最后一个 chunk 只有 1 条且 chunk 数>1，就并到倒数第二页去
        if (chunks.size() > 1 && chunks.get(chunks.size() - 1).size() <= 1) {
            java.util.List<String> last = chunks.remove(chunks.size() - 1);
            chunks.get(chunks.size() - 1).addAll(last);
        }

        java.util.List<PptStructureDTO.SlideDTO> out = new java.util.ArrayList<>();
        int total = chunks.size();
        for (int i = 0; i < total; i++) {
            boolean lastPage = (i == total - 1);
            PptStructureDTO.SlideDTO page = PptStructureDTO.SlideDTO.builder()
                    .pageNum(s.getPageNum())
                    .type(s.getType())
                    .title(lastPage ? s.getTitle() : (s.getTitle() + "（续 " + (i + 1) + "/" + total + "）"))
                    .bulletPoints(chunks.get(i))
                    .content(s.getContent())
                    .notes(s.getNotes())
                    .imageUrl(s.getImageUrl())
                    .formula(lastPage ? s.getFormula() : "")
                    .highlightPoints(lastPage ? s.getHighlightPoints() : java.util.List.of())
                    .interaction(lastPage ? s.getInteraction() : null)
                    .imageSuggestion(lastPage ? s.getImageSuggestion() : "")
                    .isFallback(s.getIsFallback())
                    .build();
            out.add(page);
        }
        return out;
    }

    private void buildTitleSlide(XMLSlideShow ppt, PptStructureDTO s, PptStructureDTO.TemplateConfig cfg) {
        XSLFSlide slide = ppt.createSlide();
        setBg(slide, cfg.getPrimaryColor());

        XSLFTextShape title = slide.createTextBox();
        title.setAnchor(new Rectangle(50, 150, SLIDE_W - 100, 100));
        setText(title, s.getTitle() != null ? s.getTitle() : "演示文稿", 44, Color.WHITE, cfg.getTitleFont(), true);

        XSLFTextShape subtitle = slide.createTextBox();
        subtitle.setAnchor(new Rectangle(50, 280, SLIDE_W - 100, 50));
        setText(subtitle, s.getDescription() != null ? s.getDescription() : "", 20, new Color(149, 165, 166), cfg.getContentFont(), false);
    }

    private void buildContentSlide(XMLSlideShow ppt, PptStructureDTO.SlideDTO slideDto, PptStructureDTO.TemplateConfig cfg) {
        // ——— 密度分级：动态降字号（每页独立派生，不改动全局 cfg） ———
        int density = computeDensityLevel(slideDto);
        PptStructureDTO.TemplateConfig pageCfg = deriveFontsForDensity(cfg, density);

        XSLFSlide slide = ppt.createSlide();
        setBg(slide, pageCfg.getBackgroundColor());

        String type = slideDto.getType() == null ? "content" : slideDto.getType().toLowerCase();

        // ——— 标题：高度压缩（60→52），顶部间距（30→25），多赚 13px 给正文 ———
        if (slideDto.getTitle() != null && !slideDto.getTitle().isEmpty()) {
            String prefix = switch (type) {
                case "summary"     -> "📌 ";
                case "homework"    -> "📝 ";
                case "interactive" -> "❓ ";
                default            -> "";
            };
            XSLFTextShape title = slide.createTextBox();
            title.setAnchor(new Rectangle(50, 25, SLIDE_W - 100, 52));
            setText(title, prefix + slideDto.getTitle(), pageCfg.getTitleFontSize(),
                    parseColor(pageCfg.getSecondaryColor()), pageCfg.getTitleFont(), true);
        }

        // ——— 正文：要点列表 或 纯正文 ———
        StringBuilder sb = new StringBuilder();
        if (slideDto.getBulletPoints() != null && !slideDto.getBulletPoints().isEmpty()) {
            for (int i = 0; i < slideDto.getBulletPoints().size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append("• ").append(slideDto.getBulletPoints().get(i));
            }
        } else if (slideDto.getContent() != null && !slideDto.getContent().isEmpty()) {
            sb.append(slideDto.getContent());
        }

        // ——— 公式 ———
        if (slideDto.getFormula() != null && !slideDto.getFormula().isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("✏️ 公式：").append(slideDto.getFormula());
        }

        // ——— 互动题 ———
        PptStructureDTO.InteractionDTO inter = slideDto.getInteraction();
        if (inter != null) {
            if (inter.getQuestion() != null && !inter.getQuestion().isEmpty()) {
                if (sb.length() > 0) sb.append("\n\n");
                String tag = "think_question".equalsIgnoreCase(inter.getType()) ? "【思考】" : "【提问】";
                sb.append(tag).append(inter.getQuestion());
            }
            if (inter.getOptions() != null && !inter.getOptions().isEmpty()) {
                sb.append("\n");
                String[] letters = {"A", "B", "C", "D", "E", "F", "G", "H"};
                for (int i = 0; i < inter.getOptions().size(); i++) {
                    String opt = inter.getOptions().get(i);
                    boolean alreadyPrefixed = false;
                    String letter = i < letters.length ? letters[i] : String.valueOf(i + 1);
                    if (opt != null && opt.length() > letter.length() + 1) {
                        String head = opt.substring(0, letter.length() + 1).trim().replace(".", "").replace("、", "");
                        if (letter.equalsIgnoreCase(head)) alreadyPrefixed = true;
                    }
                    sb.append("\n  ").append(alreadyPrefixed ? "○ " : "○ " + letter + ". ").append(opt);
                }
            }
            if (inter.getAnswer() != null && !inter.getAnswer().isEmpty()) {
                sb.append("\n\n✅ 参考答案：").append(inter.getAnswer());
            }
        }

        // ——— 高亮关键词 ———
        if (slideDto.getHighlightPoints() != null && !slideDto.getHighlightPoints().isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("✨关键词：").append(String.join("、", slideDto.getHighlightPoints()));
        }

        // ——— 正文布局：行距120%(加20%用户要求)，初始18pt，行数过多自动缩小
        int bodyY = 105;
        int tipBottomReserve = 95;           // 右下角配图建议 + 底部边距（tip缩小，正文更宽裕）
        int bodyW = SLIDE_W - 140;           // 左右各 70 边距
        int bodyH = SLIDE_H - bodyY - tipBottomReserve;

        // ============================================================
        //  【先把正文文本拼出来，用于行数估算→自适应字号】
        // ============================================================
        String rawText = sb.toString();

        // ——— 用户要求：20%行距 = 排版的"加 20%"= 总行距 120%；字号根据行数自适应
        final int START_PT = 18;             // 初始最大字号：18pt
        final int MIN_PT   = 12;             // 最小保底：12pt（再小就看不清了）
        double lineSpacingPct = 120.0;       // 行距：120% = 加 20%（用户要求）

        // ——— 【核心：行数过多自动缩小字号】———
        int fontSize = computeAdaptiveFontSize(rawText, bodyW, bodyH, START_PT, MIN_PT, lineSpacingPct);
        double spaceAfterPt = Math.max(5.0, fontSize * 0.2); // 段后距随字号动态调整

        XSLFTextShape content = slide.createTextBox();
        content.setAnchor(new Rectangle(70, bodyY, bodyW, bodyH));

        setText(content, rawText, fontSize, Color.WHITE, pageCfg.getContentFont(), false, lineSpacingPct, spaceAfterPt);

        if (log.isInfoEnabled()) {
            int estimatedLines = estimateWrappedLineCount(rawText, fontSize, bodyW);
            String shrinkNote = (fontSize < START_PT) ? " （多行溢出，自动从" + START_PT + "pt缩到" + fontSize + "pt）" : " （行数刚好，保持18pt）";
            log.info("📐 P{} 字号={}pt{} 视觉行数≈{} 行距={}%(加20%) 段后距={}pt(字号×0.2) bodyW={} bodyH={} chars={}",
                    slideDto.getPageNum(), fontSize, shrinkNote, estimatedLines,
                    (int) lineSpacingPct, String.format("%.1f", spaceAfterPt),
                    bodyW, bodyH, rawText.length());
        }

        // ——— 【按用户要求：配图建议缩小放右下角——再往右下挪一点】
        String tipText = "🎨 配图建议："
                + (slideDto.getImageSuggestion() == null || slideDto.getImageSuggestion().isEmpty()
                ? "—"
                : slideDto.getImageSuggestion());
        int tipSize = 10;                     // 字体缩小：12 → 10pt
        int tipW = 430;                       // 宽度再缩小：470 → 430（更紧凑利于靠右下角）
        int tipH = tipBottomReserve - 35;     // 高度也随之再缩小一点
        int tipX = SLIDE_W - 55 - tipW;       // 更靠右：右边距 70 → 55px
        int tipY = SLIDE_H - tipBottomReserve + 35; // 更靠下：+20 → +35（再往下挪15px）
        XSLFTextShape tip = slide.createTextBox();
        tip.setAnchor(new Rectangle(tipX, tipY, tipW, tipH));
        // 右下角小字：更淡的灰蓝、行距 120%、段后距 10×0.2=2pt（最小3pt）
        setText(tip, tipText, tipSize, new Color(160, 175, 185), pageCfg.getContentFont(), false, 120.0, Math.max(3.0, tipSize * 0.2));
    }

    private void buildEndSlide(XMLSlideShow ppt, PptStructureDTO s, PptStructureDTO.TemplateConfig cfg) {
        XSLFSlide slide = ppt.createSlide();
        setBg(slide, cfg.getPrimaryColor());

        XSLFTextShape thank = slide.createTextBox();
        thank.setAnchor(new Rectangle(180, 180, 600, 100));
        setText(thank, "谢谢观看", 56, Color.WHITE, cfg.getTitleFont(), true);

        XSLFTextShape sub = slide.createTextBox();
        sub.setAnchor(new Rectangle(180, 300, 600, 50));
        setText(sub, s.getTitle() != null ? s.getTitle() : "", 24, new Color(200, 200, 200), cfg.getContentFont(), false);
    }

    private void setText(XSLFTextShape shape, String text, Integer fontSize, Color color, String font, boolean bold) {
        // 用户要求：20% 行距 → 排版里的「加 20%」= 总行距 120%；段后距字号×0.2（最小5pt保底不挤）
        int size = fontSize != null ? fontSize : 18;
        setText(shape, text, fontSize, color, font, bold, 120.0, Math.max(5.0, size * 0.2));
    }

    /**
     * 核心 setText（8 参数版）：
     * ⚠️ POI 的 setLineSpacing(double) 参数单位是【百分比数值】！
     *   - 100 = 1 倍行距（紧贴）
     *   - 120 = 1.2 倍行距（=用户说的「20% 行距」，排版术语的"加 20%"）
     *   - 150 = 1.5 倍行距
     *   - 200 = 2 倍行距
     *
     * 段后距 spaceAfterPt：磅值，例如 3.6 = 3.6 磅段后距（= 18pt 字号 × 0.2）
     */
    private void setText(XSLFTextShape shape, String text, Integer fontSize, Color color, String font, boolean bold,
                         double lineSpacingPct, double spaceAfterPt) {
        int size = fontSize != null ? fontSize : 18;
        String safeText = (text == null) ? "" : text;

        // ——— 先写文本内容 ———
        shape.setText(safeText);

        // ——— 统一设置段落属性 + run 属性（对所有 paragraph/run 生效）
        java.util.List<XSLFTextParagraph> paras = shape.getTextParagraphs();
        for (int pi = 0; pi < paras.size(); pi++) {
            XSLFTextParagraph para = paras.get(pi);

            try {
                // ✅ 参数是百分比：120.0 = 120% 行距 = 1.2 倍（用户要求的 20% 行距）
                para.setLineSpacing(lineSpacingPct);
            } catch (Throwable ignore) {}
            try {
                // 段后距（磅）：字号 × 0.2 = 3.6pt
                para.setSpaceAfter(spaceAfterPt);
            } catch (Throwable ignore) {}
            try {
                // 段前距：仅首段给 1pt
                para.setSpaceBefore(pi == 0 ? 1.0 : 0.0);
            } catch (Throwable ignore) {}

            // ——— 设置字号、颜色、字体、粗体
            for (XSLFTextRun run : para.getTextRuns()) {
                run.setFontSize((double) size);
                run.setFontColor(color);
                if (font != null && !font.isEmpty()) run.setFontFamily(font);
                run.setBold(bold);
            }
        }
    }

    private void setBg(XSLFSlide slide, String colorHex) {
        try {
            slide.getBackground().setFillColor(parseColor(colorHex));
        } catch (Exception e) {
            log.warn("设置背景色失败: {}", colorHex);
        }
    }

    private Color parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.BLACK;
        try {
            String h = hex.replace("#", "");
            if (h.length() == 6) {
                return new Color(
                        Integer.parseInt(h.substring(0, 2), 16),
                        Integer.parseInt(h.substring(2, 4), 16),
                        Integer.parseInt(h.substring(4, 6), 16));
            }
        } catch (Exception ignored) {}
        return Color.BLACK;
    }

    private String buildObjectName(String fileName, String title) {
        String datePath = LocalDateTime.now().format(DATE_FMT);
        String name = fileName != null ? fileName : (title != null ? title : "presentation");
        return PPT_OBJECT_PREFIX + datePath + "/" + name + "-" + UUID.randomUUID().toString().substring(0, 8) + ".pptx";
    }
}
