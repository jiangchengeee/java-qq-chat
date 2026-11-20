import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import javax.imageio.ImageIO;

/**
 * 完整闭环测试：真实连服务端登录 → 真实收发消息 → 渲染各界面并截图。
 *
 * 两个坑记录一下：
 * 1. 等网络结果时不能在 EDT 上 sleep，否则 invokeLater 的回调排不上队
 * 2. 离屏截图时只给顶层 setSize 不够，子组件必须自顶向下拿到尺寸才能画出来
 */
public class UiFlow {

    static QQClient app;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        new File("/tmp/uishots").mkdirs();

        // ---------- 1) 在 EDT 上创建窗口、填表、登录 ----------
        SwingUtilities.invokeAndWait(() -> {
            try {
                Constructor<QQClient> ctor =
                        QQClient.class.getDeclaredConstructor(String.class, int.class);
                ctor.setAccessible(true);
                app = ctor.newInstance("127.0.0.1", 18888);

                setField(app, "nameField", new JTextField("刘泽成"));
                setField(app, "passField", new JPasswordField("123456"));

                Method doLogin = QQClient.class.getDeclaredMethod("doLogin");
                doLogin.setAccessible(true);
                doLogin.invoke(app);
                System.out.println("已触发登录…");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // ---------- 2) 主线程等待，EDT 保持空闲 ----------
        Thread.sleep(2000);

        SwingUtilities.invokeAndWait(() -> {
            try {
                System.out.println("登录后 当前用户: " + ((JLabel) getField(app, "selfLabel")).getText());
                System.out.println("登录后 好友列表: " + dumpModel());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // ---------- 3) 另起两个真实客户端：李四上线、王五发消息 ----------
        startPeer("李四");
        Thread.sleep(1000);
        startSender("王五", "刘泽成", "这是一条真实发过来的测试消息");
        Thread.sleep(1500);

        // ---------- 4) 回 EDT 检查并截图 ----------
        SwingUtilities.invokeAndWait(() -> {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, ChatWindow> chats =
                        (java.util.Map<String, ChatWindow>) getField(app, "openChats");
                System.out.println("已打开的聊天窗口数: " + chats.size());

                ChatWindow chat;
                if (!chats.isEmpty()) {
                    chat = chats.values().iterator().next();
                    System.out.println("聊天对象: " + chat.getTitle());
                } else {
                    throw new IllegalStateException("真实消息没有自动开窗");
                }

                // 补几条用于展示完整对话
                chat.appendOutgoing("收到了，网络这边是通的");
                chat.appendIncoming("李四", "界面也做出来了？");
                chat.appendOutgoing("嗯，登录、好友列表、聊天窗口都有了");
                chat.appendSystem("已发送文件：实验数据.txt（1024 字节）");

                JPanel chatPane = (JPanel) chat.getContentPane();
                pairLayout(chatPane, 560, 620);
                shoot(chatPane, "/tmp/uishots/04-chat.png", 560, 620);

                JPanel cardPanel = (JPanel) getField(app, "cardPanel");
                JPanel friendsCard = (JPanel) cardPanel.getComponent(1);
                pairLayout(friendsCard, 360, 520);
                dumpTree(friendsCard, 0);
                shoot(friendsCard, "/tmp/uishots/03-friends.png", 360, 520);

                System.out.println("最终好友列表: " + dumpModel());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        System.out.println("界面渲染完成");
        System.exit(0);
    }

    // ================= 布局与截图 =================

    /** 自顶向下传递尺寸，然后逐层布局 */
    static void pairLayout(Container root, int w, int h) {
        root.setSize(w, h);
        sizeDown(root, w, h);
    }

    private static void sizeDown(Container parent, int w, int h) {
        parent.setSize(w, h);
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) {
                Container c = (Container) child;
                int cw = c.getWidth(), ch = c.getHeight();
                if (cw > 0 && ch > 0) {
                    sizeDown(c, cw, ch);
                }
            }
        }
    }

    static void dumpTree(Component c, int depth) {
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < depth; i++) pad.append("  ");
        String extra = "";
        if (c instanceof JList) extra = " model=" + ((JList<?>) c).getModel().getSize();
        if (c instanceof JLabel) extra = " text=" + ((JLabel) c).getText();
        System.out.println(pad + c.getClass().getSimpleName() + " " + c.getWidth() + "x"
                + c.getHeight() + " vis=" + c.isVisible() + extra);
        if (c instanceof Container && depth < 5) {
            for (Component ch : ((Container) c).getComponents()) dumpTree(ch, depth + 1);
        }
    }

    static void shoot(JComponent comp, String path, int w, int h) throws Exception {
        pairLayout(comp, w, h);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        comp.paint(g2);
        g2.dispose();
        ImageIO.write(img, "png", new File(path));
        System.out.println("已保存 " + path + " (" + w + "x" + h + ")");
    }

    // ================= 真实客户端线程 =================

    static void startPeer(String name) {
        Thread t = new Thread(() -> {
            try {
                java.net.Socket s = new java.net.Socket("127.0.0.1", 18888);
                java.io.PrintWriter out = new java.io.PrintWriter(
                        new java.io.OutputStreamWriter(s.getOutputStream(),
                                java.nio.charset.StandardCharsets.UTF_8), true);
                out.println("LOGIN " + name);
                java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(s.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8));
                while (in.readLine() != null) {
                    // 保持在线
                }
            } catch (Exception ignored) {
            }
        }, "peer-" + name);
        t.setDaemon(true);
        t.start();
    }

    static void startSender(String name, String target, String text) {
        Thread t = new Thread(() -> {
            try {
                java.net.Socket s = new java.net.Socket("127.0.0.1", 18888);
                java.io.PrintWriter out = new java.io.PrintWriter(
                        new java.io.OutputStreamWriter(s.getOutputStream(),
                                java.nio.charset.StandardCharsets.UTF_8), true);
                out.println("LOGIN " + name);
                Thread.sleep(400);
                out.println("MSG " + target + " " + text);
                out.flush();
            } catch (Exception ignored) {
            }
        }, "sender-" + name);
        t.setDaemon(true);
        t.start();
    }

    // ================= 反射工具 =================

    static void setField(Object target, String name, Object value) throws Exception {
        Field f = QQClient.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static Object getField(Object target, String name) throws Exception {
        Field f = QQClient.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    static String dumpModel() throws Exception {
        DefaultListModel<?> model = (DefaultListModel<?>) getField(app, "friendModel");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < model.size(); i++) {
            sb.append("[").append(model.get(i)).append("]");
        }
        return sb.length() == 0 ? "(空)" : sb.toString();
    }
}
