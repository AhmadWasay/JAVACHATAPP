package server;

import common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Handles one client connection
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username = "Anonymous";

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() {
        return username;
    }

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

            // First line expected: CONNECT desiredName
            String first = in.readLine();
            if (first != null && first.startsWith(Protocol.CLIENT_PREFIX + "CONNECT")) {
                String[] parts = first.split(" ", 3);
                String desired = parts.length >= 2 ? parts[1].trim() : "User";
                username = server.resolveUniqueName(desired);
                sendMessage(Protocol.SERVER_PREFIX + username + " CONNECTED!");
                server.broadcast(Protocol.SERVER_PREFIX + "USER_JOINED " + username, this);
                server.broadcastUserList();
                // ... inside run(), after successful connection ...

                // --- NEW: Send History ---
                for (String oldMsg : server.getHistory()) {
                    sendMessage(oldMsg);
                }
                // -------------------------
            } else {
                sendMessage(Protocol.SERVER_PREFIX + "ERROR Missing CONNECT");
                close();
                return;
            }

            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(Protocol.CLIENT_PREFIX)) {
                    // Commands prefixed by CLIENT:
                    handleClientCommand(line.substring(Protocol.CLIENT_PREFIX.length()).trim());
                } else {
                    // Plain chat message -> broadcast
                    server.broadcast(Protocol.SERVER_PREFIX + "MSG " + username + " " + line, this);
                }
            }
        } catch (IOException e) {
            // client disconnected or error
        } finally {
            close();
        }
    }

    private void handleClientCommand(String cmdLine) {
        if (cmdLine.startsWith("PM ")) {
            // PM <target> <message>
            String[] parts = cmdLine.split(" ", 3);
            if (parts.length >= 3) {
                String target = parts[1];
                String msg = parts[2];
                boolean found = false;
                synchronized (server) {
                    for (ClientHandler ch : server.clients) {
                        if (ch.getUsername().equalsIgnoreCase(target)) {
                            ch.sendMessage(Protocol.SERVER_PREFIX + "PM " + username + " " + msg);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    sendMessage(Protocol.SERVER_PREFIX + "ERROR UserNotFound " + target);
                }
            } else {
                sendMessage(Protocol.SERVER_PREFIX + "ERROR BadPM");
            }
        } else if (cmdLine.equals("USERS")) {
            server.broadcastUserList();
        } else if (cmdLine.startsWith("RENAME ")) {
            String[] p = cmdLine.split(" ", 2);
            if (p.length == 2) {
                String newName = server.resolveUniqueName(p[1].trim());
                String old = this.username;
                this.username = newName;
                sendMessage(Protocol.SERVER_PREFIX + "RENAMED " + old + " " + newName);
                server.broadcast(Protocol.SERVER_PREFIX + "USER_RENAMED " + old + " " + newName, this);
                server.broadcastUserList();
            }
        } else if (cmdLine.equals("QUIT")) {
            close();
        } else {
            sendMessage(Protocol.SERVER_PREFIX + "ERROR UnknownCmd");
        }
    }

    private void close() {
        try {
            socket.close();
        } catch (IOException ignored) {}
        server.removeClient(this);
    }
}