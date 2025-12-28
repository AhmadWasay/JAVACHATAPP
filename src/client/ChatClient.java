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

    public ChatClient(String host, int port, String username, String password, Consumer<String> messageHandler) throws IOException {
        this.messageHandler = messageHandler;

        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out.println(Protocol.CLIENT_PREFIX + Protocol.LOGIN + " " + username + " " + password);
        
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

    public void sendText(String text) {
        if (text.startsWith("/")) {
            String cmdBody = text.substring(1); // Remove the '/'
            String[] parts = cmdBody.split(" ", 2);
            String commandName = parts[0].toUpperCase(); // e.g., "pm" -> "PM"
            String args = parts.length > 1 ? " " + parts[1] : "";

            out.println(Protocol.CLIENT_PREFIX + commandName + args);
        } else {
            out.println(text);
        }
    }

    public void close() {
        try {
            out.println(Protocol.CLIENT_PREFIX + "QUIT");
            socket.close();
        } catch (IOException ignored) {}
    }
}
