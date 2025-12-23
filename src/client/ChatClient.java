package client;

import common.Protocol;
import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ChatClient {
    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;
    private final Consumer<String> messageHandler;

    public ChatClient(String host, int port, String username, Consumer<String> messageHandler) throws IOException {
        this.messageHandler = messageHandler;

        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // --- FIX 1: Send the correct Handshake ---
        // Was: out.println(username);
        // Now: C:CONNECT <username>
        out.println(Protocol.CLIENT_PREFIX + "CONNECT " + username);

        Thread readerThread = new Thread(this::listen);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void listen() {
        String line;
        try {
            while ((line = in.readLine()) != null) {
                messageHandler.accept(line);
            }
        } catch (IOException e) {
            messageHandler.accept("[SYSTEM] Disconnected from server.");
            close();
        }
    }

    /**
     * Handles user input. If it starts with '/', it is sent as a command.
     * Otherwise, it is sent as a standard chat message.
     */
    public void sendText(String text) {
        if (text.startsWith("/")) {
            // --- FIX 2: Translate UI commands (e.g. /pm) to Protocol commands (e.g. C:PM) ---
            String cmdBody = text.substring(1); // Remove the '/'
            String[] parts = cmdBody.split(" ", 2);
            String commandName = parts[0].toUpperCase(); // e.g., "pm" -> "PM"
            String args = parts.length > 1 ? " " + parts[1] : "";

            // Send: C:COMMAND <args>
            out.println(Protocol.CLIENT_PREFIX + commandName + args);
        } else {
            // Send raw text. The server treats non-prefixed messages as public chat.
            out.println(text);
        }
    }

    public void close() {
        try {
            // Send QUIT command via protocol
            out.println(Protocol.CLIENT_PREFIX + "QUIT");
            socket.close();
        } catch (IOException ignored) {}
    }
}
