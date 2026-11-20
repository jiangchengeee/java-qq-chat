import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 端到端测试客户端（无界面）。
 * 用法：先启动 Server，再运行本类。
 *   java Server 18888
 *   java NetTest 18888
 */
public class NetTest {

    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  [通过] " : "  [失败] ") + name);
        if (ok) pass++; else fail++;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 18888;

        System.out.println("=== 测试1：登录与在线列表 ===");
        C a = new C(port, "张三");
        Thread.sleep(300);
        C b = new C(port, "李四");
        Thread.sleep(400);
        check("张三登录成功", a.loginOk);
        check("李四登录成功", b.loginOk);
        check("李四的列表含张三", b.list.contains("张三"));
        check("李四的列表含自己", b.list.contains("李四"));

        System.out.println("\n=== 测试2：重名登录被拒绝 ===");
        C dup = new C(port, "张三");
        Thread.sleep(400);
        check("重名登录返回 LOGIN_FAIL", !dup.loginOk && dup.failReason != null);
        System.out.println("  提示：" + dup.failReason);

        System.out.println("\n=== 测试3：点对点消息经服务端中转 ===");
        int before = b.msgCount();
        a.send("MSG 李四 你好李四，我是张三");
        Thread.sleep(400);
        check("李四收到消息", b.msgCount() == before + 1);
        check("发送者正确", "张三".equals(b.lastFrom));
        check("内容正确", "你好李四，我是张三".equals(b.lastContent));

        System.out.println("\n=== 测试4：并行通信（连发不等回复）===");
        int base = b.msgCount();
        for (int i = 1; i <= 5; i++) {
            a.send("MSG 李四 第" + i + "条");
        }
        Thread.sleep(700);
        check("5 条全部送达", b.msgCount() == base + 5);
        System.out.println("  李四收到顺序：" + b.contentsSince(base));

        System.out.println("\n=== 测试5：群发 ===");
        C c = new C(port, "王五");
        Thread.sleep(400);
        int bn = b.msgCount(), cn = c.msgCount(), an = a.msgCount();
        a.send("GROUP 大家好，这是群消息");
        Thread.sleep(500);
        check("李四收到群消息", b.msgCount() == bn + 1);
        check("王五收到群消息", c.msgCount() == cn + 1);
        check("张三自己不收到", a.msgCount() == an);
        check("群消息带 [群] 标记", b.lastContent.startsWith("[群]"));
        System.out.println("  李四收到：" + b.lastFrom + " -> " + b.lastContent);

        System.out.println("\n=== 测试6：文件传输 张三 -> 李四 ===");
        File src = new File("testfile.txt");
        String content = "这是一次文件传输测试。\n内容要一字不差地送到对面。\n第三行中文也要正常。\n";
        Files.write(src.toPath(), content.getBytes(StandardCharsets.UTF_8));

        a.sendFile("李四", src);
        Thread.sleep(1500);
        check("李四收到文件", b.lastFile != null && b.lastFile.exists());
        if (b.lastFile != null && b.lastFile.exists()) {
            String received = new String(Files.readAllBytes(b.lastFile.toPath()), StandardCharsets.UTF_8);
            check("文件大小一致", b.lastFile.length() == src.length());
            check("文件内容一致", content.equals(received));
        }

        System.out.println("\n=== 测试7：下线后列表更新 ===");
        c.logout();
        Thread.sleep(600);
        check("李四列表中已无王五", !b.list.contains("王五"));

        System.out.println("\n=== 测试8：给不在线的人发消息 ===");
        int an2 = a.sysCount();
        a.send("MSG 王五 你还在吗");
        Thread.sleep(400);
        check("服务端回了系统提示", a.sysCount() > an2);
        System.out.println("  提示：" + a.lastSys);

        System.out.println("\n=== 测试9：长消息与特殊字符 ===");
        String longMsg = "长消息测试：" + "啊".repeat(300);
        int bn2 = b.msgCount();
        a.send("MSG 李四 " + longMsg);
        Thread.sleep(500);
        check("长消息送达", b.msgCount() == bn2 + 1);
        check("长消息内容完整", longMsg.equals(b.lastContent));

        System.out.println("\n=========================================");
        System.out.println("  通过 " + pass + " 项，失败 " + fail + " 项");
        System.out.println("=========================================");

        src.delete();
        System.exit(fail == 0 ? 0 : 1);
    }

    /** 简易测试客户端：独立线程收消息，记录结构化结果 */
    static class C {
        Socket socket;
        BufferedReader in;
        PrintWriter out;
        String name;

        volatile boolean loginOk = false;
        volatile String failReason = null;
        volatile String list = "";
        volatile String lastFrom = "";
        volatile String lastContent = "";
        volatile String lastSys = "";
        volatile File lastFile = null;

        final List<String> contents = Collections.synchronizedList(new ArrayList<>());
        final List<String> sysMsgs = Collections.synchronizedList(new ArrayList<>());

        C(int port, String name) throws IOException {
            this.name = name;
            socket = new Socket("127.0.0.1", port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            Thread t = new Thread(this::loop, "c-" + name);
            t.setDaemon(true);
            t.start();
            login();
        }

        void loop() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    String[] p = line.split(" ", 2);
                    String cmd = p[0];
                    String rest = p.length > 1 ? p[1] : "";

                    switch (cmd) {
                        case "LOGIN_OK":
                            loginOk = true;
                            break;
                        case "LOGIN_FAIL":
                            failReason = rest;
                            break;
                        case "LIST":
                            list = rest;
                            break;
                        case "MSG": {
                            String[] m = rest.split(" ", 2);
                            lastFrom = m[0];
                            lastContent = m.length > 1 ? m[1] : "";
                            contents.add(lastContent);
                            break;
                        }
                        case "SYS":
                            lastSys = rest;
                            sysMsgs.add(rest);
                            break;
                        case "FILEIN": {
                            String[] f = rest.split(" ");
                            String fileName = f[1];
                            long size = Long.parseLong(f[2]);
                            File dest = new File("downloads_test",
                                    System.currentTimeMillis() + "_" + fileName);
                            dest.getParentFile().mkdirs();
                            byte[] buf = new byte[8192];
                            long remaining = size;
                            try (FileOutputStream fos = new FileOutputStream(dest)) {
                                while (remaining > 0) {
                                    int n = socket.getInputStream().read(buf, 0,
                                            (int) Math.min(buf.length, remaining));
                                    if (n < 0) break;
                                    fos.write(buf, 0, n);
                                    remaining -= n;
                                }
                            }
                            lastFile = dest;
                            break;
                        }
                        default:
                            break;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        void login() { out.println("LOGIN " + name); out.flush(); }
        void send(String s) { out.println(s); out.flush(); }
        void logout() { out.println("LOGOUT"); out.flush(); }

        int msgCount() { return contents.size(); }
        int sysCount() { return sysMsgs.size(); }

        List<String> contentsSince(int idx) {
            synchronized (contents) {
                return new ArrayList<>(contents.subList(idx, contents.size()));
            }
        }

        void sendFile(String target, File f) throws IOException {
            out.println("FILE " + target + " " + f.getName() + " " + f.length());
            out.flush();
            OutputStream raw = socket.getOutputStream();
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) > 0) {
                    raw.write(buf, 0, n);
                    raw.flush();
                }
            }
        }
    }
}
