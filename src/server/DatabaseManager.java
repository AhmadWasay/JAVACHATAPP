package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/javachat";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "1234";

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

    public static boolean registerUser(String username, String password, String email) {

        // Note: We only check if the username exists here.
        // If you want to prevent duplicate emails, you should also check getUsernameByEmail(email) here.
        if (checkLogin(username, password) != null) return false; 

        String sql = "INSERT INTO users (username, password, email) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, email);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<String> getAllUsernames() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username ASC";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public static String getUsernameByEmail(String email) {
        String sql = "SELECT username FROM users WHERE email = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // *** UPDATED METHOD: Allows Login via Username OR Email ***
    public static String checkLogin(String userIdentifier, String password) {
        // Query checks if the identifier matches the username OR the email
        String sql = "SELECT username FROM users WHERE (username = ? OR email = ?) AND password = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userIdentifier); // Check if it's a username
            pstmt.setString(2, userIdentifier); // Check if it's an email
            pstmt.setString(3, password);       // Check password
            
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("username"); // Return the official username
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

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

    public static List<String> getChatHistory(String username) {
        List<String> history = new ArrayList<>();
        
        String sql = "SELECT sender, receiver, content, timestamp FROM messages " +
                     "WHERE receiver = 'ALL' OR receiver = ? OR sender = ? " +
                     "ORDER BY timestamp DESC LIMIT 50";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String s = rs.getString("sender");
                String r = rs.getString("receiver");
                String msg = rs.getString("content");
                
                if (r.equals("ALL")) {
                    history.add("MSG " + s + " " + msg);
                } else {
                    if (s.equals(username)) {
                        history.add("MSG Me -> " + r + ": " + msg);
                    } else {
                        history.add("MSG " + s + " (Private): " + msg);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        Collections.reverse(history);
        return history;
    }
}