import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * QQ 风格聊天窗口。
 *
 * 每种消息用不同颜色区分：
 *   自己发的   绿色 + 右对齐标签
 *   对方发的   蓝色 + 左对齐标签
 *   系统提示   灰色居中
 */
public class ChatWindow extends JFrame {

    private static final Color QQ_BLUE   = new Color(0x12B7F5);
    private static final Color SELF_GREEN = new Color(0x2BC13B);
    private static final Color TIP_GRAY   = new Color(0x999999);
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");

    private final Client client;
    private final String selfName;
    private final String friend;
    private final QQClient owner;

    private final JTextPane history = new JTextPane();
    private final JTextArea input = new JTextArea();
    private final JLabel titleLabel = new JLabel();
    private final JLabel stateLabel = new JLabel("在线");
    private final JButton fileButton = new JButton("发送文件");

    public ChatWindow(Client client, String selfName, String friend, QQClient owner) {
        super();
        this.client = client;
        this.selfName = selfName;
        this.friend = friend;
        this.owner = owner;

        boolean isGroup = Protocol.GROUP_TARGET.equals(friend);
        setTitle(isGroup ? "群聊（所有人）" : "与 " + friend + " 聊天");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(560, 620);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0xF5F6F7));

        root.add(buildHeader(isGroup), BorderLayout.NORTH);
        root.add(buildHistory(), BorderLayout.CENTER);
        root.add(buildInput(isGroup), BorderLayout.SOUTH);

        add(root);
    }

    private JPanel buildHeader(boolean isGroup) {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel avatar = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = Math.min(getWidth(), getHeight()) - 4;
                int x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
                g2.setColor(isGroup ? QQ_BLUE : SELF_GREEN);
                g2.fillOval(x, y, d, d);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("微软雅黑", Font.BOLD, d / 2));
                String s = isGroup ? "群" : friend.substring(0, 1);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(s, x + (d - fm.stringWidth(s)) / 2,
                        y + (d - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(42, 42));
        header.add(avatar, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        titleLabel.setText(isGroup ? "群聊（所有人）" : friend);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));

        stateLabel.setText(isGroup ? "发送给全部在线好友" : "在线");
        stateLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        stateLabel.setForeground(SELF_GREEN);

        text.add(titleLabel);
        text.add(Box.createVerticalStrut(3));
        text.add(stateLabel);
        header.add(text, BorderLayout.CENTER);

        return header;
    }

    private JScrollPane buildHistory() {
        history.setEditable(false);
        history.setBackground(Color.WHITE);
        history.setBorder(new EmptyBorder(10, 14, 10, 14));
        history.setFont(new Font("微软雅黑", Font.PLAIN, 14));

        JScrollPane scroll = new JScrollPane(history);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(0xE0E0E0)));
        return scroll;
    }

    private JPanel buildInput(boolean isGroup) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Color.WHITE);
        wrap.setBorder(new EmptyBorder(8, 10, 10, 10));

        input.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        JScrollPane inputScroll = new JScrollPane(input);
        inputScroll.setPreferredSize(new Dimension(10, 90));
        inputScroll.setBorder(BorderFactory.createLineBorder(new Color(0xDDDDDD)));
        wrap.add(inputScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        buttons.setOpaque(false);

        // 回车发送，Shift+回车换行
        input.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send");
        input.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                doSend();
            }
        });

        if (!isGroup) {
            fileButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
            fileButton.setFocusPainted(false);
            fileButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            fileButton.addActionListener(e -> doSendFile());
            buttons.add(fileButton);
        }

        JButton send = new JButton("发送(S)");
        send.setFont(new Font("微软雅黑", Font.BOLD, 13));
        send.setForeground(Color.WHITE);
        send.setBackground(QQ_BLUE);
        send.setOpaque(true);
        send.setBorderPainted(false);
        send.setFocusPainted(false);
        send.setCursor(new Cursor(Cursor.HAND_CURSOR));
        send.setPreferredSize(new Dimension(90, 32));
        send.addActionListener(e -> doSend());
        buttons.add(send);

        wrap.add(buttons, BorderLayout.SOUTH);
        return wrap;
    }

    /** 发送文字 */
    private void doSend() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (Protocol.GROUP_TARGET.equals(friend)) {
            client.sendGroup(text);
        } else {
            client.sendMessage(friend, text);
        }
        appendOutgoing(text);
        input.setText("");
        input.requestFocus();
    }

    /** 选文件并发送 */
    private void doSendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要发送的文件");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = chooser.getSelectedFile();
        if (f == null || !f.exists()) {
            return;
        }
        if (f.length() > 100L * 1024 * 1024) {
            JOptionPane.showMessageDialog(this, "文件超过 100MB，发不了", "提示",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        fileButton.setEnabled(false);
        fileButton.setText("发送中…");

        // 发文件可能耗时，别占着界面线程
        new Thread(() -> {
            try {
                client.sendFile(friend, f);
                SwingUtilities.invokeLater(() -> {
                    appendSystem("已发送文件：" + f.getName() + "（" + f.length() + " 字节）");
                    fileButton.setEnabled(true);
                    fileButton.setText("发送文件");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendSystem("文件发送失败：" + ex.getMessage());
                    fileButton.setEnabled(true);
                    fileButton.setText("发送文件");
                });
            }
        }, "file-send").start();
    }

    // ==================== 往聊天记录里追加内容 ====================

    void appendIncoming(String from, String content) {
        append(" " + from + "  " + TIME.format(new Date()), content, QQ_BLUE);
    }

    void appendOutgoing(String content) {
        append("我  " + TIME.format(new Date()), content, SELF_GREEN);
    }

    void appendSystem(String text) {
        appendStyled("\n" + text + "\n", TIP_GRAY, getHistoryDocLength());
    }

    private void append(String title, String content, Color color) {
        // 第一段：昵称 + 时间，用对应颜色
        appendStyled("\n" + title + "\n", color, getHistoryDocLength());
        // 第二段：消息正文，用深灰保证可读性
        appendStyled(content + "\n", new Color(0x333333), getHistoryDocLength());
        scrollToBottom();
    }

    private void appendStyled(String text, Color color, int insertAt) {
        try {
            javax.swing.text.StyledDocument doc = history.getStyledDocument();
            javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setForeground(attrs, color);
            javax.swing.text.StyleConstants.setFontFamily(attrs, "微软雅黑");
            javax.swing.text.StyleConstants.setFontSize(attrs, 13);
            doc.insertString(doc.getLength(), text, attrs);
        } catch (Exception ignored) {
        }
    }

    private int getHistoryDocLength() {
        return history.getDocument().getLength();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() ->
                history.setCaretPosition(history.getDocument().getLength()));
    }

    void setOnline(boolean online) {
        stateLabel.setText(online ? "在线" : "离线");
        stateLabel.setForeground(online ? SELF_GREEN : new Color(0x999999));
    }
}
