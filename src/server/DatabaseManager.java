package server;

import java.sql.*;

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

        if (checkLogin(username, password) != null) return false; 

        String sql = "INSERT INTO users (username, password, email) VALUES (?, ?, ?)";
        try (java.sql.Connection conn = getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, email);
            pstmt.executeUpdate();
            return true;
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

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

    public static String getUsernameByEmail(String email) {

        String sql = "SELECT username FROM users WHERE email = ?";

        try (java.sql.Connection conn = getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, email);
            java.sql.ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String checkLogin(String username, String password) {
        String sql = "SELECT username FROM users WHERE username = ? AND password = ?";
        
        try (java.sql.Connection conn = getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            
            java.sql.ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (java.sql.SQLException e) {
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

    public static java.util.List<String> getChatHistory(String username) {
        java.util.List<String> history = new java.util.ArrayList<>();
        
        String sql = "SELECT sender, receiver, content, timestamp FROM messages " +
                     "WHERE receiver = 'ALL' OR receiver = ? OR sender = ? " +
                     "ORDER BY timestamp DESC LIMIT 50";

        try (java.sql.Connection conn = getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            
            java.sql.ResultSet rs = pstmt.executeQuery();

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
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }
        
        java.util.Collections.reverse(history);
        return history;
    }
}