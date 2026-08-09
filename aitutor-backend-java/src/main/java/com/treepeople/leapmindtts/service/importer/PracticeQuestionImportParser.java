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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
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
import javax.xml.parsers.DocumentBuilderFactory;

@Component
public class PracticeQuestionImportParser {

    public static final String SUPPORTED_FORMATS = ".xlsx, .xls, .csv, .docx, .doc, .pdf";

    private static final int MAX_QUESTIONS = 1000;
    private static final List<String> COLUMN_ORDER = List.of(
            "subject", "gradeLevel", "track", "chapter", "knowledgePoint", "questionType",
            "difficulty", "title", "content", "optionA", "optionB", "optionC", "optionD",
            "correctAnswer", "answerKeywords", "analysis", "lessonId", "status"
    );
    private static final Pattern OPTION_START_PATTERN = Pattern.compile("(?<![A-Za-z])([A-Ha-h])\\s*[.．、)]\\s*");
    private static final Pattern OPTION_LINE_PATTERN = Pattern.compile("^\\(?\\s*([A-Ha-h])\\s*[).．、]\\s*(.*)$");
    private static final Pattern NUMBERED_QUESTION_PATTERN = Pattern.compile("^(?:第\\s*)?(\\d{1,3})\\s*[.．、]\\s*(.+)$");
    private static final Pattern SUB_QUESTION_PATTERN = Pattern.compile("^[（(]\\s*\\d+\\s*[）)]\\s*(.+)$");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9 _-]{0,30})\\s*[:：]\\s*(.*)$");
    private static final Pattern ANSWER_ITEM_PATTERN = Pattern.compile("(\\d{1,3})\\s*[.．、:]\\s*([A-Ha-h,，、;；]+|[^\\s，。；;]+)");
    private static final Pattern SECTION_PATTERN = Pattern.compile("^[一二三四五六七八九十]+[、.]\\s*(.+)$");

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
            default -> throw new IllegalArgumentException("Only " + SUPPORTED_FORMATS + " files are supported");
        };
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("No questions were recognized. Use the template columns or a numbered exam paper format.");
        }
        if (questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("A single import supports at most " + MAX_QUESTIONS + " questions");
        }
        return questions;
    }

    private List<PracticeQuestion> parseSpreadsheet(MultipartFile file) throws Exception {
        List<PracticeQuestion> result = new ArrayList<>();
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) return result;
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
                if (row == null) continue;
                Map<String, String> values = hasMappedHeader
                        ? valuesFromHeader(headerMap, index -> formatter.formatCellValue(row.getCell(index), evaluator))
                        : valuesByPosition(index -> formatter.formatCellValue(row.getCell(index), evaluator));
                if (!isBlankRow(values)) result.add(toQuestion(values, result.size() + 1, "spreadsheet"));
            }
        }
        return result;
    }

    private List<PracticeQuestion> parseCsv(MultipartFile file) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (StringUtils.hasText(line)) rows.add(splitCsv(line));
            }
        }
        if (rows.isEmpty()) return List.of();
        rows.get(0).set(0, stripBom(rows.get(0).get(0)));
        Map<String, Integer> headerMap = headerMap(index -> cell(rows.get(0), index), rows.get(0).size());
        boolean hasMappedHeader = isStructuredHeader(headerMap);
        int start = hasMappedHeader ? 1 : 0;
        List<PracticeQuestion> result = new ArrayList<>();
        for (int rowIndex = start; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            Map<String, String> values = hasMappedHeader
                    ? valuesFromHeader(headerMap, index -> cell(row, index))
                    : valuesByPosition(index -> cell(row, index));
            if (!isBlankRow(values)) result.add(toQuestion(values, result.size() + 1, "csv"));
        }
        return result;
    }

    private List<PracticeQuestion> parseDocx(MultipartFile file) throws Exception {
        try (InputStream input = file.getInputStream(); XWPFDocument document = new XWPFDocument(input)) {
            List<PracticeQuestion> tableQuestions = parseDocxTables(document);
            if (!tableQuestions.isEmpty()) return tableQuestions;

            StringBuilder text = new StringBuilder();
            ImageCounter imageCounter = new ImageCounter();
            for (XWPFParagraph paragraph : document.getParagraphs()) appendLine(text, paragraphText(paragraph, imageCounter));
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    appendLine(text, row.getTableCells().stream()
                            .map(cell -> cellTextWithRichContent(cell, imageCounter))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .reduce((a, b) -> a + " " + b)
                            .orElse(""));
                }
            }
            return parseStructuredText(text.toString(), "word");
        }
    }

    private String cellTextWithRichContent(XWPFTableCell cell, ImageCounter imageCounter) {
        return cell.getParagraphs().stream()
                .map(paragraph -> paragraphText(paragraph, imageCounter))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + " " + b)
                .orElseGet(cell::getText);
    }

    private String paragraphText(XWPFParagraph paragraph, ImageCounter imageCounter) {
        String xml = paragraph.getCTP().xmlText();
        String richText = xmlToImportText(xml, imageCounter);
        return StringUtils.hasText(richText) ? richText : paragraph.getText();
    }

    private String xmlToImportText(String xml, ImageCounter imageCounter) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            StringBuilder builder = new StringBuilder();
            appendNodeText(document.getDocumentElement(), builder, imageCounter, false);
            return normalizeInlineText(builder.toString());
        } catch (Exception ignored) {
            return "";
        }
    }

    private void appendNodeText(Node node, StringBuilder builder, ImageCounter imageCounter, boolean inMath) {
        String localName = node.getLocalName();
        if ("drawing".equals(localName) || "pict".equals(localName)) {
            appendSeparated(builder, "[image" + imageCounter.next() + "]");
            return;
        }
        if ("oMath".equals(localName) || "oMathPara".equals(localName)) {
            String math = collectMathText(node);
            if (StringUtils.hasText(math)) appendSeparated(builder, "$" + math + "$");
            return;
        }
        if ("t".equals(localName)) {
            builder.append(node.getTextContent());
            return;
        }
        if ("tab".equals(localName) || "br".equals(localName) || "cr".equals(localName)) {
            builder.append(' ');
            return;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            appendNodeText(child, builder, imageCounter, inMath || localName != null && localName.startsWith("oMath"));
        }
    }

    private String collectMathText(Node node) {
        StringBuilder builder = new StringBuilder();
        collectMathText(node, builder);
        return normalizeInlineText(builder.toString())
                .replace("×", "\\times ")
                .replace("÷", "\\div ");
    }

    private void collectMathText(Node node, StringBuilder builder) {
        String localName = node.getLocalName();
        if ("t".equals(localName)) {
            builder.append(node.getTextContent());
            return;
        }
        if ("f".equals(localName)) {
            Node numerator = childElement(node, "num");
            Node denominator = childElement(node, "den");
            if (numerator != null && denominator != null) {
                builder.append('(');
                collectMathText(numerator, builder);
                builder.append(")/(");
                collectMathText(denominator, builder);
                builder.append(')');
                return;
            }
        }
        if ("rad".equals(localName)) {
            builder.append("sqrt(");
            Node expression = childElement(node, "e");
            if (expression != null) collectMathText(expression, builder);
            builder.append(')');
            return;
        }
        if ("sSup".equals(localName)) {
            Node base = childElement(node, "e");
            Node sup = childElement(node, "sup");
            if (base != null) collectMathText(base, builder);
            builder.append('^');
            if (sup != null) collectMathText(sup, builder);
            return;
        }
        if ("sSub".equals(localName)) {
            Node base = childElement(node, "e");
            Node sub = childElement(node, "sub");
            if (base != null) collectMathText(base, builder);
            builder.append('_');
            if (sub != null) collectMathText(sub, builder);
            return;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectMathText(child, builder);
        }
    }

    private Node childElement(Node node, String localName) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) return child;
        }
        return null;
    }

    private void appendSeparated(StringBuilder builder, String value) {
        if (!builder.isEmpty() && !Character.isWhitespace(builder.charAt(builder.length() - 1))) builder.append(' ');
        builder.append(value).append(' ');
    }

    private String normalizeInlineText(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private List<PracticeQuestion> parseDocxTables(XWPFDocument document) {
        List<PracticeQuestion> result = new ArrayList<>();
        for (XWPFTable table : document.getTables()) {
            List<XWPFTableRow> rows = table.getRows();
            if (rows.isEmpty()) continue;
            List<XWPFTableCell> headerCells = rows.get(0).getTableCells();
            Map<String, Integer> headerMap = headerMap(index -> cellText(headerCells, index), headerCells.size());
            if (!isStructuredHeader(headerMap)) continue;
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();
                Map<String, String> values = valuesFromHeader(headerMap, index -> cellText(cells, index));
                if (!isBlankRow(values)) result.add(toQuestion(values, result.size() + 1, "word table"));
            }
        }
        return result;
    }

    private List<PracticeQuestion> parseDoc(MultipartFile file) throws Exception {
        try (InputStream input = file.getInputStream();
             HWPFDocument document = new HWPFDocument(input);
             WordExtractor extractor = new WordExtractor(document)) {
            return parseStructuredText(extractor.getText(), "word");
        }
    }

    private List<PracticeQuestion> parsePdf(MultipartFile file) throws Exception {
        try (InputStream input = file.getInputStream(); PDDocument document = PDDocument.load(input)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return parseStructuredText(stripper.getText(document), "pdf");
        }
    }

    private List<PracticeQuestion> parseStructuredText(String rawText, String sourceName) {
        String text = rawText == null ? "" : rawText.replace("\r\n", "\n").replace('\r', '\n');
        Map<Integer, String> answers = extractAnswers(text);
        List<QuestionDraft> drafts = new ArrayList<>();
        QuestionDraft current = null;
        String currentSection = "";
        String currentType = "";

        for (String rawLine : text.split("\\n")) {
            String line = normalizeLine(rawLine);
            if (!StringUtils.hasText(line)) continue;
            if (line.matches(".*(注意事项|考试时间|满分|姓名|班级|学号|得分).*")) continue;
            if (line.matches("^答[:：]?$")) continue;

            Matcher section = SECTION_PATTERN.matcher(line);
            if (section.matches()) {
                currentSection = section.group(1).trim();
                currentType = inferTypeFromSection(currentSection);
                continue;
            }

            Matcher numbered = NUMBERED_QUESTION_PATTERN.matcher(line);
            if (numbered.matches()) {
                if (current != null && current.hasContent()) drafts.add(current);
                current = new QuestionDraft(Integer.parseInt(numbered.group(1)), currentSection, currentType);
                current.appendContent(numbered.group(2));
                continue;
            }

            if (current == null) {
                current = parseTaggedLineIntoDraft(line, currentSection, currentType);
                continue;
            }

            if (appendOptions(current, line)) continue;

            Matcher field = FIELD_PATTERN.matcher(line);
            if (field.matches()) {
                String canonical = canonicalHeader(field.group(1));
                if (canonical != null) {
                    if (("content".equals(canonical) || "title".equals(canonical)) && current.hasContent()) {
                        drafts.add(current);
                        current = new QuestionDraft(null, currentSection, currentType);
                    }
                    current.put(canonical, field.group(2));
                    continue;
                }
            }

            Matcher subQuestion = SUB_QUESTION_PATTERN.matcher(line);
            if (subQuestion.matches() || current.hasContent()) {
                current.appendContent(line);
            }
        }
        if (current != null && current.hasContent()) drafts.add(current);

        List<PracticeQuestion> result = new ArrayList<>();
        for (QuestionDraft draft : drafts) {
            if (draft.number != null && answers.containsKey(draft.number)) {
                draft.put("correctAnswer", answers.get(draft.number));
            }
            Map<String, String> values = draft.values();
            if (!isBlankRow(values)) result.add(toQuestion(values, result.size() + 1, sourceName));
        }
        return result;
    }

    private QuestionDraft parseTaggedLineIntoDraft(String line, String section, String type) {
        Matcher field = FIELD_PATTERN.matcher(line);
        if (!field.matches()) return null;
        String canonical = canonicalHeader(field.group(1));
        if (canonical == null) return null;
        QuestionDraft draft = new QuestionDraft(null, section, type);
        draft.put(canonical, field.group(2));
        return draft;
    }

    private boolean appendOptions(QuestionDraft draft, String line) {
        Matcher first = OPTION_START_PATTERN.matcher(line);
        if (!first.find()) return false;
        List<MatcherHit> hits = new ArrayList<>();
        first.reset();
        while (first.find()) hits.add(new MatcherHit(first.start(), first.end(), first.group(1).toUpperCase(Locale.ROOT)));
        if (hits.isEmpty()) return false;
        for (int i = 0; i < hits.size(); i++) {
            MatcherHit hit = hits.get(i);
            int end = i + 1 < hits.size() ? hits.get(i + 1).start : line.length();
            String value = line.substring(hit.end, end).trim();
            if (!StringUtils.hasText(value)) value = hit.option;
            draft.put("option" + hit.option, value);
        }
        return true;
    }

    private Map<Integer, String> extractAnswers(String text) {
        Map<Integer, String> answers = new HashMap<>();
        int answerStart = Math.max(text.indexOf("答案"), text.indexOf("参考答案"));
        if (answerStart < 0) answerStart = text.indexOf("答：");
        if (answerStart < 0) return answers;
        Matcher matcher = ANSWER_ITEM_PATTERN.matcher(text.substring(answerStart));
        while (matcher.find()) {
            try {
                answers.put(Integer.parseInt(matcher.group(1)), matcher.group(2).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return answers;
    }

    private PracticeQuestion toQuestion(Map<String, String> values, int index, String sourceName) {
        PracticeQuestion question = new PracticeQuestion();
        String content = firstText(values.get("content"), values.get("title"));
        if (!StringUtils.hasText(content)) throw new IllegalArgumentException(sourceName + " question " + index + " is missing content");
        String questionType = normalizeQuestionType(values.get("questionType"), values);
        String title = firstText(values.get("title"), abbreviate(content, 36));
        String answer = trim(values.get("correctAnswer"));
        boolean pendingAnswer = !StringUtils.hasText(answer);

        question.setSubject(defaultText(values.get("subject"), "数学"));
        question.setGradeLevel(defaultText(values.get("gradeLevel"), "八升九"));
        question.setTrack(defaultText(values.get("track"), question.getSubject()));
        question.setChapter(defaultText(values.get("chapter"), "文档导入"));
        question.setKnowledgePoint(defaultText(values.get("knowledgePoint"), defaultKnowledgePoint(question.getChapter())));
        question.setQuestionType(questionType);
        question.setDifficulty(normalizeDifficulty(values.get("difficulty")));
        question.setTitle(title);
        question.setContent(content);
        question.setOptionA(defaultOption(values.get("optionA"), "A", questionType));
        question.setOptionB(defaultOption(values.get("optionB"), "B", questionType));
        question.setOptionC(defaultOption(values.get("optionC"), "C", questionType));
        question.setOptionD(defaultOption(values.get("optionD"), "D", questionType));
        question.setCorrectAnswer(pendingAnswer ? "待补充" : normalizeAnswer(answer, questionType));
        question.setAnswerKeywords(trim(values.get("answerKeywords")));
        question.setAnalysis(defaultText(values.get("analysis"), ""));
        question.setLessonId(trim(values.get("lessonId")));
        question.setStatus(pendingAnswer ? "DISABLED" : normalizeStatus(values.get("status")));
        question.setCreatedAt(LocalDateTime.now());
        return question;
    }

    private String defaultOption(String value, String fallback, String questionType) {
        if (!"SINGLE_CHOICE".equals(questionType) && !"MULTIPLE_CHOICE".equals(questionType)) return trim(value);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String inferTypeFromSection(String section) {
        String normalized = section == null ? "" : section;
        if (normalized.contains("选择")) return "SINGLE_CHOICE";
        if (normalized.contains("填空")) return "FILL_BLANK";
        if (normalized.contains("解答") || normalized.contains("计算") || normalized.contains("证明")) return "SHORT_ANSWER";
        return "";
    }

    private String defaultKnowledgePoint(String chapter) {
        if (!StringUtils.hasText(chapter)) return "未分类";
        String value = chapter.replaceAll("[（(].*?[）)]", "").replaceAll("\\s+", "");
        return StringUtils.hasText(value) ? value : "未分类";
    }

    private Map<String, Integer> headerMap(IntFunction<String> reader, int count) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String canonical = canonicalHeader(reader.apply(index));
            if (canonical != null) result.putIfAbsent(canonical, index);
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
        for (int index = 0; index < COLUMN_ORDER.size(); index++) values.put(COLUMN_ORDER.get(index), trim(reader.apply(index)));
        return values;
    }

    private boolean isBlankRow(Map<String, String> values) {
        return values.values().stream().noneMatch(StringUtils::hasText);
    }

    private String normalizeQuestionType(String raw, Map<String, String> values) {
        String value = normalizeToken(raw);
        if (value.contains("MULTIPLE") || value.contains("MULTI") || value.contains("多选")) return "MULTIPLE_CHOICE";
        if (value.contains("SINGLE") || value.contains("选择") || value.contains("单选")) return "SINGLE_CHOICE";
        if (value.contains("FILL") || value.contains("填空")) return "FILL_BLANK";
        if (value.contains("SHORT") || value.contains("简答") || value.contains("问答") || value.contains("解答")) return "SHORT_ANSWER";
        if (StringUtils.hasText(values.get("optionA")) && StringUtils.hasText(values.get("optionB"))) return "SINGLE_CHOICE";
        String content = defaultText(values.get("content"), "");
        return content.contains("____") || content.contains("______") || content.contains("________") ? "FILL_BLANK" : "SHORT_ANSWER";
    }

    private String normalizeDifficulty(String raw) {
        String value = normalizeToken(raw);
        long stars = value.chars().filter(character -> character == '★' || character == '*').count();
        if (value.contains("HARD") || value.contains("困难") || value.contains("高难") || stars >= 5) return "HARD";
        if (value.contains("ADVANCED") || value.contains("MEDIUM") || value.contains("中等") || value.contains("偏上") || value.contains("进阶") || stars >= 3) return "ADVANCED";
        return "BASIC";
    }

    private String normalizeStatus(String raw) {
        String value = normalizeToken(raw);
        if (value.contains("DISABLED") || value.contains("停用") || value.contains("禁用") || value.contains("审核")) return "DISABLED";
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
        register(aliases, "chapter", "chapter", "章节", "章", "模块");
        register(aliases, "knowledgePoint", "knowledgepoint", "知识点", "考点");
        register(aliases, "questionType", "questiontype", "type", "题型", "类型");
        register(aliases, "difficulty", "difficulty", "难度", "难易度");
        register(aliases, "title", "title", "标题", "题目标题");
        register(aliases, "content", "content", "stem", "question", "题干", "题目", "问题");
        register(aliases, "optionA", "optiona", "选项a", "a选项", "A");
        register(aliases, "optionB", "optionb", "选项b", "b选项", "B");
        register(aliases, "optionC", "optionc", "选项c", "c选项", "C");
        register(aliases, "optionD", "optiond", "选项d", "d选项", "D");
        register(aliases, "correctAnswer", "correctanswer", "answer", "答案", "正确答案", "参考答案", "标准答案");
        register(aliases, "answerKeywords", "answerkeywords", "答案关键词", "关键词", "评分关键词");
        register(aliases, "analysis", "analysis", "解析", "答案解析", "说明");
        register(aliases, "lessonId", "lessonid", "课程id", "课时id");
        register(aliases, "status", "status", "状态");
        return aliases;
    }

    private static void register(Map<String, String> aliases, String canonical, String... names) {
        for (String name : names) aliases.put(normalizeHeaderToken(name), canonical);
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
        if (StringUtils.hasText(value)) builder.append(value.trim()).append('\n');
    }

    private static String normalizeLine(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
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
        return value.substring(0, maxLength) + "...";
    }

    private record MatcherHit(int start, int end, String option) {
    }

    private static class ImageCounter {
        private int value;

        int next() {
            value += 1;
            return value;
        }
    }

    private static class QuestionDraft {
        private final Integer number;
        private final Map<String, String> fields = new LinkedHashMap<>();
        private final StringBuilder content = new StringBuilder();

        QuestionDraft(Integer number, String chapter, String questionType) {
            this.number = number;
            if (number != null) fields.put("title", "第 " + number + " 题");
            if (StringUtils.hasText(chapter)) fields.put("chapter", chapter);
            if (StringUtils.hasText(questionType)) fields.put("questionType", questionType);
            fields.putIfAbsent("subject", "数学");
            fields.putIfAbsent("gradeLevel", "八升九");
            fields.putIfAbsent("difficulty", "ADVANCED");
        }

        void put(String key, String value) {
            if (StringUtils.hasText(value)) fields.put(key, value.trim());
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
            if (content.length() > 0) values.merge("content", content.toString(), (existing, appended) -> existing + " " + appended);
            return values;
        }
    }
}
