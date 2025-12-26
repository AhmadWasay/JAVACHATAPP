package server;

import java.sql.*;


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

    // Add this to DatabaseManager.java
    public static java.util.List<String> getAllUsernames() {
        java.util.List<String> users = new java.util.ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username ASC";
        
        try (java.sql.Connection conn = getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    /**
     * Validate login credentials.
     */
    // Update this method in DatabaseManager.java
    public static String checkLogin(String username, String password) {
        // SQL: Find the user with this name (ignoring case) and password
        // We select the 'username' column so we get the OFFICIAL casing (e.g. "Abdullah")
        String sql = "SELECT username FROM users WHERE username = ? AND password = ?";
        
        try (java.sql.Connection conn = getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            
            java.sql.ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // Return the official name from the database
                return rs.getString("username");
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        return null; // Login failed
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
    // Add this to DatabaseManager.java
    public static java.util.List<String> getChatHistory(String username) {
        java.util.List<String> history = new java.util.ArrayList<>();
        
        // Query: Get messages where:
        // 1. Receiver is 'ALL' (Public chat)
        // 2. Receiver is ME (Private msg to me)
        // 3. Sender is ME (Private msg I sent)
        String sql = "SELECT sender, receiver, content, timestamp FROM messages " +
                     "WHERE receiver = 'ALL' OR receiver = ? OR sender = ? " +
                     "ORDER BY timestamp DESC LIMIT 50"; // Get newest 50, then we reverse them

        try (java.sql.Connection conn = getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            
            java.sql.ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String s = rs.getString("sender");
                String r = rs.getString("receiver");
                String msg = rs.getString("content");
                
                // Format the message so the Client understands it
                if (r.equals("ALL")) {
                    // Public: MSG Sender Content
                    history.add("MSG " + s + " " + msg);
                } else {
                    // Private
                    if (s.equals(username)) {
                        // I sent it: MSG Me -> Receiver: Content
                        history.add("MSG Me -> " + r + ": " + msg);
                    } else {
                        // I received it: MSG Sender (Private): Content
                        history.add("MSG " + s + " (Private): " + msg);
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        
        // The query gave us Newest -> Oldest. We want Oldest -> Newest for chat.
        java.util.Collections.reverse(history);
        return history;
    }
}