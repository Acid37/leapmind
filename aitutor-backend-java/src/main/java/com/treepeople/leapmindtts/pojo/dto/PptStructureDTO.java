package com.treepeople.leapmindtts.pojo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PptStructureDTO {

    /** 备课ID（Python的generate-ppt返回的pptId，值等于prepId） */
    private Integer pptId;

    /** 演示文稿总标题（如Python只传slides而没传title，则会从type=cover的slide提取） */
    private String title;

    /** 副标题（封面页下方） */
    private String description;

    /** 幻灯片列表（可能包含type=cover的封面页，导出时会自动跳过封面页，避免和buildTitleSlide重复） */
    private List<SlideDTO> slides;

    /** 模板样式配置（可选，null时使用默认蓝色主题） */
    private TemplateConfig templateConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SlideDTO {

        /** 页码，从1开始 */
        private Integer pageNum;

        /**
         * 幻灯片类型（枚举）：
         * cover      封面页 → 导出时作为整个PPT的title/description来源，不单独画成内容页
         * content    内容页 → 正常要点讲解
         * interactive互动页 → 附加展示question/options/answer
         * summary    总结页 → 标题前加"📌"标记
         * homework   课后作业页 → 标题前加"📝"标记
         */
        private String type;

        /** 单页标题 */
        private String title;

        /** 纯正文（bulletPoints为空时使用，支持\n换行） */
        private String content;

        /** 要点列表，1~7条，每行前自动加项目符号 */
        private List<String> bulletPoints;

        /** 教师讲解词（Stage3输出，用于TTS） */
        private String notes;

        /** 配图URL（保留） */
        private String imageUrl;

        // ---------- 以下是Python generate-ppt 新增返回字段 ----------

        /** 配图提示文案（可供AI生图或前端占位，POI渲染时暂不画图片） */
        private String imageSuggestion;

        /** LaTeX公式（POI渲染时在正文下方单独显示一行加粗） */
        private String formula;

        /** 需要高亮的关键词列表（在正文底部追加"关键词：xxx、yyy"形式） */
        private List<String> highlightPoints;

        /** 互动题（type=interactive时不为null） */
        private InteractionDTO interaction;

        /** AI生成失败回退标记（true时前端显示重试提示，POI导出照常） */
        private Boolean isFallback;
    }

    /**
     * 互动题结构（type=interactive时使用）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
   
    public static class InteractionDTO {
        /** 互动类型：choice_question / think_question / practice */
        private String type;
        /** 问题文本 */
        private String question;
        /** 选项列表（选择题必填） */
        private List<String> options;
        /** 正确答案或参考答案 */
        private String answer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TemplateConfig {

        private String primaryColor;

        private String secondaryColor;

        private String backgroundColor;

        private String titleFont;

        private String contentFont;

        private Integer titleFontSize;

        private Integer contentFontSize;
    }

    /**
     * 统一解析入口（推荐 Service/Controller 都走这里）。
     *
     * <ol>
     *   <li>第 1 次：按 <code>PptStructureDTO</code> 顶层对象解析（正常路径：
     *       <code>{"pptId":N,"slides":[...]}</code>）。</li>
     *   <li>若第 1 次失败 <b>且</b> JSON 顶层 Trim 后以 <code>'['</code> 开头，
     *       视为「纯 slides 数组」的历史脏数据（Python 直接把数组落库），
     *       <b>纯内存包装</b>为 <code>{"slides":[...]}</code> 再解析 1 次，
     *       不回写 DB、不改 Python、不做命名转换。</li>
     * </ol>
     *
     * @param om   Jackson ObjectMapper（建议由 Spring 注入复用）
     * @param json 原 JSON 字符串
     * @return 解析后的 PptStructureDTO，不会为 null
     * @throws RuntimeException 两种格式都解析失败时抛出（由上层统一转 HTTP 500）
     */
    public static PptStructureDTO parse(ObjectMapper om, String json) {
        if (om == null) throw new IllegalArgumentException("ObjectMapper 不能为 null");
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("PPT 结构 JSON 不能为空");
        }
        RuntimeException firstErr = null;
        // —— 1. 正常路径：顶层 {pptId, slides} 对象 ——
        try {
            PptStructureDTO dto = om.readValue(json, PptStructureDTO.class);
            if (dto != null) return dto;
        } catch (Exception ex) {
            firstErr = new RuntimeException("按 PptStructureDTO 顶层对象解析失败: " + ex.getMessage(), ex);
        }
        // —— 2. 兜底路径：DB 存的是纯 slides 数组，内存包装成 {"slides":[...]} 再试 ——
        String trimmed = json.trim();
        if (trimmed.charAt(0) == '[') {
            String wrapped = "{\"slides\":" + trimmed + "}";
            try {
                PptStructureDTO dto = om.readValue(wrapped, PptStructureDTO.class);
                if (dto != null) return dto;
            } catch (Exception ex2) {
                RuntimeException wrap = new RuntimeException(
                        "按 PptStructureDTO 解析失败，且纯数组→对象内存包装后再次解析失败: " + ex2.getMessage(), ex2);
                if (firstErr != null) wrap.addSuppressed(firstErr);
                throw wrap;
            }
        }
        if (firstErr != null) throw firstErr;
        throw new RuntimeException("解析 PPT 结构 JSON 失败（未知格式）");
    }
}
