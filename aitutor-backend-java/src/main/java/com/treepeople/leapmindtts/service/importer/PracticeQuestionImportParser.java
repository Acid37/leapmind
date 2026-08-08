package com.treepeople.leapmindtts.service.importer;

import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PracticeQuestionImportParser {

    public static final String SUPPORTED_FORMATS = ".xlsx、.xls、.csv、.docx、.doc、.pdf";

    private static final int MAX_QUESTIONS = 1000;
    private static final List<String> COLUMN_ORDER = List.of(
            "subject", "gradeLevel", "track", "chapter", "knowledgePoint", "questionType",
            "difficulty", "title", "content", "optionA", "optionB", "optionC", "optionD",
            "correctAnswer", "answerKeywords", "analysis", "lessonId", "status"
    );
    private static final Pattern OPTION_PATTERN = Pattern.compile("^[（(]?([A-Da-d])[）).．、:：]\\s*(.+)$");
    private static final Pattern NUMBERED_QUESTION_PATTERN = Pattern.compile("^(?:第\\s*)?(\\d+)\\s*[.．、)）:]\\s*(.+)$");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9 _-]{0,30})\\s*[:：]\\s*(.*)$");
    private static final Map<String, String> HEADER_ALIASES = buildHeaderAliases();

    public List<PracticeQuestion> parse(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extensionOf(filename);
        List<PracticeQuestion> questions = switch (extension) {
            case "xlsx", "xls" -> parseSpreadsheet(file);
            case "csv" -> parseCsv(file);
            case "docx" -> parseDocx(file);
            case "doc" -> parseDoc(file);
            case "pdf" -> parsePdf(file);
            default -> throw new IllegalArgumentException("仅支持 " + SUPPORTED_FORMATS + " 文件");
        };
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("没有识别到题目。Excel/Word 表格请使用模板字段；Word/PDF 文本请包含“题目：”和“答案：”等标签");
        }
        if (questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("单次最多导入 " + MAX_QUESTIONS + " 道题目");
        }
        return questions;
    }

    private List<PracticeQuestion> parseSpreadsheet(MultipartFile file) throws Exception {
        List<PracticeQuestion> result = new ArrayList<>();
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                return result;
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> headerMap = headerRow == null
                    ? Map.of()
                    : headerMap(index -> formatter.formatCellValue(headerRow.getCell(index), evaluator), headerRow.getLastCellNum());
            boolean hasMappedHeader = isStructuredHeader(headerMap);
            int startRow = hasMappedHeader ? sheet.getFirstRowNum() + 1 : sheet.getFirstRowNum();
            for (int rowIndex = startRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = hasMappedHeader
                        ? valuesFromHeader(headerMap, index -> formatter.formatCellValue(row.getCell(index), evaluator))
                        : valuesByPosition(index -> formatter.formatCellValue(row.getCell(index), evaluator));
                if (!isBlankRow(values)) {
                    result.add(toQuestion(values, result.size() + 1, "表格导入"));
                }
            }
        }
        return result;
    }

    private List<PracticeQuestion> parseCsv(MultipartFile file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (StringUtils.hasText(line)) {
                    rows.add(splitCsv(line));
                }
            }
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        if (!rows.get(0).isEmpty()) {
            rows.get(0).set(0, stripBom(rows.get(0).get(0)));
        }
        Map<String, Integer> headerMap = headerMap(index -> cell(rows.get(0), index), rows.get(0).size());
        boolean hasMappedHeader = isStructuredHeader(headerMap);
        int start = hasMappedHeader ? 1 : 0;
        List<PracticeQuestion> result = new ArrayList<>();
        for (int rowIndex = start; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            Map<String, String> values = hasMappedHeader
                    ? valuesFromHeader(headerMap, index -> cell(row, index))
                    : valuesByPosition(index -> cell(row, index));
            if (!isBlankRow(values)) {
                result.add(toQuestion(values, result.size() + 1, "CSV 导入"));
            }
        }
        return result;
    }

    private List<PracticeQuestion> parseDocx(MultipartFile file) throws Exception {
        try (InputStream input = file.getInputStream(); XWPFDocument document = new XWPFDocument(input)) {
            List<PracticeQuestion> tableQuestions = new ArrayList<>();
            for (XWPFTable table : document.getTables()) {
                List<XWPFTableRow> rows = table.getRows();
                if (rows.isEmpty()) {
                    continue;
                }
                List<XWPFTableCell> headerCells = rows.get(0).getTableCells();
                Map<String, Integer> headerMap = headerMap(index -> cellText(headerCells, index), headerCells.size());
                if (!isStructuredHeader(headerMap)) {
                    continue;
                }
                for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                    List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();
                    Map<String, String> values = valuesFromHeader(headerMap, index -> cellText(cells, index));
                    if (!isBlankRow(values)) {
                        tableQuestions.add(toQuestion(values, tableQuestions.size() + 1, "Word 表格"));
                    }
                }
            }
            if (!tableQuestions.isEmpty()) {
                return tableQuestions;
            }

            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                appendLine(text, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<XWPFTableCell> cells = row.getTableCells();
                    if (cells.size() == 2) {
                        appendLine(text, cellText(cells, 0) + "：" + cellText(cells, 1));
                    } else {
                        appendLine(text, cells.stream().map(XWPFTableCell::getText).reduce((a, b) -> a + " " + b).orElse(""));
                    }
                }
            }
            return parseStructuredText(text.toString(), "Word 文档");
        }
    }

    private List<PracticeQuestion> parseDoc(MultipartFile file) throws Exception {
        try (InputStream input = file.getInputStream();
             HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return parseStructuredText(extractor.getText(), "Word 文档");
        }
    }

    private List<PracticeQuestion> parsePdf(MultipartFile file) throws Exception {
        try (InputStream input = file.getInputStream(); PDDocument document = PDDocument.load(input)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return parseStructuredText(stripper.getText(document), "PDF 文档");
        }
    }

    private List<PracticeQuestion> parseStructuredText(String rawText, String sourceName) {
        List<QuestionDraft> drafts = new ArrayList<>();
        QuestionDraft current = new QuestionDraft();
        boolean questionStarted = false;
        String normalizedText = rawText == null ? "" : rawText.replace("\r\n", "\n").replace('\r', '\n');
        for (String rawLine : normalizedText.split("\\n")) {
            String line = rawLine.trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }

            Matcher optionMatcher = OPTION_PATTERN.matcher(line);
            if (optionMatcher.matches()) {
                if (questionStarted || current.hasContent()) {
                    current.put("option" + optionMatcher.group(1).toUpperCase(Locale.ROOT), optionMatcher.group(2));
                }
                continue;
            }

            Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
            if (fieldMatcher.matches()) {
                String canonical = canonicalHeader(fieldMatcher.group(1));
                if (canonical != null) {
                    if (("content".equals(canonical) || "title".equals(canonical)) && current.hasContent()) {
                        drafts.add(current);
                        current = new QuestionDraft();
                    }
                    current.put(canonical, fieldMatcher.group(2));
                    if ("content".equals(canonical) || "title".equals(canonical)) {
                        questionStarted = true;
                    }
                    continue;
                }
            }

            Matcher questionMatcher = NUMBERED_QUESTION_PATTERN.matcher(line);
            if (questionMatcher.matches()) {
                if (current.hasContent()) {
                    drafts.add(current);
                    current = new QuestionDraft();
                }
                current.put("title", "第 " + questionMatcher.group(1) + " 题");
                current.appendContent(questionMatcher.group(2));
                questionStarted = true;
                continue;
            }

            if (questionStarted || current.hasContent()) {
                current.appendContent(line);
            }
        }
        if (current.hasContent()) {
            drafts.add(current);
        }

        List<PracticeQuestion> result = new ArrayList<>();
        for (QuestionDraft draft : drafts) {
            Map<String, String> values = draft.values();
            if (!isBlankRow(values)) {
                result.add(toQuestion(values, result.size() + 1, sourceName));
            }
        }
        return result;
    }

    private PracticeQuestion toQuestion(Map<String, String> values, int index, String sourceName) {
        PracticeQuestion question = new PracticeQuestion();
        String content = firstText(values.get("content"), values.get("title"));
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException(sourceName + "第 " + index + " 题缺少题干");
        }
        String title = firstText(values.get("title"), abbreviate(content, 36));
        String answer = trim(values.get("correctAnswer"));
        boolean pendingAnswer = !StringUtils.hasText(answer);
        String questionType = normalizeQuestionType(values.get("questionType"), values);

        question.setSubject(defaultText(values.get("subject"), "通用"));
        question.setGradeLevel(defaultText(values.get("gradeLevel"), "大学"));
        question.setTrack(defaultText(values.get("track"), question.getSubject()));
        question.setChapter(defaultText(values.get("chapter"), "文档导入"));
        question.setKnowledgePoint(defaultText(values.get("knowledgePoint"), "未分类"));
        question.setQuestionType(questionType);
        question.setDifficulty(normalizeDifficulty(values.get("difficulty")));
        question.setTitle(title);
        question.setContent(content);
        question.setOptionA(trim(values.get("optionA")));
        question.setOptionB(trim(values.get("optionB")));
        question.setOptionC(trim(values.get("optionC")));
        question.setOptionD(trim(values.get("optionD")));
        question.setCorrectAnswer(pendingAnswer ? "待补充" : normalizeAnswer(answer, questionType));
        question.setAnswerKeywords(trim(values.get("answerKeywords")));
        question.setAnalysis(defaultText(values.get("analysis"), ""));
        question.setLessonId(trim(values.get("lessonId")));
        question.setStatus(pendingAnswer ? "DISABLED" : normalizeStatus(values.get("status")));
        question.setCreatedAt(LocalDateTime.now());
        return question;
    }

    private Map<String, Integer> headerMap(IntFunction<String> reader, int count) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String canonical = canonicalHeader(reader.apply(index));
            if (canonical != null) {
                result.putIfAbsent(canonical, index);
            }
        }
        return result;
    }

    private boolean isStructuredHeader(Map<String, Integer> headerMap) {
        return headerMap.containsKey("content") && headerMap.size() >= 4;
    }

    private Map<String, String> valuesFromHeader(Map<String, Integer> headerMap, IntFunction<String> reader) {
        Map<String, String> values = new LinkedHashMap<>();
        headerMap.forEach((key, index) -> values.put(key, trim(reader.apply(index))));
        return values;
    }

    private Map<String, String> valuesByPosition(IntFunction<String> reader) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < COLUMN_ORDER.size(); index++) {
            values.put(COLUMN_ORDER.get(index), trim(reader.apply(index)));
        }
        return values;
    }

    private boolean isBlankRow(Map<String, String> values) {
        return values.values().stream().noneMatch(StringUtils::hasText);
    }

    private String normalizeQuestionType(String raw, Map<String, String> values) {
        String value = normalizeToken(raw);
        if (value.contains("MULTIPLE") || value.contains("MULTI") || value.contains("多选")) return "MULTIPLE_CHOICE";
        if (value.contains("SINGLE") || value.contains("单选")) return "SINGLE_CHOICE";
        if (value.contains("FILL") || value.contains("填空")) return "FILL_BLANK";
        if (value.contains("SHORT") || value.contains("简答") || value.contains("问答")) return "SHORT_ANSWER";
        if (StringUtils.hasText(values.get("optionA")) && StringUtils.hasText(values.get("optionB"))) return "SINGLE_CHOICE";
        String content = defaultText(values.get("content"), "");
        return content.contains("____") || content.contains("______") ? "FILL_BLANK" : "SHORT_ANSWER";
    }

    private String normalizeDifficulty(String raw) {
        String value = normalizeToken(raw);
        long stars = value.chars().filter(character -> character == '★').count();
        if (value.contains("HARD") || value.contains("困难") || value.contains("高难") || stars >= 5) return "HARD";
        if (value.contains("ADVANCED") || value.contains("MEDIUM") || value.contains("中等") || value.contains("进阶") || stars >= 3) return "ADVANCED";
        return "BASIC";
    }

    private String normalizeStatus(String raw) {
        String value = normalizeToken(raw);
        if (value.contains("DISABLED") || value.contains("停用") || value.contains("禁用")) return "DISABLED";
        return "ENABLED";
    }

    private String normalizeAnswer(String answer, String questionType) {
        String normalized = trim(answer);
        if (("SINGLE_CHOICE".equals(questionType) || "MULTIPLE_CHOICE".equals(questionType)) && normalized != null) {
            return normalized.toUpperCase(Locale.ROOT).replace("，", ",").replace("、", ",");
        }
        return normalized;
    }

    private static Map<String, String> buildHeaderAliases() {
        Map<String, String> aliases = new HashMap<>();
        register(aliases, "subject", "subject", "科目", "学科");
        register(aliases, "gradeLevel", "gradelevel", "grade", "年级", "学段");
        register(aliases, "track", "track", "方向", "考试", "题库");
        register(aliases, "chapter", "chapter", "章节", "章");
        register(aliases, "knowledgePoint", "knowledgepoint", "知识点", "考点");
        register(aliases, "questionType", "questiontype", "type", "题型", "类型");
        register(aliases, "difficulty", "difficulty", "难度", "难易度");
        register(aliases, "title", "title", "标题", "题目标题");
        register(aliases, "content", "content", "stem", "question", "题干", "题目", "问题");
        register(aliases, "optionA", "optiona", "选项a", "a选项");
        register(aliases, "optionB", "optionb", "选项b", "b选项");
        register(aliases, "optionC", "optionc", "选项c", "c选项");
        register(aliases, "optionD", "optiond", "选项d", "d选项");
        register(aliases, "correctAnswer", "correctanswer", "answer", "答案", "正确答案", "参考答案", "标准答案");
        register(aliases, "answerKeywords", "answerkeywords", "答案关键词", "关键词", "评分关键词");
        register(aliases, "analysis", "analysis", "解析", "答案解析", "说明");
        register(aliases, "lessonId", "lessonid", "课程id", "课时id");
        register(aliases, "status", "status", "状态");
        return aliases;
    }

    private static void register(Map<String, String> aliases, String canonical, String... names) {
        for (String name : names) {
            aliases.put(normalizeHeaderToken(name), canonical);
        }
    }

    private static String canonicalHeader(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        return HEADER_ALIASES.get(normalizeHeaderToken(stripBom(raw)));
    }

    private static String normalizeHeaderToken(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-（）()【】\\[\\]]", "");
    }

    private static String normalizeToken(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static List<String> splitCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        result.add(current.toString());
        return result;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String cell(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : "";
    }

    private static String cellText(List<XWPFTableCell> cells, int index) {
        return index >= 0 && index < cells.size() ? trim(cells.get(index).getText()) : "";
    }

    private static void appendLine(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(value.trim()).append('\n');
        }
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : trim(second);
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String stripBom(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").trim();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "…";
    }

    private static class QuestionDraft {
        private final Map<String, String> fields = new LinkedHashMap<>();
        private final StringBuilder content = new StringBuilder();

        void put(String key, String value) {
            if (StringUtils.hasText(value)) {
                fields.put(key, value.trim());
            }
        }

        void appendContent(String value) {
            if (!StringUtils.hasText(value)) return;
            if (content.length() > 0) content.append(' ');
            content.append(value.trim());
        }

        boolean hasContent() {
            return content.length() > 0 || StringUtils.hasText(fields.get("content"));
        }

        Map<String, String> values() {
            Map<String, String> values = new LinkedHashMap<>(fields);
            if (content.length() > 0) {
                values.merge("content", content.toString(), (existing, appended) -> existing + " " + appended);
            }
            return values;
        }
    }
}
