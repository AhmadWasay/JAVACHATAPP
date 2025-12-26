package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import common.Protocol;

/**
 * ChatServer: accepts connections and keeps set of ClientHandler
 */
public class ChatServer {
    private final int port;
    private final java.util.List<String> chatHistory = Collections.synchronizedList(new java.util.ArrayList<>());
    // Keep set of handlers for broadcasting
    final Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("Starting ChatServer on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket sock = serverSocket.accept();
                ClientHandler handler = new ClientHandler(sock, this);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void broadcast(String message, ClientHandler exclude) {
        // Only save actual chat messages (MSG) or System messages, ignore Connect/Disconnect technical signals if you want
        if (message.startsWith(Protocol.SERVER_PREFIX + "MSG")) {
            synchronized (chatHistory) {
                chatHistory.add(message);
                if (chatHistory.size() > 10) { // Keep only last 10
                    chatHistory.remove(0);
                }
            }
        }

        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch != exclude) ch.sendMessage(message);
            }
        }
    }
    
    // Add a getter for the handler to use
    public java.util.List<String> getHistory() {
        synchronized (chatHistory) {
            return new java.util.ArrayList<>(chatHistory); // Return a copy
        }
    }

    public void removeClient(ClientHandler ch) {
        clients.remove(ch);
        broadcast(Protocol.SERVER_PREFIX + "USER_LEFT " + ch.getUsername(), null);
        broadcastUserList();
    }

    public void broadcastUserList() {
        // 1. Get ALL users from Database
        java.util.List<String> allUsers = DatabaseManager.getAllUsernames();
        
        // 2. Get ONLINE users (Convert to Lowercase for matching)
        java.util.Set<String> onlineUsersLower = new java.util.HashSet<>();
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.getUsername() != null) {
                    // Store as lowercase: "abdullah"
                    onlineUsersLower.add(ch.getUsername().toLowerCase());
                }
            }
        }

        // 3. Build the list string
        StringBuilder sb = new StringBuilder();
        sb.append(common.Protocol.SERVER_PREFIX).append("USERLIST");
        
        for (String user : allUsers) {
            sb.append(" ");
            sb.append(user); // Send the pretty name (e.g. "Abdullah")
            sb.append(":");
            
            // 4. Check if the lowercase version exists in our online set
            boolean isOnline = onlineUsersLower.contains(user.toLowerCase());
            
            sb.append(isOnline ? "1" : "0");
        }

        // 5. Broadcast to everyone
        broadcast(sb.toString(), null);
    }

    // Add this method to ChatServer.java
    public boolean sendPrivateMessage(String senderName, String targetName, String message) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                // Check if this is the user we are looking for
                if (ch.getUsername() != null && ch.getUsername().equalsIgnoreCase(targetName)) {
                    // Send the message with a "(Private)" tag so they know it's a whisper
                    ch.sendMessage(Protocol.SERVER_PREFIX + "MSG " + senderName + " (Private): " + message);
                    return true; // Found and sent
                }
            }
        }
        return false; // User not online
    }

    // Check username uniqueness and return a resolved unique name
    public String resolveUniqueName(String desired) {
        synchronized (clients) {
            String name = desired;
            int suffix = 1;
            boolean collides;
            do {
                collides = false;
                for (ClientHandler ch : clients) {
                    if (ch.getUsername() != null && ch.getUsername().equalsIgnoreCase(name)) {
                        collides = true;
                        break;
                    }
                }
                if (collides) {
                    name = desired + suffix;
                    suffix++;
                }
            } while (collides);
            return name;
        }
    }

    public static void main(String[] args) {
        int port = 5555;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        new ChatServer(port).start();
    }
}