import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/hotel_management"; // Changed port from 3307 to standard 3306
    private static final String USER = "root";
    private static final String PASSWORD = "123@Admin";
    
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
    }
    
    public static Connection getConnectionWithoutDB() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection("jdbc:mysql://localhost:3306/", USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
    }
}

class Room {
    private int roomNumber;
    private String roomType;
    private boolean isBooked;
    private String guestName;
    private int nightsStayed;
    private double pricePerNight;
    private Map<String, String> preBookings;

    public Room(int roomNumber, String roomType, double pricePerNight) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.pricePerNight = pricePerNight;
        this.isBooked = false;
        this.guestName = "";
        this.nightsStayed = 0;
        this.preBookings = new HashMap<>();
        loadFromDatabase();
    }

    private void loadFromDatabase() {
        String sql = "SELECT * FROM rooms WHERE room_number = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, this.roomNumber);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                this.roomType = rs.getString("room_type");
                this.pricePerNight = rs.getDouble("price_per_night");
                this.isBooked = rs.getBoolean("is_booked");
                
                if (this.isBooked) {
                    loadActiveBooking();
                }
                
                loadPreBookings();
            } else {
                insertRoom();
            }
        } catch (SQLException e) {
            System.err.println("Error loading room from database: " + e.getMessage());
        }
    }
    
    private void insertRoom() {
        String sql = "INSERT INTO rooms (room_number, room_type, price_per_night) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, this.roomNumber);
            stmt.setString(2, this.roomType);
            stmt.setDouble(3, this.pricePerNight);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting room: " + e.getMessage());
        }
    }
    
    private void loadActiveBooking() {
        String sql = "SELECT * FROM bookings WHERE room_number = ? AND is_active = true";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, this.roomNumber);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                this.guestName = rs.getString("guest_name");
                this.nightsStayed = rs.getInt("nights_stayed");
            }
        } catch (SQLException e) {
            System.err.println("Error loading active booking: " + e.getMessage());
        }
    }
    
    private void loadPreBookings() {
        String sql = "SELECT * FROM pre_bookings WHERE room_number = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, this.roomNumber);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String dates = rs.getDate("check_in_date") + " to " + rs.getDate("check_out_date");
                this.preBookings.put(dates, rs.getString("guest_name"));
            }
        } catch (SQLException e) {
            System.err.println("Error loading pre-bookings: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return !isBooked;
    }

    public void bookRoom(String guestName, int nights) {
        if (!isBooked) {
            String sql = "INSERT INTO bookings (room_number, guest_name, nights_stayed, total_bill, is_active, check_in_date, check_out_date) " +
                         "VALUES (?, ?, ?, ?, true, CURDATE(), DATE_ADD(CURDATE(), INTERVAL ? DAY))";
            
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                double totalBill = nights * pricePerNight;
                
                stmt.setInt(1, this.roomNumber);
                stmt.setString(2, guestName);
                stmt.setInt(3, nights);
                stmt.setDouble(4, totalBill);
                stmt.setInt(5, nights);
                stmt.executeUpdate();
                
                updateRoomStatus(true);
                
                this.isBooked = true;
                this.guestName = guestName;
                this.nightsStayed = nights;
            } catch (SQLException e) {
                System.err.println("Error booking room: " + e.getMessage());
            }
        }
    }
    
    public void checkout() {
        String sql = "UPDATE bookings SET is_active = false, check_out_date = CURDATE() WHERE room_number = ? AND is_active = true";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, this.roomNumber);
            stmt.executeUpdate();
            
            updateRoomStatus(false);
            moveToHistory();
            
            this.isBooked = false;
            this.guestName = "";
            this.nightsStayed = 0;
        } catch (SQLException e) {
            System.err.println("Error during checkout: " + e.getMessage());
        }
    }
    
    private void updateRoomStatus(boolean isBooked) {
        String sql = "UPDATE rooms SET is_booked = ? WHERE room_number = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBoolean(1, isBooked);
            stmt.setInt(2, this.roomNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating room status: " + e.getMessage());
        }
    }
    
    private void moveToHistory() {
        String sql = "INSERT INTO guest_history (guest_name, room_number, nights_stayed, total_bill, check_in_date, check_out_date) " +
                     "SELECT guest_name, room_number, nights_stayed, total_bill, check_in_date, check_out_date FROM bookings " +
                     "WHERE room_number = ? AND is_active = false";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, this.roomNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error moving to history: " + e.getMessage());
        }
    }
    
    public void cancelPreBooking(String dates) {
        String[] dateParts = dates.split(" to ");
        String checkInDate = dateParts[0].trim();
        String checkOutDate = dateParts[1].trim();
        String guestName = this.preBookings.get(dates);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First record in history
            String historySql = "INSERT INTO canceled_pre_bookings_history " +
                              "(room_number, guest_name, original_check_in_date, original_check_out_date) " +
                              "VALUES (?, ?, ?, ?)";
            try (PreparedStatement historyStmt = conn.prepareStatement(historySql)) {
                historyStmt.setInt(1, this.roomNumber);
                historyStmt.setString(2, guestName);
                historyStmt.setDate(3, Date.valueOf(checkInDate));
                historyStmt.setDate(4, Date.valueOf(checkOutDate));
                historyStmt.executeUpdate();
            }
            
            // Then delete from active pre-bookings
            String deleteSql = "DELETE FROM pre_bookings WHERE room_number = ? AND check_in_date = ? AND check_out_date = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, this.roomNumber);
                deleteStmt.setDate(2, Date.valueOf(checkInDate));
                deleteStmt.setDate(3, Date.valueOf(checkOutDate));
                deleteStmt.executeUpdate();
            }
            
            this.preBookings.remove(dates);
        } catch (SQLException e) {
            System.err.println("Error canceling pre-booking: " + e.getMessage());
        }
    }
    
    public Object[] toTableRow() {
        return new Object[]{roomNumber, roomType, pricePerNight, isBooked ? "Booked by " + guestName : "Available"};
    }

    public int getRoomNumber() {
        return roomNumber;
    }

    public String getGuestName() {
        return guestName;
    }

    public int getNightsStayed() {
        return nightsStayed;
    }

    public double getPricePerNight() {
        return pricePerNight;
    }

    public double getTotalBill() {
        return nightsStayed * pricePerNight;
    }

    public boolean isPreBooked(String dates) {
        return preBookings.containsKey(dates);
    }

    public void addPreBooking(String guestName, String dates) {
        String[] dateParts = dates.split(" to ");
        String checkInDate = dateParts[0].trim();
        String checkOutDate = dateParts[1].trim();
        
        String sql = "INSERT INTO pre_bookings (room_number, guest_name, check_in_date, check_out_date) " +
                     "VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, this.roomNumber);
            stmt.setString(2, guestName);
            stmt.setDate(3, Date.valueOf(checkInDate));
            stmt.setDate(4, Date.valueOf(checkOutDate));
            stmt.executeUpdate();
            
            this.preBookings.put(dates, guestName);
        } catch (SQLException e) {
            System.err.println("Error adding pre-booking: " + e.getMessage());
        }
    }

    public Map<String, String> getPreBookings() {
        return preBookings;
    }
}

