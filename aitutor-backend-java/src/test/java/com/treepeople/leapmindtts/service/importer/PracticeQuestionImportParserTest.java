package com.treepeople.leapmindtts.service.importer;

import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PracticeQuestionImportParserTest {

    private static final List<String> HEADERS = List.of(
            "subject", "gradeLevel", "track", "chapter", "knowledgePoint", "questionType",
            "difficulty", "title", "content", "optionA", "optionB", "optionC", "optionD",
            "correctAnswer", "answerKeywords", "analysis", "lessonId", "status"
    );

    private final PracticeQuestionImportParser parser = new PracticeQuestionImportParser();

    @Test
    void importsXlsxTemplate() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Questions");
            var header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) header.createCell(index).setCellValue(HEADERS.get(index));
            var row = sheet.createRow(1);
            String[] values = {"数学", "大学", "高数", "导数", "导数定义", "SHORT_ANSWER", "BASIC", "导数含义",
                    "请说明导数的几何意义。", "", "", "", "", "切线斜率", "切线;斜率", "导数表示切线斜率。", "", "ENABLED"};
            for (int index = 0; index < values.length; index++) row.createCell(index).setCellValue(values[index]);
            workbook.write(output);
            bytes = output.toByteArray();
        }

        List<PracticeQuestion> questions = parser.parse(new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes));

        assertEquals(1, questions.size());
        assertEquals("请说明导数的几何意义。", questions.get(0).getContent());
        assertEquals("ENABLED", questions.get(0).getStatus());
    }

    @Test
    void importsWordTable() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFTable table = document.createTable(2, HEADERS.size());
            for (int index = 0; index < HEADERS.size(); index++) table.getRow(0).getCell(index).setText(HEADERS.get(index));
            String[] values = {"数学", "大学", "高数", "极限", "重要极限", "FILL_BLANK", "ADVANCED", "极限填空",
                    "lim x->0 sin(x)/x = ____。", "", "", "", "", "1", "", "重要极限", "", "ENABLED"};
            for (int index = 0; index < values.length; index++) table.getRow(1).getCell(index).setText(values[index]);
            document.write(output);
            bytes = output.toByteArray();
        }

        List<PracticeQuestion> questions = parser.parse(new MockMultipartFile("file", "questions.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes));

        assertEquals(1, questions.size());
        assertEquals("FILL_BLANK", questions.get(0).getQuestionType());
        assertEquals("重要极限", questions.get(0).getKnowledgePoint());
    }

    @Test
    void ignoresWordDocumentHeadingBeforeLabeledQuestion() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("LeapMind Word 题库导入测试");
            document.createParagraph().createRun().setText("科目：数学");
            document.createParagraph().createRun().setText("知识点：导数计算");
            document.createParagraph().createRun().setText("题目：若 f(x)=x²，求 f'(3)。");
            document.createParagraph().createRun().setText("答案：6");
            document.write(output);
            bytes = output.toByteArray();
        }

        List<PracticeQuestion> questions = parser.parse(new MockMultipartFile("file", "labeled.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes));

        assertEquals(1, questions.size());
        assertEquals("若 f(x)=x²，求 f'(3)。", questions.get(0).getContent());
    }

    @Test
    void importsTextPdf() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 11);
                content.setLeading(16);
                content.newLineAtOffset(60, 740);
                for (String line : List.of(
                        "Question: What is 2 + 2?",
                        "Answer: 4",
                        "Subject: Math",
                        "Chapter: Arithmetic",
                        "KnowledgePoint: Addition",
                        "QuestionType: SHORT_ANSWER",
                        "Difficulty: BASIC")) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(output);
            bytes = output.toByteArray();
        }

        List<PracticeQuestion> questions = parser.parse(new MockMultipartFile("file", "questions.pdf", "application/pdf", bytes));

        assertEquals(1, questions.size());
        assertEquals("What is 2 + 2?", questions.get(0).getContent());
        assertEquals("4", questions.get(0).getCorrectAnswer());
    }

    @Test
    void importsCsvAndMarksMissingAnswerForReview() throws Exception {
        String csv = "题目,题型,章节,知识点\n请解释矩阵的秩。,简答,线性代数,矩阵的秎\n";
        List<PracticeQuestion> questions = parser.parse(new MockMultipartFile("file", "questions.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, questions.size());
        assertEquals("DISABLED", questions.get(0).getStatus());
        assertEquals("待补充", questions.get(0).getCorrectAnswer());
    }
}
