package client;

import common.Protocol;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCode;
import java.util.ArrayList;
import java.util.List;

public class ChatController {

    @FXML private VBox chatBox;
    @FXML private ScrollPane scrollPane;
    @FXML private TextField inputField;
    @FXML private ListView<String> usersList;
    @FXML private Button sendButton;
    @FXML private Label statusLabel; // The Header Label
    
    // Hidden fields
    @FXML private TextField nameField;
    @FXML private TextField hostField; 
    @FXML private TextField portField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;

    private ChatClient client;
    private String password;
    
    // --- NEW: Chat Management ---
    private static final String UNIVERSAL_CHAT = "Universal Chat";
    private String currentChatTarget = UNIVERSAL_CHAT; // Who are we talking to right now?
    
    // We store ALL messages here, then filter them based on which tab is open
    private final List<ChatMessage> allMessages = new ArrayList<>();
    
    // Simple helper class to store message data
    private static class ChatMessage {
        String sender;
        String content;
        String type; // "PUBLIC" or "PRIVATE"
        String target; // If private, who is the other person?
        boolean isMyMessage;

        public ChatMessage(String sender, String content, String type, String target, boolean isMyMessage) {
            this.sender = sender;
            this.content = content;
            this.type = type;
            this.target = target;
            this.isMyMessage = isMyMessage;
        }
    }
    // ----------------------------

    public void setAutoLogin(String username, String password) {
        this.nameField.setText(username);
        this.password = password;
        this.connect(); 
    }

