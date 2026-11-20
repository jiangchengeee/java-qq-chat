import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/** 对渲染出来的截图做像素级检查，替代肉眼观察 */
public class PixelCheck {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  [通过] " : "  [失败] ") + name + "  " + detail);
        if (ok) pass++; else fail++;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File("/tmp/uishots/01-login.png"));
        int w = img.getWidth(), h = img.getHeight();
        System.out.println("图片尺寸: " + w + "x" + h);

        // 1) 顶部应该是蓝色渐变
        int top = img.getRGB(w / 2, 20);
        Color c1 = new Color(top);
        check("顶部为蓝色调", c1.getBlue() > 180 && c1.getRed() < 100,
                String.format("RGB(%d,%d,%d)", c1.getRed(), c1.getGreen(), c1.getBlue()));

        // 2) 头部区域不同高度颜色应该不同（渐变）
        Color cTop = new Color(img.getRGB(w / 2, 10));
        Color cBot = new Color(img.getRGB(w / 2, 160));
        check("头部存在渐变", !cTop.equals(cBot),
                "顶部=" + hex(cTop) + " 底部=" + hex(cBot));

        // 3) 表单区域应该是浅灰背景
        Color form = new Color(img.getRGB(10, 300));
        check("表单区为浅色背景", form.getRed() > 230 && form.getGreen() > 230 && form.getBlue() > 230,
                hex(form));

        // 4) 登录按钮应该是蓝色块
        Color btn = new Color(img.getRGB(w / 2, 410));
        check("登录按钮为蓝色", btn.getBlue() > 180 && btn.getRed() < 120, hex(btn));

        // 5) 找一下白色文字（logo "Java-QQ"）——头部里应该有白色像素
        int whiteCount = 0;
        for (int y = 40; y < 130; y++) {
            for (int x = 0; x < w; x++) {
                Color c = new Color(img.getRGB(x, y));
                if (c.getRed() > 240 && c.getGreen() > 240 && c.getBlue() > 240) {
                    whiteCount++;
                }
            }
        }
        check("头部有白色文字像素", whiteCount > 500, "白色像素数=" + whiteCount);

        // 6) 整体不应该是一张纯色图（说明确实画了东西）
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < h; y += 4) {
            for (int x = 0; x < w; x += 4) {
                colors.add(img.getRGB(x, y));
            }
        }
        check("画面有足够多的颜色", colors.size() > 50, "采样颜色种类=" + colors.size());

        // 7) 登录失败截图里应该有红色提示文字
        BufferedImage fail = ImageIO.read(new File("/tmp/uishots/02-login-fail.png"));
        int redCount = 0;
        for (int y = 340; y < 400; y++) {
            for (int x = 0; x < w; x++) {
                Color c = new Color(fail.getRGB(x, y));
                if (c.getRed() > 150 && c.getGreen() < 90 && c.getBlue() < 90) {
                    redCount++;
                }
            }
        }
        check("失败提示为红色文字", redCount > 20, "红色像素数=" + redCount);

        System.out.println("\n通过 " + pass + " 项，失败 " + fail + " 项");
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
