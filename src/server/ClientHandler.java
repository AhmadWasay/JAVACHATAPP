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
    
    // --- FIX 1: Define this variable ---
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

    private void handleCheckLogin(String line) {
        String[] parts = line.split(" ", 3); // C:CHECK_LOGIN user pass
        if (parts.length < 3) return;
        
        String user = parts[1];
        String pass = parts[2];

        if (DatabaseManager.checkLogin(user, pass)) {
            // Success: Tell client it's okay, but DON'T join the chat
            sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS);
        } else {
            sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_FAIL);
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

                // 1. Full Login (For Chat Controller)
                if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.LOGIN)) {
                    authenticated = handleLogin(line);
                } 
                // 2. Register (For Chat Controller)
                else if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.REGISTER)) {
                    authenticated = handleRegister(line);
                } 
                // 3. NEW: Check Login (For Login Controller - Silent)
                else if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.CHECK_LOGIN)) {
                    handleCheckLogin(line);
                    // Note: We do NOT set authenticated=true, so the loop continues 
                    // or the client disconnects. Perfect for just checking.
                } 
                else {
                    sendMessage(Protocol.SERVER_PREFIX + "ERROR Please login first");
                }
            }
            // --- FIX 2: Removed Duplicate "PHASE 2" Block ---
            
            // --- PHASE 2: JOINING CHAT ---
            inChat = true; // Mark them as officially joined
            
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

    private boolean handleLogin(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) return false;
        String user = parts[1];
        String pass = parts[2];

        if (DatabaseManager.checkLogin(user, pass)) {
            this.username = user;
            return true;
        } else {
            sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_FAIL);
            return false;
        }
    }

    private boolean handleRegister(String line) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) return false;
        String user = parts[1];
        String pass = parts[2];

        if (DatabaseManager.registerUser(user, pass)) {
            this.username = user;
            return true;
        } else {
            sendMessage(Protocol.SERVER_PREFIX + "ERROR Username taken");
            return false;
        }
    }

    private void handleClientCommand(String cmdLine) {
        if (cmdLine.startsWith("PM ")) {
             String[] parts = cmdLine.split(" ", 3);
             if (parts.length >= 3) {
                 String target = parts[1];
                 String msg = parts[2];

                 DatabaseManager.saveMessage(this.username, target, msg);
                 boolean sent = server.sendPrivateMessage(this.username, target, msg);
                 
                 if (sent) {
                     sendMessage(Protocol.SERVER_PREFIX + "MSG " + "Me" + " -> " + target + ": " + msg);
                 } else {
                     sendMessage(Protocol.SERVER_PREFIX + "MSG " + "[System]" + " User '" + target + "' is offline. Message saved to history.");
                 }
             }
        }
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) {}
        
        // --- FIX 3: Check inChat to prevent "USER_LEFT null" spam ---
        if (inChat && username != null) {
            server.removeClient(this);
        }
    }
}