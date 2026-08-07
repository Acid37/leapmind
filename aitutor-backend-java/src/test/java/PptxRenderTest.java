import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.LeapMindTtsApplication;
import com.treepeople.leapmindtts.pojo.dto.PptStructureDTO;
import com.treepeople.leapmindtts.service.PptxExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 临时测试：用正式 PptxExportServiceImpl.generatePptxBytes 引擎
 * 生成「氧化还原反应」（MySQL py_teaching_contents id=1）的 .pptx，
 * 不走 MinIO 上传，直接写本地。
 */
@SpringBootTest(classes = LeapMindTtsApplication.class)
public class PptxRenderTest {

    @Autowired
    private PptxExportService pptxExportService;

    @Test
    public void renderRedoxPptx() throws Exception {
        // 1. 从 MySQL py_teaching_contents 读 ppt_structure_json（snake_case）
        String json = readPptJson();
        System.out.println("读到的JSON长度: " + json.length());

        // 2. 反序列化为 PptStructureDTO（含 slides）
        ObjectMapper om = new ObjectMapper();
        PptStructureDTO structure = om.readValue(
                "{\"title\":\"氧化还原反应\",\"slides\":" + json + "}",
                PptStructureDTO.class);

        // 3. 调用正式引擎的 generatePptxBytes（反射访问，因它在实现类非接口方法）
        Method method = pptxExportService.getClass().getMethod("generatePptxBytes",
                PptStructureDTO.class, Long.class);
        byte[] pptx = (byte[]) method.invoke(pptxExportService, structure, null);

        // 4. 写本地
        String outPath = "D:\\leapmind1\\氧化还原反应-正式引擎.pptx";
        Files.write(Paths.get(outPath), pptx);
        System.out.println("已生成: " + outPath + " (" + pptx.length + " 字节)");

        // 5. 验证页数
        try (org.apache.poi.xslf.usermodel.XMLSlideShow ppt =
                     new org.apache.poi.xslf.usermodel.XMLSlideShow(new java.io.ByteArrayInputStream(pptx))) {
            System.out.println("PPTX 页数: " + ppt.getSlides().size());
        }
    }

    private String readPptJson() throws Exception {
        // 直接查询 MySQL（避免引入额外依赖，用 JDBC 或直接从文件读）
        // 简化：从之前导出的 prep7 文件读（内容相同：氧化还原反应 11 页）
        // 这里改为从 MySQL py_teaching_contents id=1 读
        java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/leapmind-voice?useUnicode=true&characterEncoding=utf8",
                "root", "1234");
        try (var st = conn.createStatement();
             var rs = st.executeQuery("SELECT ppt_structure_json FROM py_teaching_contents WHERE id=1")) {
            if (rs.next()) return rs.getString(1);
        }
        throw new IllegalStateException("MySQL 无数据");
    }
}
