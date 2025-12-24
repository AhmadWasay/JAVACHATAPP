package client;

import common.Protocol;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

public class LoginController {

    @FXML private TextField userField;
    @FXML private PasswordField passField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Label errorLabel;

    private static final String HOST = "localhost";
    private static final int PORT = 5555;

    @FXML
    public void initialize() {
        loginButton.setOnAction(e -> handleAuth(true));
        registerButton.setOnAction(e -> handleAuth(false));
    }

    private void handleAuth(boolean isLogin) {
        String user = userField.getText().trim();
        String pass = passField.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        // Run network operation in a background thread to keep UI responsive
        new Thread(() -> {
            try {
                // 1. Open a temporary connection to check credentials
                Socket socket = new Socket(HOST, PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // 2. Send Command: C:LOGIN user pass
                String command = isLogin ? Protocol.LOGIN : Protocol.REGISTER;
                out.println(Protocol.CLIENT_PREFIX + command + " " + user + " " + pass);

                // 3. Read Response
                String response = in.readLine(); // Expecting "S:LOGIN_SUCCESS <user>"
                
                Platform.runLater(() -> {
                    if (response != null && response.startsWith(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS)) {
                        // Success! Close this temp socket and open the main Chat UI
                        try {
                            socket.close(); // We close this because ChatClient will make its own robust connection
                            openChatScene(user);
                        } catch (IOException ex) {
                            showError("Error switching scenes.");
                        }
                    } else {
                        // Failure (User taken or Wrong password)
                        showError(isLogin ? "Invalid username or password." : "Username already exists.");
                        try { socket.close(); } catch (IOException ignored) {}
                    }
                });

            } catch (IOException e) {
                Platform.runLater(() -> showError("Cannot connect to server."));
            }
        }).start();
    }

    private void showError(String msg) {
        errorLabel.setVisible(true);
        errorLabel.setText(msg);
    }

    private void openChatScene(String username) throws IOException {
        // Load the main chat window
        FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
        Parent root = loader.load();

        // Get the ChatController and tell it to auto-connect
        ChatController controller = loader.getController();
        controller.setAutoLogin(username); // <--- We will add this method next
        // Capture password from field before clearing or changing scenes
        String pass = passField.getText().trim(); 
        controller.setAutoLogin(username, pass);
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle("JavaChat - " + username);
        stage.setMinWidth(600);
        stage.setMinHeight(400);
    }
}