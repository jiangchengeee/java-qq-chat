import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.lang.reflect.Method;

/**
 * 把界面渲染成 PNG 图片，用来检查布局效果。
 * 不依赖窗口管理器，直接让组件自己画到 BufferedImage 上。
 */
public class UiShot {

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        new File("/tmp/uishots").mkdirs();

        SwingUtilities.invokeAndWait(() -> {
            try {
                // ---- 登录界面 ----
                final QQClient[] app = new QQClient[1];
                app[0] = (QQClient) createClient();
                JPanel loginCard = getCard(app[0], 0);
                loginCard.setSize(360, 520);
                loginCard.doLayout();
                layoutDeep(loginCard);
                shoot(loginCard, "/tmp/uishots/01-login.png");

                // ---- 登录失败提示 ----
                Method tip = QQClient.class.getDeclaredMethod("showLoginTip", String.class);
                tip.setAccessible(true);
                tip.invoke(app[0], "该用户名已在线，请换一个");
                layoutDeep(loginCard);
                shoot(loginCard, "/tmp/uishots/02-login-fail.png");

                System.out.println("登录界面截图完成");
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
    }

    static Object createClient() throws Exception {
        java.lang.reflect.Constructor<?> c = QQClient.class.getDeclaredConstructor(String.class, int.class);
        c.setAccessible(true);
        Object o = c.newInstance("127.0.0.1", 8888);
        System.out.println("QQClient 创建成功: " + o.getClass().getName());
        return o;
    }

    /** 从 CardLayout 里取出指定序号的卡片 */
    static JPanel getCard(JFrame frame, int index) throws Exception {
        java.lang.reflect.Field f = QQClient.class.getDeclaredField("cardPanel");
        f.setAccessible(true);
        JPanel cardPanel = (JPanel) f.get(frame);
        Component[] comps = cardPanel.getComponents();
        return (JPanel) comps[index];
    }

    static void layoutDeep(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof Container) {
                layoutDeep((Container) child);
            }
        }
    }

    static void shoot(JComponent comp, String path) throws Exception {
        BufferedImage img = new BufferedImage(comp.getWidth(), comp.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        comp.paint(g2);
        g2.dispose();
        ImageIO.write(img, "png", new File(path));
        System.out.println("已保存 " + path + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
