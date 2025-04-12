import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.*;
import javax.swing.*;


public class Login extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel createAccountLabel;
    private final Color PRIMARY_COLOR = new Color(0, 120, 215);
    private final Color SECONDARY_COLOR = new Color(30, 40, 50);
    private final Color LINK_COLOR = new Color(100, 180, 255);

    public Login() {
        initUI();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initUI() {
        setTitle("Hotel Management System");
        setSize(800, 600);
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 20, 20));
        setLayout(new BorderLayout());

        // Main panel with gradient background
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                GradientPaint gp = new GradientPaint   (
                    0, 0, new Color(20, 30, 40), 
                    getWidth(), getHeight(), new Color(10, 20, 30)
                );
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setLayout(new GridBagLayout());
        add(mainPanel, BorderLayout.CENTER);

        // Form panel
        JPanel formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setBorder(BorderFactory.createEmptyBorder(40, 50, 40, 50));
        formPanel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;

        // Header
        JLabel headerLabel = new JLabel("SIGN IN");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        headerLabel.setForeground(Color.WHITE);
        formPanel.add(headerLabel, gbc);

        // Username Field
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userLabel.setForeground(Color.WHITE);
        formPanel.add(userLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        usernameField = new JTextField(20);
        styleTextField(usernameField);
        formPanel.add(usernameField, gbc);

        // Password Field
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.LINE_END;
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passLabel.setForeground(Color.WHITE);
        formPanel.add(passLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        passwordField = new JPasswordField(20);
        styleTextField(passwordField);
        formPanel.add(passwordField, gbc);

        // Login Button
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        loginButton = new JButton("SIGN IN");
        styleButton(loginButton, PRIMARY_COLOR);
        formPanel.add(loginButton, gbc);

        // Create account link
        gbc.gridy++;
        JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        linkPanel.setOpaque(false);
        
        JLabel questionLabel = new JLabel("Don't have an account?");
        questionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        questionLabel.setForeground(new Color(180, 180, 180));
        
        createAccountLabel = new JLabel("Create account");
        createAccountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        createAccountLabel.setForeground(LINK_COLOR);
        createAccountLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        linkPanel.add(questionLabel);
        linkPanel.add(createAccountLabel);
        formPanel.add(linkPanel, gbc);

        mainPanel.add(formPanel);

        // Add window controls
        addWindowControls();
        setupEventHandlers();
    }

    private void styleTextField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setOpaque(false);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(100, 100, 100)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
    }

    private void styleButton(JButton button, Color bgColor) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.BLACK);
        button.setBackground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void addWindowControls() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(SECONDARY_COLOR);
        titleBar.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 5));

        // Title
        JLabel title = new JLabel("Hotel Management System");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        titleBar.add(title, BorderLayout.WEST);

        // Window controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controlPanel.setOpaque(false);
        
        JButton minimizeButton = new JButton("−");
        styleControlButton(minimizeButton, new Color(80, 80, 80));
        minimizeButton.addActionListener(e -> setState(JFrame.ICONIFIED));
        
        JButton closeButton = new JButton("×");
        styleControlButton(closeButton, new Color(200, 60, 60));
        closeButton.addActionListener(e -> System.exit(0));
        
        controlPanel.add(minimizeButton);
        controlPanel.add(closeButton);
        titleBar.add(controlPanel, BorderLayout.EAST);

        // Make draggable
        titleBar.addMouseListener(new MouseAdapter() {
            private Point dragOffset;
            
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }
            
            public void mouseDragged(MouseEvent e) {
                Point newLocation = e.getLocationOnScreen();
                newLocation.translate(-dragOffset.x, -dragOffset.y);
                setLocation(newLocation);
            }
        });

        add(titleBar, BorderLayout.NORTH);
    }

    private void styleControlButton(JButton button, Color bgColor) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(30, 30));
    }

    private void setupEventHandlers() {
        // Login action
        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "Please enter both username and password", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            try (Connection conn = DatabaseConnection.getConnection()) {
                String sql = "SELECT password FROM users WHERE username = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next()) {
                        String storedPassword = rs.getString("password");
                        String hashedInputPassword = Integer.toString(password.hashCode());
                        
                        if (storedPassword.equals(hashedInputPassword)) {
                            JOptionPane.showMessageDialog(this, 
                                "Login successful! Redirecting to Dashboard...", 
                                "Success", 
                                JOptionPane.INFORMATION_MESSAGE);
                            
                            dispose();
                            new Project();
                        } else {
                            JOptionPane.showMessageDialog(this, 
                                "Invalid username or password", 
                                "Error", 
                                JOptionPane.ERROR_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, 
                            "Invalid username or password", 
                            "Error", 
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, 
                    "Database error: " + ex.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // Create account link action
        createAccountLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                dispose();
                new Register();
            }
            
            public void mouseEntered(MouseEvent e) {
                createAccountLabel.setForeground(LINK_COLOR.brighter());
            }
            
            public void mouseExited(MouseEvent e) {
                createAccountLabel.setForeground(LINK_COLOR);
            }
        });
        
        // Enter key for login
        passwordField.addActionListener(e -> loginButton.doClick());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new Login();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}