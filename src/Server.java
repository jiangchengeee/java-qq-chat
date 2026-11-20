import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * QQ 聊天服务端。
 *
 * 职责：
 * 1. 接受任意多个客户端连接，每个连接一个线程（并行通信的基础）
 * 2. 维护“用户名 -> 连接处理对象”的在线表
 * 3. 客户端之间所有消息都经这里中转，不做点对点直连
 *
 * 运行：java Server [端口]
 */
public class Server {

    /** 在线用户表。用 ConcurrentHashMap 是因为多个客户端线程会同时读写 */
    private static final Map<String, ClientHandler> ONLINE = new ConcurrentHashMap<>();

    /** 文件接收目录 */
    private static final File FILE_DIR = new File("server_files");

    public static void main(String[] args) throws IOException {
        int port = 8888;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("端口号不是数字，使用默认 8888");
            }
        }

        if (!FILE_DIR.exists()) {
            FILE_DIR.mkdirs();
        }

        System.out.println("=========================================");
        System.out.println("  QQ 服务端已启动，监听端口 " + port);
        System.out.println("  在线用户表已就绪，等待客户端连接...");
        System.out.println("=========================================");

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("端口 " + port + " 被占用或无法监听：" + e.getMessage());
            return;
        }

        while (true) {
            Socket socket = serverSocket.accept();       // 阻塞等待下一个客户端
            new ClientHandler(socket).start();           // 每个客户端单独一个线程
        }
    }

    /** 把某个用户加入在线表，并把最新列表广播给所有人 */
    private static void addOnline(String name, ClientHandler handler) {
        ONLINE.put(name, handler);
        System.out.println("[上线] " + name + "，当前在线 " + ONLINE.size() + " 人");
        broadcastList();
        // 通知其他人有新成员
        String notice = Protocol.RES_SYS + " " + name + " 上线了";
        for (ClientHandler h : ONLINE.values()) {
            if (h != handler) {
                h.send(notice);
            }
        }
    }

    private static void removeOnline(String name) {
        if (name == null) {
            return;
        }
        if (ONLINE.remove(name) != null) {
            System.out.println("[下线] " + name + "，当前在线 " + ONLINE.size() + " 人");
            broadcastList();
            String notice = Protocol.RES_SYS + " " + name + " 下线了";
            for (ClientHandler h : ONLINE.values()) {
                h.send(notice);
            }
        }
    }

    /** 把在线用户名列表发给所有客户端，让好友列表实时更新 */
    private static void broadcastList() {
        String line = Protocol.RES_LIST + " " + String.join(",", ONLINE.keySet());
        for (ClientHandler h : ONLINE.values()) {
            h.send(line);
        }
    }

    /** 一个客户端连接的处理器 */
    private static class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        /** 登录成功后才会有值 */
        private volatile String username;

        ClientHandler(Socket socket) {
            this.socket = socket;
            setDaemon(true);
        }

        void send(String line) {
            out.println(line);
            out.flush();
        }

        @Override
        public void run() {
            try {
                in = Protocol.readerOf(socket);
                out = Protocol.writerOf(socket);

                String line;
                // 主循环：一直在读这个客户端发来的命令
                while ((line = in.readLine()) != null) {
                    handle(line);
                }
            } catch (IOException e) {
                // 客户端异常断开，不算错误
            } finally {
                removeOnline(username);
                closeQuietly();
            }
        }

        private void handle(String line) throws IOException {
            String[] parts = line.split(" ", 2);
            String cmd = parts[0];
            String rest = parts.length > 1 ? parts[1] : "";

            switch (cmd) {
                case Protocol.CMD_LOGIN:   doLogin(rest.trim());       break;
                case Protocol.CMD_LIST:    doList();                   break;
                case Protocol.CMD_MSG:     doMsg(rest);                break;
                case Protocol.CMD_GROUP:   doGroup(rest);              break;
                case Protocol.CMD_FILE:    doFile(rest);               break;
                case Protocol.CMD_LOGOUT:  doLogout();                 break;
                default:
                    send(Protocol.RES_SYS + " 未知命令：" + cmd);
            }
        }

        private void doLogin(String name) {
            if (name.isEmpty()) {
                send(Protocol.RES_LOGIN_FAIL + " 用户名不能为空");
                return;
            }
            // 用 putIfAbsent 保证用户名唯一，避免两个人同名互相顶掉
            if (ONLINE.putIfAbsent(name, this) != null) {
                System.out.println("[拒绝] " + name + " 重名登录");
                send(Protocol.RES_LOGIN_FAIL + " 该用户名已在线，请换一个");
                return;
            }
            username = name;
            send(Protocol.RES_LOGIN_OK);
            System.out.println("[登录] " + name);
            broadcastList();

            // 给刚登录的人补一条欢迎信息
            send(Protocol.RES_SYS + " 欢迎 " + name + "，当前在线 " + ONLINE.size() + " 人");
        }

        private void doList() {
            send(Protocol.RES_LIST + " " + String.join(",", ONLINE.keySet()));
        }

        /** 点对点消息：服务端只负责转发 */
        private void doMsg(String rest) {
            if (username == null) {
                send(Protocol.RES_SYS + " 请先登录");
                return;
            }
            String[] p = rest.split(" ", 2);
            if (p.length < 2) {
                send(Protocol.RES_SYS + " 消息格式不对");
                return;
            }
            String target = p[0];
            String content = p[1];

            ClientHandler peer = ONLINE.get(target);
            if (peer == null) {
                send(Protocol.RES_SYS + " " + target + " 不在线");
                return;
            }
            peer.send(Protocol.RES_MSG + " " + username + " " + content);
            System.out.println("[转发] " + username + " -> " + target + " : " + content);
        }

        /** 群发：发给除自己以外的所有在线用户 */
        private void doGroup(String content) {
            if (username == null) {
                send(Protocol.RES_SYS + " 请先登录");
                return;
            }
            if (content.isEmpty()) {
                send(Protocol.RES_SYS + " 群消息不能为空");
                return;
            }
            int count = 0;
            for (Map.Entry<String, ClientHandler> e : ONLINE.entrySet()) {
                if (!e.getKey().equals(username)) {
                    e.getValue().send(Protocol.RES_MSG + " " + username + " [群]" + content);
                    count++;
                }
            }
            System.out.println("[群发] " + username + " 发给了 " + count + " 人");
        }

        /** 文件传输：先声明，然后把字节流原样转给目标 */
        private void doFile(String rest) {
            if (username == null) {
                send(Protocol.RES_SYS + " 请先登录");
                return;
            }
            String[] p = rest.split(" ");
            if (p.length < 3) {
                send(Protocol.RES_SYS + " 文件命令格式不对");
                return;
            }
            String target = p[0];
            String fileName = p[1];
            long size;
            try {
                size = Long.parseLong(p[2]);
            } catch (NumberFormatException e) {
                send(Protocol.RES_SYS + " 文件大小解析失败");
                return;
            }

            ClientHandler peer = ONLINE.get(target);
            if (peer == null) {
                send(Protocol.RES_SYS + " " + target + " 不在线，文件没发出去");
                return;
            }
            if (size < 0 || size > 100L * 1024 * 1024) {
                send(Protocol.RES_SYS + " 文件大小超出限制（最大 100MB）");
                return;
            }

            try {
                // 1) 先告诉对方：有个文件要来了
                peer.send(Protocol.RES_FILEIN + " " + username + " " + fileName + " " + size);

                // 2) 自己先接受发送方发来的字节
                File dest = new File(FILE_DIR, System.currentTimeMillis() + "_" + fileName);
                byte[] buffer = new byte[8192];
                long remaining = size;
                try (FileOutputStream fos = new FileOutputStream(dest)) {
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int n = socket.getInputStream().read(buffer, 0, toRead);
                        if (n < 0) {
                            throw new IOException("发送方提前断开");
                        }
                        fos.write(buffer, 0, n);
                        remaining -= n;
                    }
                }

                // 3) 再把文件内容转给接收方
                peer.writeRawFile(dest, fileName, size);

                send(Protocol.RES_SYS + " 文件 " + fileName + " 已发送给 " + target);
                System.out.println("[文件] " + username + " -> " + target + " : " + fileName
                        + " (" + size + " 字节)");
            } catch (IOException e) {
                send(Protocol.RES_SYS + " 文件传输失败：" + e.getMessage());
            }
        }

        private void doLogout() {
            send(Protocol.RES_SYS + " 已退出");
            removeOnline(username);
            username = null;
            closeQuietly();
        }

        /** 把存好的文件内容写给接收方（在同一个 socket 上跟文本流混着发） */
        synchronized void writeRawFile(File file, String fileName, long size) throws IOException {
            OutputStream raw = socket.getOutputStream();
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                long remaining = size;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int n = fis.read(buffer, 0, toRead);
                    if (n < 0) {
                        break;
                    }
                    raw.write(buffer, 0, n);
                    raw.flush();
                    remaining -= n;
                }
            }
        }

        private void closeQuietly() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
