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

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // --- PHASE 1: AUTHENTICATION LOOP ---
            // The user cannot chat until they pass this loop
            boolean authenticated = false;
            while (!authenticated) {
                String line = in.readLine();
                if (line == null) return; // Client disconnected

                if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.LOGIN)) {
                    // Format: C:LOGIN <user> <pass>
                    authenticated = handleLogin(line);
                } else if (line.startsWith(Protocol.CLIENT_PREFIX + Protocol.REGISTER)) {
                    // Format: C:REGISTER <user> <pass>
                    authenticated = handleRegister(line);
                } else {
                    sendMessage(Protocol.SERVER_PREFIX + "ERROR Please login first");
                }
            }

            // --- PHASE 2: MAIN CHAT LOOP ---
            sendMessage(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS + " " + username);
            server.broadcast(Protocol.SERVER_PREFIX + "USER_JOINED " + username, this);
            server.broadcastUserList();

            // Load offline/history messages (Optional - we can add this later)
            // loadChatHistory(); 

            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(Protocol.CLIENT_PREFIX)) {
                    handleClientCommand(line.substring(Protocol.CLIENT_PREFIX.length()).trim());
                } else {
                    // Chat message: Save to DB then Broadcast
                    // The server now knows WHO sent it (this.username)
                    // For public chat, we can set receiver as "ALL"
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
        String[] parts = line.split(" ", 3); // C:LOGIN user pass
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
            // Auto-login after register? Or ask them to login? 
            // Let's just log them in for convenience.
            return true;
        } else {
            sendMessage(Protocol.SERVER_PREFIX + "ERROR Username taken");
            return false;
        }
    }

    private void handleClientCommand(String cmdLine) {
        // ... (Keep your existing PM / RENAME / QUIT logic here) ...
        // Important: When handling PM, save to DB!
        if (cmdLine.startsWith("PM ")) {
             String[] parts = cmdLine.split(" ", 3);
             if (parts.length >= 3) {
                 String target = parts[1];
                 String msg = parts[2];
                 // Save private message to DB
                 DatabaseManager.saveMessage(this.username, target, msg);
                 
                 // ... rest of your PM logic ...
             }
        }
        // ...
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) {}
        server.removeClient(this);
    }
}