class Hotel {
    private ArrayList<Room> rooms;
    private ArrayList<String> guestHistory;

    public Hotel() {
        rooms = new ArrayList<>();
        guestHistory = new ArrayList<>();
        initializeRooms();
        loadGuestHistory();
    }

    private void initializeRooms() {
        // Floor 1: Single Rooms (101 to 105)
        for (int i = 1; i <= 5; i++) {
            int roomNumber = 100 + i;
            rooms.add(new Room(roomNumber, "Single", 50));
        }

        // Floor 2: Double Rooms (201 to 205)
        for (int i = 1; i <= 5; i++) {
            int roomNumber = 200 + i;
            rooms.add(new Room(roomNumber, "Double", 80));
        }

        // Floor 3: Suite Rooms (301 to 305)
        for (int i = 1; i <= 5; i++) {
            int roomNumber = 300 + i;
            rooms.add(new Room(roomNumber, "Suite", 150));
        }

        // Floor 4: Deluxe Rooms (401 to 405)
        for (int i = 1; i <= 5; i++) {
            int roomNumber = 400 + i;
            rooms.add(new Room(roomNumber, "Deluxe", 200));
        }
    }
    
    private void loadGuestHistory() {
        String sql = "SELECT * FROM guest_history ORDER BY check_out_date DESC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String entry = "Guest: " + rs.getString("guest_name") + 
                               ", Room: " + rs.getInt("room_number") + 
                               ", Nights: " + rs.getInt("nights_stayed") + 
                               ", Total Bill: $" + rs.getDouble("total_bill") +
                               ", Check-out: " + rs.getDate("check_out_date");
                guestHistory.add(entry);
            }
        } catch (SQLException e) {
            System.err.println("Error loading guest history: " + e.getMessage());
        }
    }

    public ArrayList<Room> getRooms() {
        return rooms;
    }

    public Room findRoom(int roomNumber) {
        for (Room room : rooms) {
            if (room.getRoomNumber() == roomNumber) {
                return room;
            }
        }
        return null;
    }

    public ArrayList<Room> getBookedRooms() {
        ArrayList<Room> bookedRooms = new ArrayList<>();
        for (Room room : rooms) {
            if (!room.isAvailable()) {
                bookedRooms.add(room);
            }
        }
        return bookedRooms;
    }

    public double calculateTotalRevenue() {
        String sql = "SELECT SUM(total_bill) AS total FROM guest_history";
        double totalRevenue = 0;
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                totalRevenue = rs.getDouble("total");
            }
        } catch (SQLException e) {
            System.err.println("Error calculating revenue: " + e.getMessage());
        }
        
        return totalRevenue;
    }

    public void addGuestToHistory(String guestName, int roomNumber, int nightsStayed, double totalBill) {
        String entry = "Guest: " + guestName + ", Room: " + roomNumber + 
                       ", Nights: " + nightsStayed + ", Total Bill: $" + totalBill;
        guestHistory.add(entry);
    }

    public ArrayList<String> getGuestHistory() {
        return guestHistory;
    }
    
    public ArrayList<String> getCanceledPreBookings() {
        ArrayList<String> canceled = new ArrayList<>();
        String sql = "SELECT * FROM canceled_pre_bookings_history ORDER BY cancellation_date DESC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String entry = "Room: " + rs.getInt("room_number") + 
                               ", Guest: " + rs.getString("guest_name") + 
                               ", Dates: " + rs.getDate("original_check_in_date") + 
                               " to " + rs.getDate("original_check_out_date") +
                               ", Canceled: " + rs.getTimestamp("cancellation_date");
                canceled.add(entry);
            }
        } catch (SQLException e) {
            System.err.println("Error loading canceled pre-bookings: " + e.getMessage());
        }
        
        return canceled;
    }
}

public class Project extends JFrame {
    private Hotel hotel;
    private JTable table;
    private DefaultTableModel tableModel;
    private final Color PRIMARY_COLOR = new Color(0, 120, 215);
    private final Color SECONDARY_COLOR = new Color(30, 40, 50);
    private final Color ACCENT_COLOR = new Color(100, 180, 255);
    private final Color BACKGROUND_COLOR = new Color(245, 248, 250);
    private final Color TABLE_HEADER_COLOR = new Color(50, 65, 80);
    private final Color BOOKED_ROOM_COLOR = new Color(255, 200, 200);
    private final Color AVAILABLE_ROOM_COLOR = new Color(220, 255, 220);

    public Project() {
        hotel = new Hotel();
        setTitle("Hotel Management System - Dashboard");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BACKGROUND_COLOR);

        // Create a modern title bar
        JPanel titleBar = createTitleBar();
        add(titleBar, BorderLayout.NORTH);

        // Main content panel with shadow effect
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(BACKGROUND_COLOR);

