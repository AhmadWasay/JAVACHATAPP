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

        // Disable buttons to prevent double-clicking
        loginButton.setDisable(true);
        registerButton.setDisable(true);

        new Thread(() -> {
            try {
                Socket socket = new Socket(HOST, PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // OLD: String command = isLogin ? Protocol.LOGIN : Protocol.REGISTER;
                // NEW: Use CHECK_LOGIN for logging in, but keep REGISTER for registering
                String command = isLogin ? Protocol.CHECK_LOGIN : Protocol.REGISTER;
                out.println(Protocol.CLIENT_PREFIX + command + " " + user + " " + pass);

                String response = in.readLine(); 
                
                Platform.runLater(() -> {
                    if (response != null && response.startsWith(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS)) {
                        try {
                            socket.close(); 
                            openChatScene(user, pass); // <--- FIXED: Passing both User and Pass
                        } catch (IOException ex) {
                            showError("Error switching scenes.");
                        }
                    } else {
                        showError(isLogin ? "Invalid username or password." : "Username already exists.");
                        try { socket.close(); } catch (IOException ignored) {}
                        // Re-enable buttons on failure
                        loginButton.setDisable(false);
                        registerButton.setDisable(false);
                    }
                });

            } catch (IOException e) {
                Platform.runLater(() -> {
                    showError("Cannot connect to server.");
                    loginButton.setDisable(false);
                    registerButton.setDisable(false);
                });
            }
        }).start();
    }

    private void showError(String msg) {
        errorLabel.setVisible(true);
        errorLabel.setText(msg);
    }

    // UPDATED METHOD: Accepts password
    private void openChatScene(String username, String password) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
        Parent root = loader.load();

        ChatController controller = loader.getController();
        // Pass both credentials to the ChatController
        controller.setAutoLogin(username, password); 

        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle("JavaChat - " + username);
        stage.setMinWidth(600);
        stage.setMinHeight(400);
    }
}