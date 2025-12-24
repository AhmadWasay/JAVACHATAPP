package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/javachat";
    private static final String DB_USER = "root"; // CHANGE THIS to your MySQL user
    private static final String DB_PASS = "1234"; // CHANGE THIS to your MySQL password

    // Load the driver explicitly to avoid "No initial context" errors
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    /**
     * Register a new user. Returns false if username already exists.
     */
    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, password); // In a real app, hash this password!
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Registration failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validate login credentials.
     */
    public static boolean checkLogin(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String storedPass = rs.getString("password");
                return storedPass.equals(password);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Save a chat message to the database.
     */
    public static void saveMessage(String sender, String receiver, String content) {
        String sql = "INSERT INTO messages (sender, receiver, content) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve chat history between two users (WhatsApp style).
     * This fetches messages where (sender=A AND receiver=B) OR (sender=B AND receiver=A)
     * Ordered by time.
     */
    public static List<String> getChatHistory(String user1, String user2) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT sender, content, timestamp FROM messages " +
                     "WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) " +
                     "ORDER BY timestamp ASC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, user1);
            pstmt.setString(2, user2);
            pstmt.setString(3, user2); // Flip for the OR condition
            pstmt.setString(4, user1);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String content = rs.getString("content");
                // Format: "Alice: Hello there"
                history.add(sender + ": " + content);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }
}