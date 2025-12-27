package server;

import common.Protocol;
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username = null;

    private String tempUser = null;
    private String tempPass = null;
    private String tempEmail = null;
    private String expectedOTP = null;
    
    private boolean inChat = false; 

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() { return username; }

    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
            out.flush();
        }
    }

    private boolean handleLogin(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) return false;
        String userRaw = parts[1];
        String pass = parts[2];

        String officialName = DatabaseManager.checkLogin(userRaw, pass);
        if (officialName != null) {
            this.username = officialName;
            return true;
        } else {
            sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_FAIL);
            return false;
        }
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // --- PHASE 1: AUTHENTICATION LOOP ---
            boolean authenticated = false;
            while (!authenticated) {
                String line = in.readLine();
                if (line == null) return; 

                // 1. STANDARD LOGIN (This was missing!)
                if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.LOGIN)) {
                     if (handleLogin(line)) {
                         authenticated = true;
                     }
                }
                // 2. Check Login (Just verifies password, doesn't connect)
                else if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.CHECK_LOGIN)) {
                    handleCheckLogin(line);
                }
                // 3. Register
                else if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.REGISTER)) {
                    authenticated = handleRegister(line); 
                }
                // 4. Verify Register OTP
                else if (line.startsWith(Protocol.CLIENT_PREFIX + "VERIFY_OTP")) {
                    handleVerifyOTP(line);
                }
                // 5. Request Login OTP
                else if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.REQUEST_LOGIN_OTP)) {
                    handleRequestLoginOTP(line);
                }
                // 6. Verify Login OTP
                else if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.VERIFY_LOGIN_OTP)) {
                    authenticated = handleVerifyLoginOTP(line);
                }
                else {
                    sendMessage(Protocol.SERVER_PREFIX + "ERROR Please login first");
                }
            }

            // --- PHASE 2: JOINING CHAT ---
            if (username == null) return; // Safety check

            inChat = true; 
            
            sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS + " " + username);
            server.broadcast(Protocol.SERVER_PREFIX + "USER_JOINED " + username, this);
            server.broadcastUserList();

            // --- SEND HISTORY ---
            java.util.List<String> history = DatabaseManager.getChatHistory(username);
            for (String histMsg : history) {
                sendMessage(Protocol.SERVER_PREFIX + histMsg);
            }

            // --- PHASE 3: MAIN CHAT LOOP ---
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(Protocol.CLIENT_PREFIX)) {
                    handleClientCommand(line.substring(Protocol.CLIENT_PREFIX.length()).trim());
                } else {
                    DatabaseManager.saveMessage(this.username, "ALL", line); 
                    server.broadcast(Protocol.SERVER_PREFIX + "MSG " + username + " " + line, this);
                }
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + username);
        } finally {
            close();
        }
    }

    // --- NEW METHODS FOR EMAIL LOGIN ---

    private void handleRequestLoginOTP(String line) {
        String[] parts = line.split(" ", 2);
        if (parts.length < 2) return;
        String email = parts[1];
        
        // 1. Check if email exists in DB
        String foundUsername = DatabaseManager.getUsernameByEmail(email);
        
        if (foundUsername == null) {
            sendMessage(Protocol.SERVER_PREFIX + "ERROR Email not found.");
            return;
        }
        
        // 2. Generate OTP
        int randomPin = (int) (Math.random() * 900000) + 100000;
        this.expectedOTP = String.valueOf(randomPin);
        this.tempEmail = email; 
        this.tempUser = foundUsername; // Store the username we found in DB
        
        // 3. Send Email
        new Thread(() -> {
            EmailService.sendOTP(email, expectedOTP);
        }).start();
        
        sendMessage(Protocol.SERVER_PREFIX + "OTP_SENT");
    }

    private boolean handleVerifyLoginOTP(String line) {
        String[] parts = line.split(" ", 3); // VERIFY_LOGIN_OTP email code
        String code = parts[2];
        
        if (this.expectedOTP != null && this.expectedOTP.equals(code)) {
            // Success! 
            this.username = this.tempUser; // Use the username we found earlier
            return true; // Breaks the auth loop
        } else {
            sendMessage(Protocol.SERVER_PREFIX + "ERROR Invalid Code");
            return false;
        }
    }

    // --- EXISTING METHODS (Kept same) ---

    private void handleCheckLogin(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) return;
        String userRaw = parts[1];
        String pass = parts[2];

        // Only send success/fail, do NOT set authenticated=true yet (Client must switch scenes first)
        if (DatabaseManager.checkLogin(userRaw, pass) != null) {
            sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS);
        } else {
            sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_FAIL);
        }
    }

    private boolean handleRegister(String line) {
        String[] parts = line.split(" ", 4);
        if (parts.length < 4) return false;

        String user = parts[1];
        String pass = parts[2];
        String email = parts[3];

        if (DatabaseManager.checkLogin(user, "dummy") != null) {
            sendMessage(Protocol.SERVER_PREFIX + "ERROR Username already exists.");
            return false;
        }

        int randomPin = (int) (Math.random() * 900000) + 100000;
        this.expectedOTP = String.valueOf(randomPin);
        this.tempUser = user;
        this.tempPass = pass;
        this.tempEmail = email;

        new Thread(() -> EmailService.sendOTP(email, expectedOTP)).start();

        sendMessage(Protocol.SERVER_PREFIX + "OTP_REQ");
        return false;
    }

    private void handleVerifyOTP(String line) {
        String[] parts = line.split(" ", 2);
        String code = parts[1];

        if (this.expectedOTP != null && this.expectedOTP.equals(code)) {
            if (DatabaseManager.registerUser(tempUser, tempPass, tempEmail)) {
                sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS); 
                // We don't auto-login here because the client architecture expects 
                // to return to login screen or re-connect. 
                // But for seamlessness, if you want auto-login, set authenticated=true here.
            } else {
                sendMessage(Protocol.SERVER_PREFIX + "ERROR Database error.");
            }
        } else {
            sendMessage(Protocol.SERVER_PREFIX + "ERROR Invalid OTP.");
        }
    }

    private void handleClientCommand(String cmdLine) {
        if (cmdLine.startsWith("PM ")) {
             String[] parts = cmdLine.split(" ", 3);
             if (parts.length >= 3) {
                 String target = parts[1];
                 String msg = parts[2];
                 
                 // Save to DB
                 DatabaseManager.saveMessage(this.username, target, msg);
                 
                 // Try to send
                 boolean sent = server.sendPrivateMessage(this.username, target, msg);
                 
                 if (sent) {
                     sendMessage(Protocol.SERVER_PREFIX + "MSG " + "Me" + " -> " + target + ": " + msg);
                 } else {
                     sendMessage(Protocol.SERVER_PREFIX + "MSG " + "[System]" + " User '" + target + "' is offline. Message saved to history.");
                 }
             }
        }
        // TYPING logic removed to fix the error
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) {}
        if (inChat && username != null) {
            server.removeClient(this);
        }
    }
}