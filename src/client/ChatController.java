package client;

import common.Protocol;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.input.KeyCode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class ChatController {

    // --- CHANGED: UI Components for Bubbles ---
    @FXML private VBox chatBox;         // The container for bubbles
    @FXML private ScrollPane scrollPane; // The scroller
    // ------------------------------------------

    @FXML private TextField inputField;
    @FXML private ListView<String> usersList;
    @FXML private Button sendButton;
    @FXML private TextField nameField;
    @FXML private Label statusLabel;
    
    @FXML private TextField hostField; 
    @FXML private TextField portField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;

    private ChatClient client;
    private String password;

    public void setAutoLogin(String username, String password) {
        this.nameField.setText(username);
        this.password = password;
        this.connect(); 
    }

    @FXML
    public void initialize() {
        // Auto-scroll to bottom when new messages arrive
        chatBox.heightProperty().addListener((observable, oldValue, newValue) -> {
            scrollPane.setVvalue(1.0); 
        });

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

        sendButton.setOnAction(e -> sendMessage());
    }

    private void connect() {
        if (client != null) return; 

        String host = "localhost";
        int port = 5555;
        String name = nameField.getText().trim();

        // Add a system label instead of text log
        addSystemMessage("Connecting to " + host + ":" + port + "...");

        try {
            client = new ChatClient(host, port, name, password, this::onRawMessage);
            statusLabel.setText("Connected as " + name);
        } catch (Exception e) {
            addSystemMessage("Failed to connect: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (client != null) client.close();
        client = null;
        Platform.runLater(() -> {
            usersList.getItems().clear();
            statusLabel.setText("Disconnected");
            addSystemMessage("Disconnected.");
        });
    }

    private void sendMessage() {
        if (client == null) return;
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        client.sendText(text);
        addMessageBubble(text, true); // True = My Message
        inputField.clear();
    }

    // --- THE BUBBLE FACTORY ---
    private void addMessageBubble(String text, boolean isMyMessage) {
        Platform.runLater(() -> {
            HBox container = new HBox();
            
            // 1. Create the Bubble
            Label bubble = new Label(text);
            bubble.setWrapText(true);
            bubble.setMaxWidth(300); // Prevent extremely wide bubbles
            
            // 2. Style based on Sender
            if (isMyMessage) {
                container.setAlignment(Pos.CENTER_RIGHT); // Align Right
                bubble.setStyle("-fx-background-color: #dcf8c6; -fx-background-radius: 10; -fx-padding: 10; -fx-font-size: 14px;");
            } else {
                container.setAlignment(Pos.CENTER_LEFT); // Align Left
                bubble.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-padding: 10; -fx-font-size: 14px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 1);");
            }
            
            // 3. Add Timestamp (Tiny text below or beside)
            // For simplicity, we just put the label in the box for now. 
            // To make it pro, we could use a VBox inside the bubble.

            container.getChildren().add(bubble);
            chatBox.getChildren().add(container);
        });
    }

    private void addSystemMessage(String text) {
        Platform.runLater(() -> {
            HBox container = new HBox();
            container.setAlignment(Pos.CENTER);
            Label lbl = new Label(text);
            lbl.setStyle("-fx-text-fill: gray; -fx-font-size: 11px; -fx-padding: 5; -fx-background-color: #e0e0e0; -fx-background-radius: 10;");
            container.getChildren().add(lbl);
            chatBox.getChildren().add(container);
        });
    }
    // ---------------------------

    private void onRawMessage(String msg) {
        Platform.runLater(() -> {
            String listPrefix = Protocol.SERVER_PREFIX + "USERLIST";
            if (msg.startsWith(listPrefix)) {
                String rawNames = msg.substring(listPrefix.length()).trim();
                usersList.getItems().clear();
                if (!rawNames.isEmpty()) {
                    usersList.getItems().addAll(Arrays.asList(rawNames.split(" ")));
                }
                return;
            }

            if (msg.startsWith("[SYSTEM]")) {
                addSystemMessage(msg.substring(8).trim());
            } else if (msg.startsWith(Protocol.SERVER_PREFIX)) {
                String cleanMsg = msg.substring(Protocol.SERVER_PREFIX.length());
                
                if (cleanMsg.startsWith("LOGIN_SUCCESS") || cleanMsg.startsWith("CONNECTED")) {
                    // ignore
                } else if (cleanMsg.startsWith("LOGIN_FAIL")) {
                    addSystemMessage("Login Failed: Incorrect password or username taken.");
                } else if (cleanMsg.startsWith("USER_JOINED")) {
                    addSystemMessage(cleanMsg.split(" ")[1] + " joined.");
                } else if (cleanMsg.startsWith("USER_LEFT")) {
                    addSystemMessage(cleanMsg.split(" ")[1] + " left.");
                } else if (cleanMsg.startsWith("MSG")) {
                    // Format: MSG Sender Content  OR  MSG Sender (Private): Content
                    String[] parts = cleanMsg.split(" ", 3);
                    if (parts.length >= 3) {
                        String sender = parts[1];
                        String content = parts[2];
                        
                        // If I sent it (e.g. from history loading), show as MY bubble
                        if (sender.equals("Me") || sender.equals(nameField.getText())) {
                             // Handle "Me -> UserB: Hello" format from history
                             if (content.startsWith("->")) {
                                 // content looks like "-> Bob: Hello"
                                 String realContent = content.substring(content.indexOf(":") + 1).trim();
                                 addMessageBubble(realContent, true);
                             } else {
                                 addMessageBubble(content, true);
                             }
                        } else {
                             // Remote message
                             // If it's private, maybe make it yellow?
                             if (content.startsWith("(Private):")) {
                                 content = "🔒 " + content; // Add lock icon
                             }
                             // Show Name + Message
                             addMessageBubble(sender + ":\n" + content, false);
                        }
                    }
                } else {
                    addSystemMessage(cleanMsg);
                }
            } else {
                addSystemMessage(msg);
            }
        });
    }
}