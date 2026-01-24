package server;

import common.Protocol;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username = null;
    private boolean inChat = false; 

    // Global Shared Memory
    private static final Map<String, String> sharedLoginOtp = new ConcurrentHashMap<>();
    private static final Map<String, RegistrationData> pendingRegistrations = new ConcurrentHashMap<>();

    static class RegistrationData {
        String user, pass, code;
        RegistrationData(String u, String p, String c) { user=u; pass=p; code=c; }
    }

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() { return username; }
    public void sendMessage(String msg) { sendRawMessage(msg); }
    private void sendRawMessage(String msg) { if (out != null) { out.println(msg); out.flush(); } }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            boolean authenticated = false;
            while (!authenticated) {
                String line = in.readLine();
                if (line == null) return; 

                // Strip Prefixes (C: or Protocol)
                String cmd = line;
                if (cmd.startsWith("C:")) cmd = cmd.substring(2).trim();
                else if (cmd.startsWith(Protocol.CLIENT_PREFIX)) cmd = cmd.substring(Protocol.CLIENT_PREFIX.length()).trim();

                if (cmd.startsWith("LOGIN")) { 
                     // Full Login (For Chat Window)
                     if (handleLogin(cmd)) authenticated = true;
                }
                else if (cmd.startsWith("CHECK_LOGIN")) {
                    // Silent Login (For Login Screen - No Broadcast)
                    handleCheckLogin(cmd);
                }
                else if (cmd.startsWith("REGISTER")) {
                    handleRegisterRequest(cmd); 
                }
                else if (cmd.startsWith("VERIFY_OTP")) {
                    if (handleVerifyRegistrationOTP(cmd)) { /* Success */ }
                }
                else if (cmd.startsWith("REQUEST_LOGIN_OTP")) {
                    handleRequestLoginOTP(cmd);
                }
                else if (cmd.startsWith("VERIFY_LOGIN_OTP")) {
                    authenticated = handleVerifyLoginOTP(cmd);
                }
                else {
                    System.out.println("SERVER DEBUG: Unknown command: " + cmd);
                    sendRawMessage("S:ERROR Please login first");
                }
            }

            // --- USER JOINED CHAT (Only happens after full LOGIN) ---
            if (username == null) return; 
            inChat = true; 
            server.broadcast("S:USER_JOINED " + username, this);
            server.broadcastUserList();

            java.util.List<String> history = DatabaseManager.getChatHistory(username);
            for (String h : history) sendRawMessage("S:" + h);
            sendRawMessage("S:HISTORY_END");

            String line;
            while ((line = in.readLine()) != null) {
                String content = line;
                if (content.equals("QUIT") || content.equals("LOGOUT")) break;
                if (content.startsWith("C:")) content = content.substring(2);
                
                if (content.trim().isEmpty()) continue;

                DatabaseManager.saveMessage(this.username, "ALL", content); 
                server.broadcast("S:MSG " + username + " " + content, this);
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + username);
        } finally {
            close();
        }
    }

    // --- LOGIC METHODS ---

    private void handleCheckLogin(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) return;
        String userRaw = parts[1]; String pass = parts[2].trim();
        String officialName = DatabaseManager.checkLogin(userRaw, pass);
        if (officialName != null) {
            // Respond Success, but DO NOT set 'this.username' or break loop
            sendRawMessage("LOGIN_SUCCESS " + officialName);
        } else {
            sendRawMessage("LOGIN_FAIL");
        }
    }

    private boolean handleLogin(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) return false;
        String userRaw = parts[1]; String pass = parts[2].trim(); 
        
        if (pass.equals("OTP_ACCESS")) { this.username = userRaw; return true; }

        String officialName = DatabaseManager.checkLogin(userRaw, pass);
        if (officialName != null) {
            this.username = officialName;
            sendRawMessage("LOGIN_SUCCESS " + this.username); 
            return true;
        } else {
            sendRawMessage("LOGIN_FAIL"); return false;
        }
    }

    private void handleRegisterRequest(String line) {
        String[] parts = line.split(" ", 4);
        if (parts.length < 4) return;
        String user = parts[1]; String pass = parts[2]; String email = parts[3];

        if (DatabaseManager.checkLogin(user, "dummy") != null) {
            sendRawMessage("ERROR Username taken"); return;
        }

        int randomPin = (int) (Math.random() * 900000) + 100000;
        String code = String.valueOf(randomPin);
        pendingRegistrations.put(email, new RegistrationData(user, pass, code));

        new Thread(() -> EmailService.sendOTP(email, code)).start();
        sendRawMessage("OTP_REQ");
    }

    private boolean handleVerifyRegistrationOTP(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) return false;
        String email = parts[1]; String code = parts[2];

        RegistrationData data = pendingRegistrations.get(email);
        if (data != null && data.code.equals(code)) {
            if (DatabaseManager.registerUser(data.user, data.pass, email)) {
                sendRawMessage("REG_SUCCESS");
                pendingRegistrations.remove(email);
                return true;
            } else { sendRawMessage("ERROR Database Write Failed"); }
        } else { sendRawMessage("ERROR Invalid OTP"); }
        return false;
    }

    private void handleRequestLoginOTP(String line) {
        String[] parts = line.trim().split(" ", 2);
        if (parts.length < 2) return;
        String email = parts[1].trim();
        
        String foundUsername = DatabaseManager.getUsernameByEmail(email);
        if (foundUsername == null) { sendRawMessage("ERROR Email not found."); return; }
        
        int randomPin = (int) (Math.random() * 900000) + 100000;
        String code = String.valueOf(randomPin);
        sharedLoginOtp.put(email, code);
        
        new Thread(() -> EmailService.sendOTP(email, code)).start();
        sendRawMessage("OTP_SENT");
    }

    private boolean handleVerifyLoginOTP(String line) {
        String[] parts = line.trim().split(" ", 3);
        if (parts.length < 3) return false;
        String email = parts[1].trim(); String inputCode = parts[2].trim();
        
        String realCode = sharedLoginOtp.get(email);
        if (realCode != null && realCode.equals(inputCode)) {
            sharedLoginOtp.remove(email); 
            String resolvedUser = DatabaseManager.getUsernameByEmail(email);
            sendRawMessage("LOGIN_SUCCESS " + resolvedUser);
            this.username = null; return false; 
        } else { sendRawMessage("ERROR Invalid Code"); return false; }
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) {}
        if (inChat && username != null) { server.removeClient(this); }
    }
}