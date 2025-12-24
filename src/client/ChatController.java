package client;

import common.Protocol;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import java.util.Arrays;

public class ChatController {

    @FXML private TextArea messageArea;
    @FXML private TextField inputField;
    @FXML private ListView<String> usersList;
    @FXML private Button sendButton;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField nameField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Label statusLabel;

    private ChatClient client;

    private String password; // Add this field

    public void setAutoLogin(String username, String password) {
        this.nameField.setText(username);
        this.password = password;
        this.nameField.setEditable(false);
        try {
            this.connect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @FXML
    public void initialize() {
        messageArea.setEditable(false);
        messageArea.setWrapText(true);

        // Feature: Click-to-PM
        // When a user clicks a name in the list, auto-fill the input field
        usersList.setOnMouseClicked(event -> {
            String selectedUser = usersList.getSelectionModel().getSelectedItem();
            if (selectedUser != null) {
                inputField.setText("/pm " + selectedUser + " ");
                inputField.requestFocus();
                inputField.positionCaret(inputField.getText().length());
            }
        });

        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendMessage();
            }
        });
        
        // ... (keep the rest of your button setup code here)
        sendButton.setOnAction(e -> sendMessage());
        connectButton.setOnAction(e -> {
            try {
                connect();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        });
        disconnectButton.setOnAction(e -> disconnect());
        disconnectButton.setDisable(true);
    }

    private void connect() throws IOException {
        String host = hostField.getText().isEmpty() ? "localhost" : hostField.getText().trim();
        int port = portField.getText().isEmpty() ? 5555 : Integer.parseInt(portField.getText().trim());
        String name = nameField.getText().isEmpty() ? "User" : nameField.getText().trim();
        client = new ChatClient(host, port, name, password, this::onRawMessage);
        appendSystem("Connecting to " + host + ":" + port + "...");

        try {
            // Pass 'this::onRawMessage' to handle incoming data
            client = new ChatClient(host, port, name, password, this::onRawMessage);
            connectButton.setDisable(true);
            disconnectButton.setDisable(false);
            statusLabel.setText("Connected as " + name);
        } catch (Exception e) {
            appendSystem("Failed to connect: " + e.getMessage());
        }
    }

    private void disconnect() {
        if (client != null) client.close();
        client = null;
        connectButton.setDisable(false);
        disconnectButton.setDisable(true);
        usersList.getItems().clear();
        statusLabel.setText("Disconnected");
        appendSystem("Disconnected.");
    }

    // Call this from LoginController to pass data
    public void setAutoLogin(String username) {
        this.nameField.setText(username);
        // We can disable editing since they are already logged in
        this.nameField.setEditable(false); 
        try {
            this.connect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } // Auto-click the connect button
    }

    private void sendMessage() {
        if (client == null) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        client.sendText(text);
        appendMy(text);
        inputField.clear();
    }

    // --- LOGIC TO UPDATE SIDEBAR ---
    private void onRawMessage(String msg) {
        Platform.runLater(() -> {
            // 1. Check for the correct protocol header: "S:USERLIST"
            String listPrefix = Protocol.SERVER_PREFIX + "USERLIST";
            
            if (msg.startsWith(listPrefix)) {
                // Remove the header to get the names: " Alice Bob Charlie"
                String rawNames = msg.substring(listPrefix.length()).trim();
                
                usersList.getItems().clear();
                
                if (!rawNames.isEmpty()) {
                    // 2. The Server uses SPACE as a separator, not commas
                    String[] names = rawNames.split(" ");
                    usersList.getItems().addAll(Arrays.asList(names));
                }
                return; // Stop here, don't print the list logic to the chat area
            }

            // Normal chat messages
            if (msg.startsWith("[SYSTEM]")) {
                appendSystem(msg.substring(8).trim());
            } else if (msg.startsWith(Protocol.SERVER_PREFIX)) {
                // Remove the "S:" prefix for cleaner chat
                String cleanMsg = msg.substring(Protocol.SERVER_PREFIX.length());
                
                // Optional: Check for specific server event types like "USER_JOINED"
                if (cleanMsg.startsWith("USER_JOINED")) {
                    String user = cleanMsg.split(" ")[1];
                    appendSystem(user + " has joined the chat.");
                } else if (cleanMsg.startsWith("USER_LEFT")) {
                    String user = cleanMsg.split(" ")[1];
                    appendSystem(user + " has left the chat.");
                } else if (cleanMsg.startsWith("MSG")) {
                    // Format: MSG <Username> <Message text...>
                    // We parse this to make it look nice
                    String[] parts = cleanMsg.split(" ", 3);
                    if (parts.length >= 3) {
                        appendRemote(parts[1] + ": " + parts[2]);
                    } else {
                        appendRemote(cleanMsg);
                    }
                } else {
                    // Fallback for other server messages
                    appendRemote(cleanMsg);
                }
            } else {
                appendRemote(msg);
            }
        });
    }

    private String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void appendSystem(String s) { 
        messageArea.appendText("[" + getCurrentTime() + "] [SYSTEM] " + s + "\n"); 
    }
    
    private void appendMy(String s) { 
        messageArea.appendText("[" + getCurrentTime() + "] [ME] " + s + "\n"); 
    }
    
    private void appendRemote(String s) { 
        messageArea.appendText("[" + getCurrentTime() + "] " + s + "\n"); 
    }
}