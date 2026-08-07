import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;

public class PptxPreviewTest {
    @Test
    public void preview() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream("D:\\leapmind1\\氧化还原反应-正式引擎.pptx"))) {
            int i = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                BufferedImage img = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, 960, 540);
                slide.draw(g);
                g.dispose();
                ImageIO.write(img, "png", new File("D:\\leapmind1\\ppt-preview-" + i + ".png"));
                i++;
            }
            System.out.println("已导出 " + ppt.getSlides().size() + " 张预览图");
        }
    }
}