    @FXML
    public void initialize() {
        // 1. Auto-scroll logic
        chatBox.heightProperty().addListener((obs, oldVal, newVal) -> scrollPane.setVvalue(1.0));

        // 2. Custom "Green Dot" Cell Factory for Sidebar
        usersList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    // Create the Dot
                    Circle dot = new Circle(4);
                    if (item.equals(UNIVERSAL_CHAT)) {
                        dot.setFill(Color.ORANGE); // Orange for Group
                    } else {
                        dot.setFill(Color.LIMEGREEN); // Green for Online Users
                    }
                    setGraphic(dot);
                    setStyle("-fx-padding: 10; -fx-font-size: 14px;");
                }
            }
        });

        // 3. Handle Clicking a User (Switch Chat View)
        usersList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                switchChat(newVal);
            }
        });

        // 4. Input handling
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendMessage();
            }
        });
        sendButton.setOnAction(e -> sendMessage());
    }

    private void switchChat(String target) {
        this.currentChatTarget = target;
        
        // Update Header
        if (target.equals(UNIVERSAL_CHAT)) {
            statusLabel.setText("Universal Chat");
        } else {
            statusLabel.setText("Chat with " + target);
        }

        // Refresh the bubbles to show only messages for this target
        renderCurrentChat();
    }

    private void renderCurrentChat() {
        chatBox.getChildren().clear();
        String myName = nameField.getText().trim();

        for (ChatMessage msg : allMessages) {
            boolean showIt = false;

            if (currentChatTarget.equals(UNIVERSAL_CHAT)) {
                // Show only PUBLIC messages
                if (msg.type.equals("PUBLIC")) showIt = true;
            } else {
                // Show PRIVATE messages involving this specific user
                if (msg.type.equals("PRIVATE")) {
                    // It's relevant if I sent it TO them, or they sent it TO me
                    if (msg.target.equals(currentChatTarget) || msg.sender.equals(currentChatTarget)) {
                        showIt = true;
                    }
                }
            }

            if (showIt) {
                // Add bubble
                addBubbleToUI(msg);
            }
        }
    }

    private void connect() {
        if (client != null) return; 
        String host = "localhost";
        int port = 5555;
        String name = nameField.getText().trim();
        
        // Add "Universal Chat" to list immediately
        usersList.getItems().add(UNIVERSAL_CHAT);
        usersList.getSelectionModel().select(0); // Default select

        addSystemMessage("Connecting...");

        try {
            client = new ChatClient(host, port, name, password, this::onRawMessage);
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

        // Logic: Are we in Universal or Private?
        if (currentChatTarget.equals(UNIVERSAL_CHAT)) {
            // Send Public
            client.sendText(text);
            // We don't add bubble yet, we wait for server echo to confirm
        } else {
            // Send Private: /pm User Msg
            client.sendText("/pm " + currentChatTarget + " " + text);
            // Note: Server echoes "Me -> Target: Msg", so we wait for that to render
        }
        
        inputField.clear();
    }

    private void addBubbleToUI(ChatMessage msg) {
        HBox container = new HBox();
        Label bubble = new Label(msg.content);
        bubble.setWrapText(true);
        bubble.setMaxWidth(350);

        if (msg.isMyMessage) {
            container.setAlignment(Pos.CENTER_RIGHT);
            bubble.setStyle("-fx-background-color: #dcf8c6; -fx-background-radius: 10; -fx-padding: 10; -fx-font-size: 14px;");
            // If private, maybe add a small lock icon or text?
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            bubble.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-padding: 10; -fx-font-size: 14px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 1);");
        }
        
        // Add sender name on top if it's not me
        VBox bubbleContent = new VBox(2);
        if (!msg.isMyMessage) {
            Label nameLbl = new Label(msg.sender);
            nameLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: gray; -fx-font-weight: bold;");
            bubbleContent.getChildren().add(nameLbl);
        }
        bubbleContent.getChildren().add(bubble);
        container.getChildren().add(bubbleContent);

        chatBox.getChildren().add(container);
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

    private void onRawMessage(String msg) {
        Platform.runLater(() -> {
            String listPrefix = Protocol.SERVER_PREFIX + "USERLIST";
            
            // 1. Handle User List Update
            if (msg.startsWith(listPrefix)) {
                String rawNames = msg.substring(listPrefix.length()).trim();
                
                // Remember who was selected
                String selected = usersList.getSelectionModel().getSelectedItem();
                
                usersList.getItems().clear();
                usersList.getItems().add(UNIVERSAL_CHAT); // Always top
                
                if (!rawNames.isEmpty()) {
                    String[] names = rawNames.split(" ");
                    for (String n : names) {
                        // Don't add ourselves to the private list? (Optional)
                        if (!n.equals(nameField.getText())) {
                            usersList.getItems().add(n);
                        }
                    }
                }
                
                // Restore selection if they were still online
                if (selected != null && usersList.getItems().contains(selected)) {
                    usersList.getSelectionModel().select(selected);
                } else {
                    usersList.getSelectionModel().select(0);
                }
                return;
            }

            // 2. Handle Chat Messages
            if (msg.startsWith(Protocol.SERVER_PREFIX + "MSG")) {
                String cleanMsg = msg.substring((Protocol.SERVER_PREFIX + "MSG").length()).trim();
                // We need to parse this message carefully
                parseAndStoreMessage(cleanMsg);
                return;
            }

            // 3. System Messages
            if (msg.startsWith("[SYSTEM]")) {
                addSystemMessage(msg.substring(8).trim());
            } else if (msg.startsWith(Protocol.SERVER_PREFIX)) {
                String clean = msg.substring(Protocol.SERVER_PREFIX.length());
                if (clean.startsWith("USER_JOINED")) {
                    addSystemMessage(clean.split(" ")[1] + " joined.");
                } else if (clean.startsWith("USER_LEFT")) {
                    addSystemMessage(clean.split(" ")[1] + " left.");
                } else if (!clean.startsWith("LOGIN_SUCCESS") && !clean.startsWith("CONNECTED")) {
                     addSystemMessage(clean);
                }
            }
        });
    }

    private void parseAndStoreMessage(String raw) {
        // Formats we expect:
        // 1. Public: "Sender Content..."
        // 2. Private (Recv): "Sender (Private): Content..."
        // 3. Private (Sent): "Me -> Target: Content..."
        
        String sender = "";
        String content = "";
        String type = "PUBLIC";
        String target = "";
        boolean isMyMessage = false;

        if (raw.contains("->")) {
            // Case 3: I sent a private message
            // "Me -> Abdullah: Hello"
            String[] parts = raw.split("->");
            String[] rightPart = parts[1].split(":", 2);
            
            sender = "Me";
            isMyMessage = true;
            target = rightPart[0].trim(); // "Abdullah"
            content = rightPart[1].trim(); // "Hello"
            type = "PRIVATE";
            
        } else if (raw.contains("(Private):")) {
            // Case 2: I received a private message
            // "Abdullah (Private): Hello"
            String[] parts = raw.split("\\(Private\\):", 2);
            sender = parts[0].trim();
            content = parts[1].trim();
            target = sender; // The chat target is the person who sent it
            type = "PRIVATE";
            isMyMessage = false;
            
        } else {
            // Case 1: Public message
            // "Abdullah Hello world" (Wait, our server sends "MSG Abdullah Content")
            String[] parts = raw.split(" ", 2);
            sender = parts[0];
            content = parts[1];
            
            if (sender.equals(nameField.getText()) || sender.equals("Me")) {
                isMyMessage = true;
            }
            type = "PUBLIC";
            target = UNIVERSAL_CHAT;
        }

        // Store it
        ChatMessage newMsg = new ChatMessage(sender, content, type, target, isMyMessage);
        allMessages.add(newMsg);

        // If the message belongs to the CURRENT view, render it immediately
        boolean renderNow = false;
        if (currentChatTarget.equals(UNIVERSAL_CHAT) && type.equals("PUBLIC")) renderNow = true;
        if (type.equals("PRIVATE") && (target.equals(currentChatTarget) || sender.equals(currentChatTarget))) renderNow = true;

        if (renderNow) {
            addBubbleToUI(newMsg);
        } else {
            // Optional: You could show a "New Message" badge on the sidebar here!
        }
    }
}