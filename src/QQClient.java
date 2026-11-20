import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * QQ 客户端主窗口。
 *
 * 用 CardLayout 做两个界面：
 *   卡片 1 登录界面（带登录失败提示）
 *   卡片 2 好友列表界面
 * 登录成功后切到卡片 2，整个过程在同一个窗口里完成。
 */
public class QQClient extends JFrame implements Client.Listener {

    private static final Color QQ_BLUE    = new Color(0x12B7F5);
    private static final Color DARK_BLUE  = new Color(0x0A8FD1);
    private static final Color BG_LIGHT   = new Color(0xF5F6F7);
    private static final Color ONLINE_GREEN = new Color(0x2BC13B);
    private static final Color OFFLINE_GRAY = new Color(0xBBBBBB);

    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);

    private final String host;
    private final int port;
    private Client client;

    // 登录界面组件
    private JTextField nameField;
    private JPasswordField passField;
    private JLabel loginTip;
    private JButton loginButton;

    // 好友列表组件
    private DefaultListModel<String> friendModel;
    private JList<String> friendList;
    private JLabel selfLabel;
    private JLabel statusLabel;
    private List<String> onlineUsers = new ArrayList<>();
    private String selfName = "";

    /** 已打开的聊天窗口，按好友名索引，避免重复开 */ 
    private final java.util.Map<String, ChatWindow> openChats = new java.util.HashMap<>();

    public QQClient(String host, int port) {
        super("Java-QQ 聊天客户端");
        this.host = host;
        this.port = port;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(360, 520);
        setLocationRelativeTo(null);
        setResizable(false);

        cardPanel.add(buildLoginPanel(), "login");
        cardPanel.add(buildFriendPanel(), "friends");
        add(cardPanel);

        cards.show(cardPanel, "login");
    }

    // ==================== 卡片 1：登录界面 ====================

    private JPanel buildLoginPanel() {
        JPanel root = new JPanel(new BorderLayout());

        // 上部：蓝色渐变头部
        JPanel header = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, QQ_BLUE, 0, getHeight(), DARK_BLUE);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setPreferredSize(new Dimension(360, 170));
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("Java-QQ");
        logo.setFont(new Font("微软雅黑", Font.BOLD, 34));
        logo.setForeground(Color.WHITE);
        logo.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = new JLabel("简单、即时、可靠");
        sub.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        sub.setForeground(new Color(0xE6F7FF));
        sub.setAlignmentX(CENTER_ALIGNMENT);

        header.add(Box.createVerticalGlue());
        header.add(logo);
        header.add(Box.createVerticalStrut(8));
        header.add(sub);
        header.add(Box.createVerticalGlue());

        // 中部：输入区
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG_LIGHT);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 30, 6, 30);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;

        JLabel userLabel = new JLabel("用户名");
        userLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 1;
        form.add(userLabel, gc);

        nameField = new JTextField();
        nameField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        nameField.setPreferredSize(new Dimension(300, 32));
        gc.gridy = 1;
        form.add(nameField, gc);

        JLabel passLabel = new JLabel("密码");
        passLabel.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        gc.gridy = 2;
        form.add(passLabel, gc);

        passField = new JPasswordField();
        passField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        passField.setPreferredSize(new Dimension(300, 32));
        gc.gridy = 3;
        form.add(passField, gc);

        // 登录失败提示区（平时不显示文字）
        loginTip = new JLabel(" ");
        loginTip.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        loginTip.setForeground(new Color(0xD93025));
        loginTip.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy = 4;
        form.add(loginTip, gc);

        loginButton = new JButton("登  录");
        loginButton.setFont(new Font("微软雅黑", Font.BOLD, 15));
        loginButton.setForeground(Color.WHITE);
        loginButton.setBackground(QQ_BLUE);
        loginButton.setFocusPainted(false);
        loginButton.setOpaque(true);
        loginButton.setBorderPainted(false);
        loginButton.setPreferredSize(new Dimension(300, 38));
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        loginButton.addActionListener(e -> doLogin());
        gc.gridy = 5;
        form.add(loginButton, gc);

        JButton exitButton = new JButton("退出");
        exitButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        exitButton.setFocusPainted(false);
        exitButton.setContentAreaFilled(false);
        exitButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exitButton.addActionListener(e -> System.exit(0));
        gc.gridy = 6;
        form.add(exitButton, gc);

        // 回车直接登录
        nameField.addActionListener(e -> doLogin());
        passField.addActionListener(e -> doLogin());

        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        return root;
    }

    /** 点击登录：连服务端、发 LOGIN */
    private void doLogin() {
        String name = nameField.getText().trim();
        String pass = new String(passField.getPassword());

        if (name.isEmpty()) {
            showLoginTip("请输入用户名");
            return;
        }
        if (pass.isEmpty()) {
            showLoginTip("请输入密码");
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("登录中…");
        showLoginTip(" ");

        // 网络操作放后台线程，别卡住界面
        new Thread(() -> {
            try {
                client = new Client(host, port);
                client.setListener(QQClient.this);
                client.connect();
                selfName = name;
                client.login(name);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    showLoginTip("连不上服务器：" + ex.getMessage());
                    resetLoginButton();
                });
            }
        }, "login-thread").start();
    }

    private void showLoginTip(String text) {
        loginTip.setText(text);
    }

    private void resetLoginButton() {
        loginButton.setEnabled(true);
        loginButton.setText("登  录");
    }

    // ==================== 卡片 2：好友列表 ====================

    private JPanel buildFriendPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_LIGHT);

        // 顶部个人信息条
        JPanel me = new JPanel(new BorderLayout(10, 0));
        me.setBackground(Color.WHITE);
        me.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel avatar = new AvatarLabel(ONLINE_GREEN);
        avatar.setPreferredSize(new Dimension(44, 44));
        me.add(avatar, BorderLayout.WEST);

        JPanel meText = new JPanel();
        meText.setLayout(new BoxLayout(meText, BoxLayout.Y_AXIS));
        meText.setBackground(Color.WHITE);
        selfLabel = new JLabel("（未登录）");
        selfLabel.setFont(new Font("微软雅黑", Font.BOLD, 15));
        statusLabel = new JLabel("在线");
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        statusLabel.setForeground(ONLINE_GREEN);
        meText.add(selfLabel);
        meText.add(Box.createVerticalStrut(3));
        meText.add(statusLabel);
        me.add(meText, BorderLayout.CENTER);

        // 好友列表
        friendModel = new DefaultListModel<>();
        friendList = new JList<>(friendModel);
        friendList.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        friendList.setFixedCellHeight(52);
        friendList.setCellRenderer(new FriendCellRenderer());
        friendList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        friendList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = friendList.getSelectedValue();
                    if (sel != null) {
                        openChat(sel);
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(friendList);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JLabel listTitle = new JLabel("  好友");
        listTitle.setFont(new Font("微软雅黑", Font.BOLD, 13));
        listTitle.setForeground(new Color(0x666666));
        listTitle.setBorder(BorderFactory.createEmptyBorder(10, 4, 6, 4));

        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setBackground(BG_LIGHT);
        listWrap.add(listTitle, BorderLayout.NORTH);
        listWrap.add(scroll, BorderLayout.CENTER);

        // 底部按钮栏
        JPanel bottom = new JPanel(new GridLayout(1, 2, 8, 0));
        bottom.setBackground(BG_LIGHT);
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));

        JButton chatButton = makeFlatButton("发消息", QQ_BLUE);
        chatButton.addActionListener(e -> {
            String sel = friendList.getSelectedValue();
            if (sel == null) {
                JOptionPane.showMessageDialog(this, "请先在列表里选一个好友", "提示",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            openChat(sel);
        });

        JButton groupButton = makeFlatButton("群发消息", DARK_BLUE);
        groupButton.addActionListener(e -> openChat(Protocol.GROUP_TARGET));

        bottom.add(chatButton);
        bottom.add(groupButton);

        root.add(me, BorderLayout.NORTH);
        root.add(listWrap, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private JButton makeFlatButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(100, 34));
        return b;
    }

    /** 把内部标识转成给人看的名字 */
    static String displayName(String raw) {
        return Protocol.GROUP_TARGET.equals(raw) ? "群聊（所有人）" : raw;
    }

    /** 打开（或切到已打开的）聊天窗口 */
    public void openChat(String friend) {
        ChatWindow w = openChats.get(friend);
        if (w == null) {
            w = new ChatWindow(client, selfName, friend, this);
            openChats.put(friend, w);
        }
        w.setVisible(true);
        w.toFront();
        w.requestFocus();
    }

    /** 好友下线时，如果聊天窗口开着，把标题标成离线 */
    void markFriendOffline(String friend) {
        ChatWindow w = openChats.get(friend);
        if (w != null) {
            w.setOnline(false);
        }
    }

    void markFriendOnline(String friend) {
        ChatWindow w = openChats.get(friend);
        if (w != null) {
            w.setOnline(true);
        }
    }

    // ==================== 头像 ====================

    /** 画一个圆里头写字的头像，不依赖图片资源 */
    static class AvatarLabel extends JLabel {
        private final Color color;

        AvatarLabel(Color color) {
            this.color = color;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - 4;
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;

            g2.setColor(color);
            g2.fillOval(x, y, d, d);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("微软雅黑", Font.BOLD, Math.max(12, d / 2)));
            FontMetrics fm = g2.getFontMetrics();
            String s = "我";
            g2.drawString(s, x + (d - fm.stringWidth(s)) / 2,
                    y + (d - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** 好友列表渲染：小圆点 + 名字 + 在线状态 */
    class FriendCellRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel dot = new JLabel("\u25CF");
        private final JLabel name = new JLabel();
        private final JLabel state = new JLabel();

        FriendCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            name.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            state.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            text.add(name);
            text.add(state);

            dot.setFont(new Font("微软雅黑", Font.PLAIN, 16));
            add(dot, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            boolean online = onlineUsers.contains(value);
            name.setText(displayName(value));
            if (value.equals(Protocol.GROUP_TARGET)) {
                state.setText("发送给全部在线好友");
            } else {
                state.setText(online ? "在线" : "离线");
            }
            dot.setForeground(online ? ONLINE_GREEN : OFFLINE_GRAY);
            name.setForeground(online ? new Color(0x222222) : new Color(0x999999));

            setBackground(isSelected ? new Color(0xE3F4FD) : Color.WHITE);
            setOpaque(true);
            return this;
        }
    }

    // ==================== Client.Listener 回调 ====================

    @Override
    public void onLoginOk() {
        SwingUtilities.invokeLater(() -> {
            selfLabel.setText(selfName);
            cards.show(cardPanel, "friends");
            resetLoginButton();
        });
    }

    @Override
    public void onLoginFail(String reason) {
        SwingUtilities.invokeLater(() -> {
            showLoginTip(reason);
            resetLoginButton();
            if (client != null) {
                client.close();
                client = null;
            }
        });
    }

    @Override
    public void onFriendList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            onlineUsers = new ArrayList<>(users);

            String selected = friendList.getSelectedValue();
            friendModel.clear();
            // 只有除了自己还有别人在线时才显示群聊入口
            if (onlineUsers.size() > 1) {
                friendModel.addElement(Protocol.GROUP_TARGET);
            }
            for (String u : onlineUsers) {
                if (!u.equals(selfName)) {
                    friendModel.addElement(u);
                }
            }
            // 恢复原来的选中项
            if (selected != null) {
                for (int i = 0; i < friendModel.size(); i++) {
                    if (friendModel.get(i).equals(selected)) {
                        friendList.setSelectedIndex(i);
                        break;
                    }
                }
            }

            for (String u : onlineUsers) {
                if (!u.equals(selfName)) {
                    markFriendOnline(u);
                }
            }
        });
    }

    @Override
    public void onMessage(String from, String content) {
        SwingUtilities.invokeLater(() -> {
            ChatWindow w = openChats.get(from);
            if (w == null) {
                w = new ChatWindow(client, selfName, from, this);
                openChats.put(from, w);
                w.setVisible(true);
            }
            w.appendIncoming(from, content);
        });
    }

    @Override
    public void onSystem(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    @Override
    public void onFileIncoming(String from, String fileName, long size, java.io.File saved) {
        SwingUtilities.invokeLater(() -> {
            ChatWindow w = openChats.get(from);
            if (w != null) {
                w.appendSystem("收到文件：" + fileName + "（" + size + " 字节），已保存到 "
                        + saved.getAbsolutePath());
            }
            JOptionPane.showMessageDialog(this,
                    "收到来自 " + from + " 的文件：\n" + fileName
                            + "\n大小：" + size + " 字节\n保存位置：" + saved.getAbsolutePath(),
                    "文件已接收", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(reason);
            statusLabel.setForeground(new Color(0xD93025));
            JOptionPane.showMessageDialog(this, "连接已断开：" + reason, "提示",
                    JOptionPane.WARNING_MESSAGE);
            cards.show(cardPanel, "login");
        });
    }

    // ==================== 入口 ====================

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 8888;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        final String h = host;
        final int p = port;
        SwingUtilities.invokeLater(() -> new QQClient(h, p).setVisible(true));
    }
}
