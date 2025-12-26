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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatController {

    @FXML private VBox chatBox;
    @FXML private ScrollPane scrollPane;
    @FXML private TextField inputField;
    @FXML private ListView<String> usersList;
    @FXML private Button sendButton;
    @FXML private Label statusLabel;
    
    // Hidden fields
    @FXML private TextField nameField;
    @FXML private TextField hostField; 
    @FXML private TextField portField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;

    private ChatClient client;
    private String password;
    
    private static final String UNIVERSAL_CHAT = "Universal Chat";
    private String currentChatTarget = UNIVERSAL_CHAT;
    
    // Store messages
    private final List<ChatMessage> allMessages = new ArrayList<>();
    
    // --- NEW: Map to store online status (true = online, false = offline) ---
    private final Map<String, Boolean> userStatusMap = new HashMap<>();

    private static class ChatMessage {
        String sender; String content; String type; String target; boolean isMyMessage;
        public ChatMessage(String sender, String content, String type, String target, boolean isMyMessage) {
            this.sender = sender; this.content = content; this.type = type; this.target = target; this.isMyMessage = isMyMessage;
        }
    }

    public void setAutoLogin(String username, String password) {
        this.nameField.setText(username);
        this.password = password;
        this.connect(); 
    }

    @FXML
    public void initialize() {
        chatBox.heightProperty().addListener((obs, oldVal, newVal) -> scrollPane.setVvalue(1.0));

        // --- UPDATED CELL FACTORY FOR BLUE / GRAY DOTS ---
        usersList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    Circle dot = new Circle(4);
                    
                    if (item.equals(UNIVERSAL_CHAT)) {
                        dot.setFill(Color.ORANGE); // Group Chat
                    } else {
                        // Check the map for status
                        boolean isOnline = userStatusMap.getOrDefault(item, false);
                        if (isOnline) {
                            dot.setFill(Color.DODGERBLUE); // BLUE for Online
                        } else {
                            dot.setFill(Color.LIGHTGRAY); // GRAY for Offline
                        }
                    }
                    setGraphic(dot);
                    setStyle("-fx-padding: 10; -fx-font-size: 14px;");
                }
            }
        });

        usersList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) switchChat(newVal);
        });

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
        if (target.equals(UNIVERSAL_CHAT)) {
            statusLabel.setText("Universal Chat");
        } else {
            statusLabel.setText("Chat with " + target);
        }
        renderCurrentChat();
    }

    private void renderCurrentChat() {
        chatBox.getChildren().clear();
        for (ChatMessage msg : allMessages) {
            boolean showIt = false;
            if (currentChatTarget.equals(UNIVERSAL_CHAT)) {
                if (msg.type.equals("PUBLIC")) showIt = true;
            } else {
                if (msg.type.equals("PRIVATE")) {
                    if (msg.target.equals(currentChatTarget) || msg.sender.equals(currentChatTarget)) {
                        showIt = true;
                    }
                }
            }
            if (showIt) addBubbleToUI(msg);
        }
    }

    private void connect() {
        if (client != null) return; 
        String host = "localhost";
        int port = 5555;
        String name = nameField.getText().trim();
        
        usersList.getItems().add(UNIVERSAL_CHAT);
        usersList.getSelectionModel().select(0);

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

        if (currentChatTarget.equals(UNIVERSAL_CHAT)) {
            client.sendText(text);
        } else {
            client.sendText("/pm " + currentChatTarget + " " + text);
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
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            bubble.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 10; -fx-padding: 10; -fx-font-size: 14px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 1);");
        }
        
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
            
            // --- UPDATED PARSING LOGIC ---
            if (msg.startsWith(listPrefix)) {
                String rawData = msg.substring(listPrefix.length()).trim();
                
                String selected = usersList.getSelectionModel().getSelectedItem();
                
                usersList.getItems().clear();
                usersList.getItems().add(UNIVERSAL_CHAT);
                userStatusMap.clear(); // Clear old statuses

                if (!rawData.isEmpty()) {
                    // Format: "UserA:1 UserB:0 UserC:1"
                    String[] entries = rawData.split(" ");
                    for (String entry : entries) {
                        if (entry.contains(":")) {
                            String[] parts = entry.split(":");
                            String username = parts[0];
                            boolean isOnline = parts[1].equals("1");
                            
                            // --- FIX: USE equalsIgnoreCase TO PREVENT SELF-CHAT ---
                            String myName = nameField.getText().trim();
                            if (!username.equalsIgnoreCase(myName)) {
                                usersList.getItems().add(username);
                                userStatusMap.put(username, isOnline);
                            }
                        }
                    }
                }
                
                if (selected != null && usersList.getItems().contains(selected)) {
                    usersList.getSelectionModel().select(selected);
                } else {
                    usersList.getSelectionModel().select(0);
                }
                
                // Force list refresh to apply colors
                usersList.refresh();
                return;
            }

            if (msg.startsWith(Protocol.SERVER_PREFIX + "MSG")) {
                parseAndStoreMessage(msg.substring((Protocol.SERVER_PREFIX + "MSG").length()).trim());
                return;
            }

            if (msg.startsWith("[SYSTEM]")) {
                addSystemMessage(msg.substring(8).trim());
            } else if (msg.startsWith(Protocol.SERVER_PREFIX)) {
                String clean = msg.substring(Protocol.SERVER_PREFIX.length());
                if (clean.startsWith("USER_JOINED")) {
                    addSystemMessage(clean.split(" ")[1] + " came online.");
                } else if (clean.startsWith("USER_LEFT")) {
                    addSystemMessage(clean.split(" ")[1] + " went offline.");
                } else if (!clean.startsWith("LOGIN_SUCCESS") && !clean.startsWith("CONNECTED")) {
                     addSystemMessage(clean);
                }
            }
        });
    }

    private void parseAndStoreMessage(String raw) {
        String sender = "", content = "", type = "PUBLIC", target = "";
        boolean isMyMessage = false;

        if (raw.contains("->")) {
            String[] parts = raw.split("->");
            String[] rightPart = parts[1].split(":", 2);
            sender = "Me"; isMyMessage = true; target = rightPart[0].trim(); content = rightPart[1].trim(); type = "PRIVATE";
        } else if (raw.contains("(Private):")) {
            String[] parts = raw.split("\\(Private\\):", 2);
            sender = parts[0].trim(); content = parts[1].trim(); target = sender; type = "PRIVATE"; isMyMessage = false;
        } else {
            String[] parts = raw.split(" ", 2);
            sender = parts[0]; content = parts[1];
            if (sender.equals(nameField.getText()) || sender.equals("Me")) isMyMessage = true;
            type = "PUBLIC"; target = UNIVERSAL_CHAT;
        }

        ChatMessage newMsg = new ChatMessage(sender, content, type, target, isMyMessage);
        allMessages.add(newMsg);

        boolean renderNow = false;
        if (currentChatTarget.equals(UNIVERSAL_CHAT) && type.equals("PUBLIC")) renderNow = true;
        if (type.equals("PRIVATE") && (target.equals(currentChatTarget) || sender.equals(currentChatTarget))) renderNow = true;

        if (renderNow) addBubbleToUI(newMsg);
    }
}