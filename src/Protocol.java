import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * 通信协议：一行一条命令，用空格分隔命令和参数。
 *
 * 客户端 -> 服务端
 *   LOGIN <用户名>          登录
 *   LIST                    请求在线好友列表
 *   MSG <目标用户> <内容>    给某个人发消息
 *   GROUP <内容>            群发
 *   FILE <目标用户> <文件名> <字节数>
 *   （FILE 之后紧接着在同一个 socket 上发原始字节）
 *
 * 服务端 -> 客户端
 *   LOGIN_OK                登录成功
 *   LOGIN_FAIL <原因>       登录失败
 *   LIST <用户1>,<用户2>    在线列表
 *   MSG <发送者> <内容>     收到消息
 *   SYS <提示>              系统提示
 *   FILEIN <发送者> <文件名> <字节数>   文件即将到达
 */
public class Protocol {
    public static final String CMD_LOGIN   = "LOGIN";
    public static final String CMD_LIST    = "LIST";
    public static final String CMD_MSG     = "MSG";
    public static final String CMD_GROUP   = "GROUP";
    public static final String CMD_FILE    = "FILE";
    public static final String CMD_LOGOUT  = "LOGOUT";

    public static final String RES_LOGIN_OK   = "LOGIN_OK";
    public static final String RES_LOGIN_FAIL = "LOGIN_FAIL";
    public static final String RES_LIST       = "LIST";
    public static final String RES_MSG        = "MSG";
    public static final String RES_SYS        = "SYS";
    public static final String RES_FILEIN     = "FILEIN";

    /** 群发时用的目标名 */
    public static final String GROUP_TARGET = "@ALL";

    /** 把一行按最多 n 段切分，注意保留消息体里的空格 */
    public static String[] split(String line, int limit) {
        return line.split(" ", limit);
    }

    public static BufferedReader readerOf(Socket socket) throws IOException {
        return new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    public static PrintWriter writerOf(Socket socket) throws IOException {
        return new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }
}
