package com.treepeople.leapmindtts.service.importer;

import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.util.Units;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void importsNaturalExamDocxWithoutTemplateHeaders() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("八升九数学综合能力测试卷");
            document.createParagraph().createRun().setText("一、选择题（本大题共2小题）");
            document.createParagraph().createRun().setText("1. 下列二次根式中，与 sqrt(12) 是同类二次根式的是（　　）");
            document.createParagraph().createRun().setText("A. sqrt(3)　　B. sqrt(5)　　C. sqrt(7)　　D. sqrt(11)");
            document.createParagraph().createRun().setText("2. 下列条件中，能判定一个四边形是平行四边形的是（　　）");
            document.createParagraph().createRun().setText("A. 一组对边相等　　B. 一组对边平行　　C. 两条对角线互相平分　　D. 两条对角线相等");
            document.createParagraph().createRun().setText("二、填空题（本大题共1小题）");
            document.createParagraph().createRun().setText("3. 计算：2 + 3 = ________。");
            document.write(output);
            bytes = output.toByteArray();
        }

        List<PracticeQuestion> questions = parser.parse(new MockMultipartFile("file", "exam.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes));

        assertEquals(3, questions.size());
        assertEquals("SINGLE_CHOICE", questions.get(0).getQuestionType());
        assertEquals("FILL_BLANK", questions.get(2).getQuestionType());
        assertEquals("DISABLED", questions.get(0).getStatus());
    }

    @Test
    void importsDocxMathLettersAndPicturePlaceholders() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("1. 如图，已知 ");
            document.getParagraphs().get(0).getCTP().set(CTP.Factory.parse("""
                    <w:p xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                         xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">
                      <w:r><w:t>1. 如图，已知 </w:t></w:r>
                      <m:oMath>
                        <m:sSup>
                          <m:e><m:r><m:t>x</m:t></m:r></m:e>
                          <m:sup><m:r><m:t>2</m:t></m:r></m:sup>
                        </m:sSup>
                      </m:oMath>
                      <w:r><w:t> + AB = 0，求 AB。</w:t></w:r>
                    </w:p>
                    """));
            byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");
            document.createParagraph().createRun().addPicture(
                    new ByteArrayInputStream(png),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                    "figure.png",
                    Units.toEMU(16),
                    Units.toEMU(16));
            document.write(output);
            bytes = output.toByteArray();
        }

        List<PracticeQuestion> questions = parser.parse(new MockMultipartFile("file", "rich.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes));

        assertEquals(1, questions.size());
        String content = questions.get(0).getContent();
        assertTrue(content.contains("$x^2$"), content);
        assertTrue(content.contains("AB"), content);
        assertTrue(content.contains("[image1]"), content);
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