        // Create the room table with modern styling
        createRoomTable();
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.WHITE);
        
        // Add shadow effect to the table
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new DropShadowBorder(Color.GRAY, 5, 0.5f, 12, true, true, true, true));
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        tablePanel.setOpaque(false);
        
        mainPanel.add(tablePanel, BorderLayout.CENTER);

        // Create the button panel with modern styling
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);

        // Update the table with room data
        updateTable();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(SECONDARY_COLOR);
        titleBar.setPreferredSize(new Dimension(getWidth(), 60));
        titleBar.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 5));

        // Title label - now centered
        JLabel titleLabel = new JLabel("HOTEL MANAGEMENT SYSTEM", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        titleBar.add(titleLabel, BorderLayout.CENTER);

        // Window controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        controlPanel.setOpaque(false);
        
        // Minimize button
        JButton minimizeButton = new JButton("−");
        styleControlButton(minimizeButton, new Color(80, 80, 80));
        minimizeButton.addActionListener(e -> setState(JFrame.ICONIFIED));
        
        // Close button
        JButton closeButton = new JButton("×");
        styleControlButton(closeButton, new Color(200, 60, 60));
        closeButton.addActionListener(e -> System.exit(0));
        
        controlPanel.add(minimizeButton);
        controlPanel.add(closeButton);
        titleBar.add(controlPanel, BorderLayout.EAST);

        // Make draggable
        MouseAdapter ma = new MouseAdapter() {
            private Point dragOffset;
            
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }
            
            public void mouseDragged(MouseEvent e) {
                Point newLocation = e.getLocationOnScreen();
                newLocation.translate(-dragOffset.x, -dragOffset.y);
                setLocation(newLocation);
            }
        };
        
        titleBar.addMouseListener(ma);
        titleBar.addMouseMotionListener(ma);

        return titleBar;
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

    private void createRoomTable() {
        String[] columns = {"Room Number", "Room Type", "Price per Night", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        table = new JTable(tableModel);
        table.setRowHeight(40);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Custom header renderer with improved styling
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setBackground(TABLE_HEADER_COLOR);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getWidth(), 45));
        
        // Custom cell renderer with better color contrast
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                // Set default colors
                c.setForeground(Color.BLACK);
                
                // Highlight booked rooms
                String status = table.getValueAt(row, 3).toString();
                if (status.startsWith("Booked")) {
                    c.setBackground(BOOKED_ROOM_COLOR);
                } else {
                    c.setBackground(AVAILABLE_ROOM_COLOR);
                }
                
                // Selection highlight
                if (isSelected) {
                    c.setBackground(new Color(180, 220, 255)); // Light blue for selection
                }
                
                // Center align all cells
                ((JLabel)c).setHorizontalAlignment(SwingConstants.CENTER);
                
                return c;
            }
        });
        
        // Center align the header with custom renderer
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBackground(TABLE_HEADER_COLOR);
                label.setForeground(Color.WHITE);
                label.setFont(new Font("Segoe UI", Font.BOLD, 14));
                return label;
            }
        };
        
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridLayout(4, 3, 15, 15));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        buttonPanel.setBackground(BACKGROUND_COLOR);
        
        String[] buttonLabels = {
            "Book Room", "Check-out", "Search Guest",
            "Cancel Booking", "View Revenue", "Pre-book Room",
            "View Pre-Booked", "Cancel Pre-Book", "View Canceled Pre-Books",
            "Guest History", "Monthly Report", "Refresh"
        };
        
        for (String label : buttonLabels) {
            JButton button = new JButton(label);
            styleDashboardButton(button);
            buttonPanel.add(button);
            
            // Add action listeners based on button label
            switch (label) {
                case "Book Room":
                    button.addActionListener(e -> bookRoom());
                    break;
                case "Check-out":
                    button.addActionListener(e -> checkoutRoom());
                    break;
                case "Search Guest":
                    button.addActionListener(e -> searchGuest());
                    break;
                case "Cancel Booking":
                    button.addActionListener(e -> cancelBooking());
                    break;
                case "View Revenue":
                    button.addActionListener(e -> showRevenue());
                    break;
                case "Pre-book Room":
                    button.addActionListener(e -> preBookRoom());
                    break;
                case "View Pre-Booked":
                    button.addActionListener(e -> viewPreBookedRooms());
                    break;
                case "Cancel Pre-Book":
                    button.addActionListener(e -> cancelPreBooking());
                    break;
                case "View Canceled Pre-Books":
                    button.addActionListener(e -> viewCanceledPreBookings());
                    break;
                case "Guest History":
                    button.addActionListener(e -> showGuestHistory());
                    break;
                case "Monthly Report":
                    button.addActionListener(e -> generateMonthlyReport());
                    break;
                case "Refresh":
                    button.addActionListener(e -> updateTable());
                    break;
            }
        }
        
        return buttonPanel;
    }

    private void styleDashboardButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.BLACK);
        button.setBackground(PRIMARY_COLOR);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(240, 240, 240), 1),
            BorderFactory.createEmptyBorder(12, 25, 12, 25)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        button.addMouseListener(new MouseAdapter() {
            Color originalColor = button.getBackground();
            
            public void mouseEntered(MouseEvent e) {
                button.setBackground(originalColor.brighter());
            }
            
            public void mouseExited(MouseEvent e) {
                button.setBackground(originalColor);
            }
        });
    }

    private void generateMonthlyReport() {
        // Create input dialog for month and year selection
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        JTextField yearField = new JTextField(String.valueOf(java.time.Year.now().getValue()));
        JComboBox<String> monthCombo = new JComboBox<>(new String[]{
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        });
        monthCombo.setSelectedIndex(java.time.LocalDate.now().getMonthValue() - 1);
        
        inputPanel.add(new JLabel("Year:"));
        inputPanel.add(yearField);
        inputPanel.add(new JLabel("Month:"));
        inputPanel.add(monthCombo);

        int result = JOptionPane.showConfirmDialog(
            this, 
            inputPanel, 
            "Select Month for Report", 
            JOptionPane.OK_CANCEL_OPTION, 
            JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            int year = Integer.parseInt(yearField.getText());
            int month = monthCombo.getSelectedIndex() + 1;

            // Create a tabbed pane for different report sections
            JTabbedPane tabbedPane = new JTabbedPane();
            tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));

            // 1. Summary Tab with KPIs
            JPanel summaryPanel = createSummaryPanel(year, month);
            tabbedPane.addTab("Summary", summaryPanel);

            // 2. Detailed Bookings Tab
            JPanel bookingsPanel = createBookingsPanel(year, month);
            tabbedPane.addTab("Bookings", bookingsPanel);

            // 3. Room Statistics Tab
            JPanel roomStatsPanel = createRoomStatsPanel(year, month);
            tabbedPane.addTab("Room Stats", roomStatsPanel);

            // 4. Revenue Analysis Tab
            JPanel revenuePanel = createRevenueAnalysisPanel(year, month);
            tabbedPane.addTab("Revenue Analysis", revenuePanel);

            // 5. Guest Statistics Tab
            JPanel guestPanel = createGuestStatisticsPanel(year, month);
            tabbedPane.addTab("Guest Stats", guestPanel);

            // Display the tabbed pane in a dialog
            JDialog reportDialog = new JDialog(this, "Monthly Report - " + monthCombo.getSelectedItem() + " " + year, true);
            reportDialog.setSize(1000, 700);
            reportDialog.setLayout(new BorderLayout());
            reportDialog.add(tabbedPane, BorderLayout.CENTER);
            
            // Add export and close buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            JButton exportButton = new JButton("Export to CSV");
            exportButton.addActionListener(e -> exportReportToCSV(year, month));
            styleReportButton(exportButton);
            
            JButton closeButton = new JButton("Close");
            closeButton.addActionListener(e -> reportDialog.dispose());
            styleReportButton(closeButton);
            
            buttonPanel.add(exportButton);
            buttonPanel.add(closeButton);
            reportDialog.add(buttonPanel, BorderLayout.SOUTH);
            
            reportDialog.setLocationRelativeTo(this);
            reportDialog.setVisible(true);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Please enter a valid year", 
                "Input Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void styleReportButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBackground(PRIMARY_COLOR);
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
    }

    private JPanel createSummaryPanel(int year, int month) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create a grid for KPIs
        JPanel kpiPanel = new JPanel(new GridLayout(2, 3, 10, 10));
        kpiPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // 1. Total Revenue
            String revenueSql = "SELECT SUM(total_bill) AS monthly_revenue FROM guest_history " +
                               "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
            double monthlyRevenue = 0;
            try (PreparedStatement stmt = conn.prepareStatement(revenueSql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    monthlyRevenue = rs.getDouble("monthly_revenue");
                }
            }
            kpiPanel.add(createKPICard("Total Revenue", String.format("$%.2f", monthlyRevenue), 
                          new Color(76, 175, 80)));

            // 2. Occupancy Rate
            String occupancySql = "SELECT COUNT(DISTINCT room_number) AS occupied_rooms FROM guest_history " +
                                 "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
            int occupiedRooms = 0;
            try (PreparedStatement stmt = conn.prepareStatement(occupancySql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    occupiedRooms = rs.getInt("occupied_rooms");
                }
            }
            double occupancyRate = (occupiedRooms * 100.0) / hotel.getRooms().size();
            kpiPanel.add(createKPICard("Occupancy Rate", String.format("%.1f%%", occupancyRate), 
                          new Color(33, 150, 243)));

            // 3. Unique Guests
            String guestSql = "SELECT COUNT(DISTINCT guest_name) AS unique_guests FROM guest_history " +
                             "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
            int uniqueGuests = 0;
            try (PreparedStatement stmt = conn.prepareStatement(guestSql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    uniqueGuests = rs.getInt("unique_guests");
                }
            }
            kpiPanel.add(createKPICard("Unique Guests", String.valueOf(uniqueGuests), 
                          new Color(255, 152, 0)));

            // 4. Total Nights
            String nightsSql = "SELECT SUM(nights_stayed) AS total_nights FROM guest_history " +
                              "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
            int totalNights = 0;
            try (PreparedStatement stmt = conn.prepareStatement(nightsSql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    totalNights = rs.getInt("total_nights");
                }
            }
            kpiPanel.add(createKPICard("Total Nights", String.valueOf(totalNights), 
                          new Color(156, 39, 176)));

            // 5. Average Stay Length
            double avgStayLength = occupiedRooms > 0 ? (double) totalNights / occupiedRooms : 0;
            kpiPanel.add(createKPICard("Avg Stay Length", String.format("%.1f nights", avgStayLength), 
                          new Color(244, 67, 54)));

            // 6. Revenue per Room
            double revenuePerRoom = occupiedRooms > 0 ? monthlyRevenue / occupiedRooms : 0;
            kpiPanel.add(createKPICard("Revenue per Room", String.format("$%.2f", revenuePerRoom), 
                          new Color(0, 150, 136)));

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error generating report: " + ex.getMessage(), 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE
            );
            return panel;
        }

        panel.add(kpiPanel, BorderLayout.NORTH);
        
        // Add a chart showing daily revenue
        JPanel chartPanel = createDailyRevenueChart(year, month);
        panel.add(chartPanel, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createKPICard(String title, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker(), 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        card.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
        
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(color.darker());
        
        JLabel valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLabel.setForeground(color.darker());
        
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        
        return card;
    }

    private JPanel createDailyRevenueChart(int year, int month) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT DAY(check_out_date) AS day, SUM(total_bill) AS daily_revenue " +
                         "FROM guest_history " +
                         "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ? " +
                         "GROUP BY DAY(check_out_date) " +
                         "ORDER BY day";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    int day = rs.getInt("day");
                    double revenue = rs.getDouble("daily_revenue");
                    dataset.addValue(revenue, "Revenue", String.valueOf(day));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error loading chart data: " + ex.getMessage(), 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
        
        // Create chart
        JFreeChart chart = ChartFactory.createBarChart(
            "Daily Revenue Breakdown - " + getMonthName(month) + " " + year,
            "Day of Month",
            "Revenue ($)",
            dataset,
            PlotOrientation.VERTICAL,
            false,
            true,
            false
        );
        
        // Customize chart appearance
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, PRIMARY_COLOR);
        renderer.setShadowVisible(false);
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setDrawBarOutline(false);
        
        // Add chart to panel
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(500, 300));
        panel.add(chartPanel, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createBookingsPanel(int year, int month) {
        JPanel panel = new JPanel(new BorderLayout());
        
        String[] columns = {"Guest Name", "Room No", "Room Type", "Check-In", "Check-Out", "Nights", "Revenue"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setRowHeight(25);
        
        // Add sorting capability
        table.setAutoCreateRowSorter(true);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT gh.guest_name, gh.room_number, r.room_type, " +
                         "gh.check_in_date, gh.check_out_date, gh.nights_stayed, gh.total_bill " +
                         "FROM guest_history gh " +
                         "JOIN rooms r ON gh.room_number = r.room_number " +
                         "WHERE MONTH(gh.check_out_date) = ? AND YEAR(gh.check_out_date) = ? " +
                         "ORDER BY gh.check_out_date DESC";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getString("guest_name"),
                        rs.getInt("room_number"),
                        rs.getString("room_type"),
                        rs.getDate("check_in_date"),
                        rs.getDate("check_out_date"),
                        rs.getInt("nights_stayed"),
                        String.format("$%.2f", rs.getDouble("total_bill"))
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error loading bookings: " + ex.getMessage(), 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
        
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createRoomStatsPanel(int year, int month) {
        JPanel panel = new JPanel(new BorderLayout());
        
        String[] columns = {"Room Type", "Bookings", "Nights", "Revenue", "Avg Revenue", "Occupancy Rate"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setRowHeight(25);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get total rooms by type
            Map<String, Integer> totalRoomsByType = new HashMap<>();
            String roomCountSql = "SELECT room_type, COUNT(*) as count FROM rooms GROUP BY room_type";
            try (PreparedStatement stmt = conn.prepareStatement(roomCountSql)) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    totalRoomsByType.put(rs.getString("room_type"), rs.getInt("count"));
                }
            }
            
            // Get room statistics
            String sql = "SELECT r.room_type, " +
                         "COUNT(*) AS bookings, " +
                         "SUM(gh.nights_stayed) AS nights, " +
                         "SUM(gh.total_bill) AS revenue " +
                         "FROM guest_history gh " +
                         "JOIN rooms r ON gh.room_number = r.room_number " +
                         "WHERE MONTH(gh.check_out_date) = ? AND YEAR(gh.check_out_date) = ? " +
                         "GROUP BY r.room_type " +
                         "ORDER BY revenue DESC";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    String roomType = rs.getString("room_type");
                    int bookings = rs.getInt("bookings");
                    double revenue = rs.getDouble("revenue");
                    double avgRevenue = bookings > 0 ? revenue / bookings : 0;
                    
                    // Calculate occupancy rate
                    int totalRooms = totalRoomsByType.getOrDefault(roomType, 1);
                    double occupancyRate = (bookings * 100.0) / (totalRooms * getDaysInMonth(year, month));
                    
                    model.addRow(new Object[]{
                        roomType,
                        bookings,
                        rs.getInt("nights"),
                        String.format("$%.2f", revenue),
                        String.format("$%.2f", avgRevenue),
                        String.format("%.1f%%", occupancyRate)
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error loading room stats: " + ex.getMessage(), 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
        
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createRevenueAnalysisPanel(int year, int month) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create a split pane for charts
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.5);
        
        // 1. Revenue by Room Type (Pie Chart)
        JFreeChart pieChart = createRevenueByRoomTypeChart(year, month);
        ChartPanel pieChartPanel = new ChartPanel(pieChart);
        pieChartPanel.setPreferredSize(new Dimension(500, 300));
        
        // 2. Revenue Trend (Line Chart)
        JFreeChart lineChart = createRevenueTrendChart(year, month);
        ChartPanel lineChartPanel = new ChartPanel(lineChart);
        lineChartPanel.setPreferredSize(new Dimension(500, 300));
        
        splitPane.setTopComponent(pieChartPanel);
        splitPane.setBottomComponent(lineChartPanel);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JFreeChart createRevenueByRoomTypeChart(int year, int month) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT r.room_type, SUM(gh.total_bill) AS revenue " +
                         "FROM guest_history gh " +
                         "JOIN rooms r ON gh.room_number = r.room_number " +
                         "WHERE MONTH(gh.check_out_date) = ? AND YEAR(gh.check_out_date) = ? " +
                         "GROUP BY r.room_type";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    dataset.setValue(rs.getString("room_type"), rs.getDouble("revenue"));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error loading revenue data: " + ex.getMessage(), 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
        
        JFreeChart chart = ChartFactory.createPieChart(
            "Revenue by Room Type - " + getMonthName(month) + " " + year,
            dataset,
            true,
            true,
            false
        );
        
        // Customize chart appearance
        chart.setBackgroundPaint(Color.WHITE);
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        
        // Set custom colors
        plot.setSectionPaint(0, new Color(79, 129, 189));
        plot.setSectionPaint(1, new Color(192, 80, 77));
        plot.setSectionPaint(2, new Color(155, 187, 89));
        plot.setSectionPaint(3, new Color(128, 100, 162));
        
        return chart;
    }

    private JFreeChart createRevenueTrendChart(int year, int month) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get data for the selected month and previous 5 months
            for (int i = 5; i >= 0; i--) {
                LocalDate date = LocalDate.of(year, month, 1).minusMonths(i);
                int m = date.getMonthValue();
                int y = date.getYear();
                
                String sql = "SELECT SUM(total_bill) AS revenue FROM guest_history " +
                             "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, m);
                    stmt.setInt(2, y);
                    ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next()) {
                        double revenue = rs.getDouble("revenue");
                        dataset.addValue(revenue, "Revenue", 
                            getMonthName(m) + " " + y);
                    }
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error loading trend data: " + ex.getMessage(), 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
        
        JFreeChart chart = ChartFactory.createLineChart(
            "Revenue Trend (Last 6 Months)",
            "Month",
            "Revenue ($)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        
        // Customize chart appearance
        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, PRIMARY_COLOR);
        renderer.setSeriesStroke(0, new BasicStroke(2.5f));
        renderer.setSeriesShapesVisible(0, true);
        
        return chart;
    }

    private JPanel createGuestStatisticsPanel(int year, int month) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create a split pane for guest statistics
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.5);
        
        // 1. Top Guests by Revenue (Table)
        JPanel topGuestsPanel = createTopGuestsPanel(year, month);
        
        // 2. Guest Origin Chart (Pie Chart)
        JFreeChart originChart = createGuestOriginChart(year, month);
        ChartPanel originChartPanel = new ChartPanel(originChart);
        
        splitPane.setLeftComponent(topGuestsPanel);
        splitPane.setRightComponent(originChartPanel);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createTopGuestsPanel(int year, int month) {
        JPanel panel = new JPanel(new BorderLayout());
        
        String[] columns = {"Guest Name", "Visits", "Total Nights", "Total Revenue"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setRowHeight(25);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT guest_name, " +
                         "COUNT(*) AS visits, " +
                         "SUM(nights_stayed) AS total_nights, " +
                         "SUM(total_bill) AS total_revenue " +
                         "FROM guest_history " +
                         "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ? " +
                         "GROUP BY guest_name " +
                         "ORDER BY total_revenue DESC " +
                         "LIMIT 10";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getString("guest_name"),
                        rs.getInt("visits"),
                        rs.getInt("total_nights"),
                        String.format("$%.2f", rs.getDouble("total_revenue"))
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error loading guest data: " + ex.getMessage(), 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
        
        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JFreeChart createGuestOriginChart(int year, int month) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // This is a simplified example - in a real app you'd have guest location data
            // For demo purposes, we'll assume the guest name contains location info
            String sql = "SELECT guest_name FROM guest_history " +
                         "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
            
            Map<String, Integer> originCounts = new HashMap<>();
            originCounts.put("Local", 0);
            originCounts.put("National", 0);
            originCounts.put("International", 0);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, month);
                stmt.setInt(2, year);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    String guestName = rs.getString("guest_name");
                    // Simple heuristic for demo - in real app use actual location data
                    if (guestName.contains("@")) {
                        originCounts.put("International", originCounts.get("International") + 1);
                    } else if (guestName.split(" ").length > 2) {
                        originCounts.put("National", originCounts.get("National") + 1);
                    } else {
                        originCounts.put("Local", originCounts.get("Local") + 1);
                    }
                }
            }
            
            for (Map.Entry<String, Integer> entry : originCounts.entrySet()) {
                if (entry.getValue() > 0) {
                    dataset.setValue(entry.getKey(), entry.getValue());
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error loading origin data: " + ex.getMessage(), 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
        
        JFreeChart chart = ChartFactory.createPieChart(
            "Guest Origin Distribution - " + getMonthName(month) + " " + year,
            dataset,
            true,
            true,
            false
        );
        
        // Customize chart appearance
        chart.setBackgroundPaint(Color.WHITE);
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        
        // Set custom colors
        plot.setSectionPaint(0, new Color(79, 129, 189));
        plot.setSectionPaint(1, new Color(192, 80, 77));
        plot.setSectionPaint(2, new Color(155, 187, 89));
        
        return chart;
    }

    private void exportReportToCSV(int year, int month) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Report as CSV");
        fileChooser.setSelectedFile(new File("hotel_report_" + getMonthName(month) + "_" + year + ".csv"));
        
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        File file = fileChooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        }
        
        try (PrintWriter writer = new PrintWriter(file)) {
            // Write header
            writer.println("Hotel Management System - Monthly Report");
            writer.println("Month: " + getMonthName(month) + " " + year);
            writer.println("Generated on: " + LocalDate.now());
            writer.println();
            
            // 1. Summary Data
            writer.println("SUMMARY");
            writer.println("Metric,Value");
            
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Total Revenue
                String revenueSql = "SELECT SUM(total_bill) AS monthly_revenue FROM guest_history " +
                                   "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
                try (PreparedStatement stmt = conn.prepareStatement(revenueSql)) {
                    stmt.setInt(1, month);
                    stmt.setInt(2, year);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        writer.println("Total Revenue," + String.format("$%.2f", rs.getDouble("monthly_revenue")));
                    }
                }
                
                // Occupancy Rate
                String occupancySql = "SELECT COUNT(DISTINCT room_number) AS occupied_rooms FROM guest_history " +
                                     "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
                try (PreparedStatement stmt = conn.prepareStatement(occupancySql)) {
                    stmt.setInt(1, month);
                    stmt.setInt(2, year);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        double occupancyRate = (rs.getInt("occupied_rooms") * 100.0) / hotel.getRooms().size();
                        writer.println("Occupancy Rate," + String.format("%.1f%%", occupancyRate));
                    }
                }
                
                // Unique Guests
                String guestSql = "SELECT COUNT(DISTINCT guest_name) AS unique_guests FROM guest_history " +
                                 "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
                try (PreparedStatement stmt = conn.prepareStatement(guestSql)) {
                    stmt.setInt(1, month);
                    stmt.setInt(2, year);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        writer.println("Unique Guests," + rs.getInt("unique_guests"));
                    }
                }
                
                // Total Nights
                String nightsSql = "SELECT SUM(nights_stayed) AS total_nights FROM guest_history " +
                                  "WHERE MONTH(check_out_date) = ? AND YEAR(check_out_date) = ?";
                try (PreparedStatement stmt = conn.prepareStatement(nightsSql)) {
                    stmt.setInt(1, month);
                    stmt.setInt(2, year);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        writer.println("Total Nights," + rs.getInt("total_nights"));
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(
                    this, 
                    "Error exporting data: " + ex.getMessage(), 
                    "Database Error", 
                    JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            
            writer.println();
            
            // 2. Bookings Data
            writer.println("BOOKINGS");
            writer.println("Guest Name,Room No,Room Type,Check-In,Check-Out,Nights,Revenue");
            
            try (Connection conn = DatabaseConnection.getConnection()) {
                String sql = "SELECT gh.guest_name, gh.room_number, r.room_type, " +
                             "gh.check_in_date, gh.check_out_date, gh.nights_stayed, gh.total_bill " +
                             "FROM guest_history gh " +
                             "JOIN rooms r ON gh.room_number = r.room_number " +
                             "WHERE MONTH(gh.check_out_date) = ? AND YEAR(gh.check_out_date) = ? " +
                             "ORDER BY gh.check_out_date DESC";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, month);
                    stmt.setInt(2, year);
                    ResultSet rs = stmt.executeQuery();
                    
                    while (rs.next()) {
                        writer.println(String.format("\"%s\",%d,\"%s\",%s,%s,%d,%s",
                            rs.getString("guest_name"),
                            rs.getInt("room_number"),
                            rs.getString("room_type"),
                            rs.getDate("check_in_date"),
                            rs.getDate("check_out_date"),
                            rs.getInt("nights_stayed"),
                            String.format("$%.2f", rs.getDouble("total_bill"))
                        ));
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(
                    this, 
                    "Error exporting bookings: " + ex.getMessage(), 
                    "Database Error", 
                    JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            
            writer.println();
            
            // 3. Room Statistics
            writer.println("ROOM STATISTICS");
            writer.println("Room Type,Bookings,Nights,Revenue,Avg Revenue,Occupancy Rate");
            
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Get total rooms by type
                Map<String, Integer> totalRoomsByType = new HashMap<>();
                String roomCountSql = "SELECT room_type, COUNT(*) as count FROM rooms GROUP BY room_type";
                try (PreparedStatement stmt = conn.prepareStatement(roomCountSql)) {
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        totalRoomsByType.put(rs.getString("room_type"), rs.getInt("count"));
                    }
                }
                
                // Get room statistics
                String sql = "SELECT r.room_type, " +
                             "COUNT(*) AS bookings, " +
                             "SUM(gh.nights_stayed) AS nights, " +
                             "SUM(gh.total_bill) AS revenue " +
                             "FROM guest_history gh " +
                             "JOIN rooms r ON gh.room_number = r.room_number " +
                             "WHERE MONTH(gh.check_out_date) = ? AND YEAR(gh.check_out_date) = ? " +
                             "GROUP BY r.room_type " +
                             "ORDER BY revenue DESC";
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, month);
                    stmt.setInt(2, year);
                    ResultSet rs = stmt.executeQuery();
                    
                    while (rs.next()) {
                        String roomType = rs.getString("room_type");
                        int bookings = rs.getInt("bookings");
                        double revenue = rs.getDouble("revenue");
                        double avgRevenue = bookings > 0 ? revenue / bookings : 0;
                        
                        // Calculate occupancy rate
                        int totalRooms = totalRoomsByType.getOrDefault(roomType, 1);
                        double occupancyRate = (bookings * 100.0) / (totalRooms * getDaysInMonth(year, month));
                        
                        writer.println(String.format("\"%s\",%d,%d,%s,%s,%.1f%%",
                            roomType,
                            bookings,
                            rs.getInt("nights"),
                            String.format("$%.2f", revenue),
                            String.format("$%.2f", avgRevenue),
                            occupancyRate
                        ));
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(
                    this, 
                    "Error exporting room stats: " + ex.getMessage(), 
                    "Database Error", 
                    JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            
            JOptionPane.showMessageDialog(
                this, 
                "Report exported successfully to: " + file.getAbsolutePath(), 
                "Export Complete", 
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (FileNotFoundException ex) {
            JOptionPane.showMessageDialog(
                this, 
                "Error writing to file: " + ex.getMessage(), 
                "File Error", 
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private int getDaysInMonth(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return yearMonth.lengthOfMonth();
    }

    private String getMonthName(int month) {
        return new java.text.DateFormatSymbols().getMonths()[month - 1];
    }

    class RoundBorder extends AbstractBorder {
        private Color color;
        private int radius;
        
        public RoundBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width-1, height-1, radius, radius);
            g2.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius+1, radius+1, radius+1, radius+1);
        }
        
        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.right = radius+1;
            insets.top = insets.bottom = radius+1;
            return insets;
        }
    }

    class DropShadowBorder extends AbstractBorder {
        private Color shadowColor;
        private int shadowSize;
        private float shadowOpacity;
        private int cornerSize;
        private boolean showTopShadow;
        private boolean showLeftShadow;
        private boolean showBottomShadow;
        private boolean showRightShadow;
        
        public DropShadowBorder(Color shadowColor, int shadowSize, float shadowOpacity, 
                int cornerSize, boolean showTopShadow, boolean showLeftShadow, 
                boolean showBottomShadow, boolean showRightShadow) {
            this.shadowColor = shadowColor;
            this.shadowSize = shadowSize;
            this.shadowOpacity = shadowOpacity;
            this.cornerSize = cornerSize;
            this.showTopShadow = showTopShadow;
            this.showLeftShadow = showLeftShadow;
            this.showBottomShadow = showBottomShadow;
            this.showRightShadow = showRightShadow;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw the shadow
            for (int i = 0; i < shadowSize; i++) {
                float opacity = shadowOpacity * ((float)(shadowSize - i) / (float)shadowSize);
                g2.setColor(new Color(shadowColor.getRed()/255f, shadowColor.getGreen()/255f, 
                    shadowColor.getBlue()/255f, opacity));
                
                int offset = shadowSize - i;
                if (showRightShadow) {
                    g2.fillRoundRect(x + width + i - 1, y + offset, offset*2, height - offset*2, cornerSize, cornerSize);
                }
                if (showBottomShadow) {
                    g2.fillRoundRect(x + offset, y + height + i - 1, width - offset*2, offset*2, cornerSize, cornerSize);
                }
                if (showLeftShadow) {
                    g2.fillRoundRect(x - i, y + offset, offset*2, height - offset*2, cornerSize, cornerSize);
                }
                if (showTopShadow) {
                    g2.fillRoundRect(x + offset, y - i, width - offset*2, offset*2, cornerSize, cornerSize);
                }
            }
            
            g2.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            int top = showTopShadow ? shadowSize : 0;
            int left = showLeftShadow ? shadowSize : 0;
            int bottom = showBottomShadow ? shadowSize : 0;
            int right = showRightShadow ? shadowSize : 0;
            return new Insets(top, left, bottom, right);
        }
        
        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.top = showTopShadow ? shadowSize : 0;
            insets.left = showLeftShadow ? shadowSize : 0;
            insets.bottom = showBottomShadow ? shadowSize : 0;
            insets.right = showRightShadow ? shadowSize : 0;
            return insets;
        }
    }

    private void updateTable() {
        tableModel.setRowCount(0); // Clear table
        for (Room room : hotel.getRooms()) {
            tableModel.addRow(room.toTableRow()); // Add updated data
        }
        table.repaint();  // Force UI refresh to update row colors
    }

    private void bookRoom() {
        String roomNumberStr = JOptionPane.showInputDialog(this, "Enter Room Number:");
        if (roomNumberStr == null) return;

        try {
            int roomNumber = Integer.parseInt(roomNumberStr);
            Room room = hotel.findRoom(roomNumber);
            if (room == null || !room.isAvailable()) {
                JOptionPane.showMessageDialog(this, "Room is not available!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String guestName = JOptionPane.showInputDialog(this, "Enter Guest Name:");
            String nightsStr = JOptionPane.showInputDialog(this, "Enter Number of Nights:");
            if (guestName == null || nightsStr == null) return;

            int nights = Integer.parseInt(nightsStr);
            room.bookRoom(guestName, nights);
            updateTable();  // Refresh UI to change row color
            JOptionPane.showMessageDialog(this, "Room booked successfully!");
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void checkoutRoom() {
        String roomNumberStr = JOptionPane.showInputDialog(this, "Enter Room Number to Checkout:");
        if (roomNumberStr == null) return;

        try {
            int roomNumber = Integer.parseInt(roomNumberStr);
            Room room = hotel.findRoom(roomNumber);
            if (room == null || room.isAvailable()) {
                JOptionPane.showMessageDialog(this, "Invalid or vacant room!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            double totalBill = room.getTotalBill();
            double gst = totalBill * 0.10; // 10% GST
            double totalWithGST = totalBill + gst;

            hotel.addGuestToHistory(room.getGuestName(), room.getRoomNumber(), room.getNightsStayed(), totalWithGST);
            room.checkout();
            updateTable();  // Refresh UI to reset color
            JOptionPane.showMessageDialog(this, "Guest checked out successfully!\nTotal Bill: $" + totalBill + "\nGST: $" + gst + "\nTotal with GST: $" + totalWithGST);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchGuest() {
        String guestName = JOptionPane.showInputDialog(this, "Enter Guest Name:");
        if (guestName == null) return;

        for (Room room : hotel.getBookedRooms()) {
            if (room.getGuestName().equalsIgnoreCase(guestName)) {
                JOptionPane.showMessageDialog(this, "Guest Found!\nRoom: " + room.getRoomNumber() +
                        "\nNights Stayed: " + room.getNightsStayed());
                return;
            }
        }
        JOptionPane.showMessageDialog(this, "Guest not found!", "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void cancelBooking() {
        String roomNumberStr = JOptionPane.showInputDialog(this, "Enter Room Number to Cancel Booking:");
        if (roomNumberStr == null) return;

        try {
            int roomNumber = Integer.parseInt(roomNumberStr);
            Room room = hotel.findRoom(roomNumber);
            if (room == null || room.isAvailable()) {
                JOptionPane.showMessageDialog(this, "Room is not booked!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            room.checkout();
            updateTable();
            JOptionPane.showMessageDialog(this, "Booking canceled successfully!");
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showRevenue() {
        double totalRevenue = hotel.calculateTotalRevenue();
        JOptionPane.showMessageDialog(this, "Total Revenue: $" + totalRevenue);
    }

    private void preBookRoom() {
        String roomNumberStr = JOptionPane.showInputDialog(this, "Enter Room Number to Pre-book:");
        if (roomNumberStr == null) return;

        try {
            int roomNumber = Integer.parseInt(roomNumberStr);
            Room room = hotel.findRoom(roomNumber);
            if (room == null) {
                JOptionPane.showMessageDialog(this, "Invalid room number!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String checkInDate = JOptionPane.showInputDialog(this, "Enter Check-in Date (YYYY-MM-DD):");
            String checkOutDate = JOptionPane.showInputDialog(this, "Enter Check-out Date (YYYY-MM-DD):");
            if (checkInDate == null || checkOutDate == null) return;

            String dates = checkInDate + " to " + checkOutDate;

            if (room.isPreBooked(dates)) {
                JOptionPane.showMessageDialog(this, "Room is already pre-booked for these dates!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String guestName = JOptionPane.showInputDialog(this, "Enter Guest Name:");
            if (guestName == null) return;

            room.addPreBooking(guestName, dates);
            JOptionPane.showMessageDialog(this, "Room pre-booked successfully for " + dates);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancelPreBooking() {
        // First show all pre-booked rooms
        StringBuilder preBookedRooms = new StringBuilder();
        Map<Integer, Map<String, String>> allPreBookings = new HashMap<>();
        
        for (Room room : hotel.getRooms()) {
            Map<String, String> preBookings = room.getPreBookings();
            if (!preBookings.isEmpty()) {
                allPreBookings.put(room.getRoomNumber(), preBookings);
                preBookedRooms.append("Room: ").append(room.getRoomNumber()).append("\n");
                for (Map.Entry<String, String> entry : preBookings.entrySet()) {
                    preBookedRooms.append("Dates: ").append(entry.getKey()).append(", Guest: ").append(entry.getValue()).append("\n");
                }
                preBookedRooms.append("\n");
            }
        }
        
        if (allPreBookings.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No pre-booked rooms to cancel.", "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Show input dialog
        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField roomNumberField = new JTextField();
        JTextField datesField = new JTextField();
        
        inputPanel.add(new JLabel("Room Number:"));
        inputPanel.add(roomNumberField);
        inputPanel.add(new JLabel("Dates (YYYY-MM-DD to YYYY-MM-DD):"));
        inputPanel.add(datesField);
        inputPanel.add(new JLabel("Pre-bookings:"));
        inputPanel.add(new JLabel(preBookedRooms.toString()));
        
        int result = JOptionPane.showConfirmDialog(
            this, 
            inputPanel, 
            "Cancel Pre-Booking", 
            JOptionPane.OK_CANCEL_OPTION, 
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (result == JOptionPane.OK_OPTION) {
            try {
                int roomNumber = Integer.parseInt(roomNumberField.getText());
                String dates = datesField.getText();
                
                Room room = hotel.findRoom(roomNumber);
                if (room == null) {
                    JOptionPane.showMessageDialog(this, "Invalid room number!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                if (!room.getPreBookings().containsKey(dates)) {
                    JOptionPane.showMessageDialog(this, "No pre-booking found for these dates!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                room.cancelPreBooking(dates);
                JOptionPane.showMessageDialog(this, "Pre-booking canceled successfully!");
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Please enter a valid room number!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void viewCanceledPreBookings() {
        StringBuilder history = new StringBuilder("Canceled Pre-Bookings History:\n\n");
        for (String entry : hotel.getCanceledPreBookings()) {
            history.append(entry).append("\n");
        }
        
        if (history.length() == "Canceled Pre-Bookings History:\n\n".length()) {
            history.append("No canceled pre-bookings found.");
        }
        
        JTextArea textArea = new JTextArea(history.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        
        JOptionPane.showMessageDialog(
            this, 
            scrollPane, 
            "Canceled Pre-Bookings History", 
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void viewPreBookedRooms() {
        StringBuilder preBookedRooms = new StringBuilder();
        for (Room room : hotel.getRooms()) {
            Map<String, String> preBookings = room.getPreBookings();
            if (!preBookings.isEmpty()) {
                preBookedRooms.append("Room: ").append(room.getRoomNumber()).append("\n");
                for (Map.Entry<String, String> entry : preBookings.entrySet()) {
                    preBookedRooms.append("Dates: ").append(entry.getKey()).append(", Guest: ").append(entry.getValue()).append("\n");
                }
                preBookedRooms.append("\n");
            }
        }
        if (preBookedRooms.length() == 0) {
            preBookedRooms.append("No pre-booked rooms.");
        }
        JOptionPane.showMessageDialog(this, preBookedRooms.toString(), "Pre-Booked Rooms", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showGuestHistory() {
        StringBuilder history = new StringBuilder();
        for (String entry : hotel.getGuestHistory()) {
            history.append(entry).append("\n");
        }
        JOptionPane.showMessageDialog(this, history.toString(), "Guest History", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void initializeDatabase() {
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
                // Create database and tables
                stmt.executeUpdate("CREATE DATABASE hotel_management");
                stmt.executeUpdate("USE hotel_management");
                
                // Create tables
                stmt.executeUpdate("CREATE TABLE rooms (" +
                    "room_number INT PRIMARY KEY, " +
                    "room_type VARCHAR(20), " +
                    "price_per_night DECIMAL(10,2), " +
                    "is_booked BOOLEAN DEFAULT false)");
                
                stmt.executeUpdate("CREATE TABLE bookings (" +
                    "booking_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "room_number INT, " +
                    "guest_name VARCHAR(100), " +
                    "check_in_date DATE, " +
                    "check_out_date DATE, " +
                    "nights_stayed INT, " +
                    "total_bill DECIMAL(10,2), " +
                    "is_active BOOLEAN DEFAULT true, " +
                    "FOREIGN KEY (room_number) REFERENCES rooms(room_number))");
                
                stmt.executeUpdate("CREATE TABLE guest_history (" +
                    "history_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "guest_name VARCHAR(100), " +
                    "room_number INT, " +
                    "check_in_date DATE, " +
                    "check_out_date DATE, " +
                    "nights_stayed INT, " +
                    "total_bill DECIMAL(10,2))");
                
                stmt.executeUpdate("CREATE TABLE pre_bookings (" +
                    "pre_booking_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "room_number INT, " +
                    "guest_name VARCHAR(100), " +
                    "check_in_date DATE, " +
                    "check_out_date DATE, " +
                    "FOREIGN KEY (room_number) REFERENCES rooms(room_number))");
                
                // Add the new table for canceled pre-bookings history
                stmt.executeUpdate("CREATE TABLE canceled_pre_bookings_history (" +
                    "history_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "room_number INT, " +
                    "guest_name VARCHAR(100), " +
                    "original_check_in_date DATE, " +
                    "original_check_out_date DATE, " +
                    "cancellation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (room_number) REFERENCES rooms(room_number))");
                
                System.out.println("Database and all tables created successfully");
            } else {
                // Database exists, check if the new table exists
                stmt.executeUpdate("USE hotel_management");
                DatabaseMetaData dbm = conn.getMetaData();
                rs = dbm.getTables(null, null, "canceled_pre_bookings_history", null);
                
                if (!rs.next()) {
                    // Table doesn't exist, create it
                    stmt.executeUpdate("CREATE TABLE canceled_pre_bookings_history (" +
                        "history_id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "room_number INT, " +
                        "guest_name VARCHAR(100), " +
                        "original_check_in_date DATE, " +
                        "original_check_out_date DATE, " +
                        "cancellation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "FOREIGN KEY (room_number) REFERENCES rooms(room_number))");
                    System.out.println("Added canceled_pre_bookings_history table to existing database");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        // Initialize database first
        initializeDatabase();
        
        // Then start the application
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new Project();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}