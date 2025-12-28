package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import common.Protocol;

public class ChatServer {
    private final int port;
    private final java.util.List<String> chatHistory = Collections.synchronizedList(new java.util.ArrayList<>());

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
        System.out.println("Server started.");
    }

    public void broadcast(String message, ClientHandler exclude) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch != exclude) ch.sendMessage(message);
            }
        }
    }

    public boolean sendPrivateMessage(String senderName, String targetName, String message) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.getUsername() != null && ch.getUsername().equalsIgnoreCase(targetName)) {
                    ch.sendMessage(Protocol.SERVER_PREFIX + "MSG " + senderName + " (Private): " + message);
                    return true;
                }
            }
        }
        return false;
    }

    public java.util.List<String> getHistory() {
        synchronized (chatHistory) {
            return new java.util.ArrayList<>(chatHistory);
        }
    }

    public void removeClient(ClientHandler ch) {
        clients.remove(ch);
        broadcast(Protocol.SERVER_PREFIX + "USER_LEFT " + ch.getUsername(), null);
        broadcastUserList();
    }

    public void broadcastUserList() {
        java.util.List<String> allUsers = DatabaseManager.getAllUsernames();
        
        java.util.Set<String> onlineUsersLower = new java.util.HashSet<>();
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.getUsername() != null) {
                    onlineUsersLower.add(ch.getUsername().toLowerCase());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(common.Protocol.SERVER_PREFIX).append("USERLIST");
        
        for (String user : allUsers) {
            sb.append(" ");
            sb.append(user);
            sb.append(":");
            
            boolean isOnline = onlineUsersLower.contains(user.toLowerCase());
            
            sb.append(isOnline ? "1" : "0");
        }

        broadcast(sb.toString(), null);
    }

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
}