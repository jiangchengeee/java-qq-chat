import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

/**
 * 客户端通信层。
 *
 * 关键设计：接收单独开一个线程，界面线程只管发送。
 * 所以“对方发过来”和“我这边发出去”互不阻塞，这就是题目要求的并行通信，
 * 不再是原来 SingleTalkClient 那种你说一句我说一句的串行模型。
 */
public class Client {

    /** 收到服务端消息时的回调，由界面层实现 */
    public interface Listener {
        void onLoginOk();
        void onLoginFail(String reason);
        void onFriendList(List<String> users);
        void onMessage(String from, String content);
        void onSystem(String text);
        void onFileIncoming(String from, String fileName, long size, File saved);
        void onDisconnected(String reason);
    }

    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private volatile boolean running = false;
    private Listener listener;

    /** 收到的文件存到本地这个目录 */
    public static final File DOWNLOAD_DIR = new File("downloads");

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 建立连接并启动接收线程 */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        in = Protocol.readerOf(socket);
        out = Protocol.writerOf(socket);
        running = true;

        if (!DOWNLOAD_DIR.exists()) {
            DOWNLOAD_DIR.mkdirs();
        }

        Thread receiver = new Thread(this::receiveLoop, "receiver");
        receiver.setDaemon(true);
        receiver.start();
    }

    public boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }

    /** 接收线程：一直在读，读到什么就回调给界面 */
    private void receiveLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                dispatch(line);
            }
        } catch (IOException e) {
            if (running) {
                notifyDisconnected("连接中断：" + e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    private void dispatch(String line) throws IOException {
        String[] parts = line.split(" ", 2);
        String cmd = parts[0];
        String rest = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case Protocol.RES_LOGIN_OK:
                call(l -> l.onLoginOk());
                break;

            case Protocol.RES_LOGIN_FAIL:
                call(l -> l.onLoginFail(rest));
                break;

            case Protocol.RES_LIST: {
                List<String> users = new ArrayList<>();
                if (!rest.trim().isEmpty()) {
                    for (String u : rest.split(",")) {
                        if (!u.trim().isEmpty()) {
                            users.add(u.trim());
                        }
                    }
                }
                call(l -> l.onFriendList(users));
                break;
            }

            case Protocol.RES_MSG: {
                String[] p = rest.split(" ", 2);
                String from = p[0];
                String content = p.length > 1 ? p[1] : "";
                call(l -> l.onMessage(from, content));
                break;
            }

            case Protocol.RES_SYS:
                call(l -> l.onSystem(rest));
                break;

            case Protocol.RES_FILEIN: {
                String[] p = rest.split(" ");
                String from = p[0];
                String fileName = p[1];
                long size = Long.parseLong(p[2]);
                File saved = receiveFile(fileName, size);
                call(l -> l.onFileIncoming(from, fileName, size, saved));
                break;
            }

            default:
                call(l -> l.onSystem("未知响应：" + line));
        }
    }

    /** 在接收线程里按字节数读文件，读完再回到正常读行的状态 */
    private File receiveFile(String fileName, long size) throws IOException {
        File dest = new File(DOWNLOAD_DIR, System.currentTimeMillis() + "_" + fileName);
        InputStream raw = socket.getInputStream();
        byte[] buffer = new byte[8192];
        long remaining = size;
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int n = raw.read(buffer, 0, toRead);
                if (n < 0) {
                    throw new IOException("文件接收中断");
                }
                fos.write(buffer, 0, n);
                remaining -= n;
            }
        }
        return dest;
    }

    private void call(java.util.function.Consumer<Listener> action) {
        if (listener != null) {
            action.accept(listener);
        }
    }

    private void notifyDisconnected(String reason) {
        call(l -> l.onDisconnected(reason));
    }

    // ================= 发送 =================

    public void login(String username) {
        send(Protocol.CMD_LOGIN + " " + username);
    }

    public void requestList() {
        send(Protocol.CMD_LIST);
    }

    public void sendMessage(String target, String content) {
        send(Protocol.CMD_MSG + " " + target + " " + content);
    }

    public void sendGroup(String content) {
        send(Protocol.CMD_GROUP + " " + content);
    }

    /** 发送文件：先发一行声明，紧接着发原始字节 */
    public void sendFile(String target, File file) throws IOException {
        String name = file.getName().replace(' ', '_');
        long size = file.length();

        // 这一行必须是纯文本，发完立刻 flush，后面的字节才不会混进去
        out.println(Protocol.CMD_FILE + " " + target + " " + name + " " + size);
        out.flush();

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

    private void send(String line) {
        if (out != null) {
            out.println(line);
            out.flush();
        }
    }

    public void close() {
        running = false;
        try {
            if (out != null) {
                out.println(Protocol.CMD_LOGOUT);
                out.flush();
            }
        } catch (Exception ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
