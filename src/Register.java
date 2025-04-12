import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.*;
import javax.swing.*;

public class Register extends JFrame {
    private JTextField usernameField, emailField;
    private JPasswordField passwordField, confirmPasswordField;
    private JButton registerButton;
    private JLabel loginLabel;
    private final Color PRIMARY_COLOR = new Color(0, 120, 215);
    private final Color SECONDARY_COLOR = new Color(30, 40, 50);
    private final Color LINK_COLOR = new Color(100, 180, 255);

    public Register() {
        initUI();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initUI() {
        setTitle("Register - Hotel Management");
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
                GradientPaint gp = new GradientPaint(
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
        JLabel headerLabel = new JLabel("CREATE ACCOUNT");
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

        // Email Field
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.LINE_END;
        JLabel emailLabel = new JLabel("Email:");
        emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        emailLabel.setForeground(Color.WHITE);
        formPanel.add(emailLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        emailField = new JTextField(20);
        styleTextField(emailField);
        formPanel.add(emailField, gbc);

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

        // Confirm Password Field
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.LINE_END;
        JLabel confirmPassLabel = new JLabel("Confirm Password:");
        confirmPassLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        confirmPassLabel.setForeground(Color.WHITE);
        formPanel.add(confirmPassLabel, gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        confirmPasswordField = new JPasswordField(20);
        styleTextField(confirmPasswordField);
        formPanel.add(confirmPasswordField, gbc);

        // Register Button
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        registerButton = new JButton("REGISTER");
        styleButton(registerButton);
        formPanel.add(registerButton, gbc);

        // Login Link
        gbc.gridy++;
        JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        linkPanel.setOpaque(false);

        JLabel questionLabel = new JLabel("Already have an account?");
        questionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        questionLabel.setForeground(new Color(180, 180, 180));

        loginLabel = new JLabel("Login here");
        loginLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        loginLabel.setForeground(LINK_COLOR);
        loginLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        linkPanel.add(questionLabel);
        linkPanel.add(loginLabel);
        formPanel.add(linkPanel, gbc);

        mainPanel.add(formPanel);

        // Setup event handlers
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

    private void styleButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.BLACK);
        button.setBackground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void initializeDatabase() {
        try (Connection conn = DatabaseConnection.getConnectionWithoutDB();
             Statement stmt = conn.createStatement()) {
            
            // Check if database exists
            ResultSet rs = conn.getMetaData().getCatalogs();
            boolean dbExists = false;
            while (rs.next()) {
                if ("hotel_management".equalsIgnoreCase(rs.getString(1))) {
                    dbExists = true;
                    break;
                }
            }
            
            if (!dbExists) {
                stmt.executeUpdate("CREATE DATABASE hotel_management");
                stmt.executeUpdate("USE hotel_management");
                
                // Create users table
                stmt.executeUpdate("CREATE TABLE users (" +
                    "user_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) UNIQUE NOT NULL, " +
                    "email VARCHAR(100) UNIQUE NOT NULL, " +
                    "password VARCHAR(255) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                
                System.out.println("Database and users table created successfully");
            } else {
                stmt.executeUpdate("USE hotel_management");
                DatabaseMetaData dbm = conn.getMetaData();
                rs = dbm.getTables(null, null, "users", null);
                
                if (!rs.next()) {
                    stmt.executeUpdate("CREATE TABLE users (" +
                        "user_id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "username VARCHAR(50) UNIQUE NOT NULL, " +
                        "email VARCHAR(100) UNIQUE NOT NULL, " +
                        "password VARCHAR(255) NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                    System.out.println("Users table created in existing database");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }

    private void registerUser() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        String confirmPassword = new String(confirmPasswordField.getPassword()).trim();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All fields are required!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email address!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Hash the password (basic hashing for demo - use BCrypt in production)
        String hashedPassword = Integer.toString(password.hashCode());

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Check if username already exists
            String checkUserSql = "SELECT username FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkUserSql)) {
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    JOptionPane.showMessageDialog(this, "Username already exists!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            // Check if email already exists
            String checkEmailSql = "SELECT email FROM users WHERE email = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkEmailSql)) {
                checkStmt.setString(1, email);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    JOptionPane.showMessageDialog(this, "Email already registered!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            // Insert new user
            String insertSql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, username);
                insertStmt.setString(2, email);
                insertStmt.setString(3, hashedPassword);
                insertStmt.executeUpdate();
                
                JOptionPane.showMessageDialog(this, "Registration successful!", "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
                new Login();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupEventHandlers() {
        // Register button action
        registerButton.addActionListener(e -> registerUser());

        // Login link action
        loginLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                dispose();
                new Login();
            }

            public void mouseEntered(MouseEvent e) {
                loginLabel.setForeground(LINK_COLOR.brighter());
            }

            public void mouseExited(MouseEvent e) {
                loginLabel.setForeground(LINK_COLOR);
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Register register = new Register();
                register.initializeDatabase();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}