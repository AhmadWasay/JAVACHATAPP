package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.Socket;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LoginController {

    @FXML private VBox loginPane, registerPane, otpPane;
    @FXML private VBox otpStep1Box, otpStep2Box; 
    @FXML private TextField ipField, loginUserField, regUserField, regEmailField, otpEmailField, otpCodeField;
    @FXML private PasswordField loginPassField, regPassField;
    @FXML private Button loginBtn, goToRegisterBtn, registerBtn, sendOtpBtn, verifyOtpBtn;
    @FXML private Hyperlink forgotPasswordLink, backToLoginBtn1; 
    @FXML private Button backToLoginBtn2;
    @FXML private Label globalErrorLabel;

    private String serverHost = "localhost";
    private int serverPort = 5555;
    
    private boolean isRegistrationMode = false;

    @FXML
    public void initialize() {
        goToRegisterBtn.setOnAction(e -> switchView(registerPane));
        forgotPasswordLink.setOnAction(e -> {
            isRegistrationMode = false; 
            switchView(otpPane);
            resetOtpUI(); 
        });
        
        if (backToLoginBtn1 != null) backToLoginBtn1.setOnAction(e -> switchView(loginPane));
        if (backToLoginBtn2 != null) backToLoginBtn2.setOnAction(e -> switchView(loginPane));

        loginBtn.setOnAction(e -> handleLogin());
        registerBtn.setOnAction(e -> handleRegister());
        sendOtpBtn.setOnAction(e -> handleSendOtp());
        verifyOtpBtn.setOnAction(e -> handleVerifyOtp());
    }

    private void switchView(VBox target) {
        loginPane.setVisible(false);
        registerPane.setVisible(false);
        otpPane.setVisible(false);
        target.setVisible(true);
        globalErrorLabel.setVisible(false);
    }
    
    private void resetOtpUI() {
        if (otpStep1Box != null) { otpStep1Box.setVisible(true); otpStep1Box.setManaged(true); }
        if (otpStep2Box != null) { otpStep2Box.setVisible(false); otpStep2Box.setManaged(false); }
        otpEmailField.clear(); otpCodeField.clear();
    }

    // --- 1. LOGIN (FIXED: USES CHECK_LOGIN) ---
    private void handleLogin() {
        String user = loginUserField.getText().trim();
        String pass = loginPassField.getText().trim();
        String host = ipField.getText().trim();
        if(host.isEmpty()) host = "localhost";

        if (user.isEmpty() || pass.isEmpty()) { showError("Enter username & password."); return; }

        // FIX: Use CHECK_LOGIN so we don't trigger "User Joined/Left" broadcast
        connectAndExecute("C:CHECK_LOGIN " + user + " " + pass, response -> {
            if (response.startsWith("LOGIN_SUCCESS")) {
                loadChat(user);
            } else {
                showError("Login Failed: " + response);
            }
        }, host);
    }

    // --- 2. REGISTER ---
    private void handleRegister() {
        String user = regUserField.getText().trim();
        String pass = regPassField.getText().trim();
        String email = regEmailField.getText().trim();
        String host = ipField.getText().trim();
        if(host.isEmpty()) host = "localhost";

        if (user.isEmpty() || pass.isEmpty() || email.isEmpty()) { showError("All fields required."); return; }

        connectAndExecute("C:REGISTER " + user + " " + pass + " " + email, response -> {
            if (response.startsWith("OTP_REQ")) {
                isRegistrationMode = true;
                Platform.runLater(() -> {
                    switchView(otpPane);
                    otpStep1Box.setVisible(false); otpStep1Box.setManaged(false);
                    otpStep2Box.setVisible(true); otpStep2Box.setManaged(true);
                    showError("OTP sent to " + email);
                });
            } else {
                showError("Registration Failed: " + response);
            }
        }, host);
    }

    // --- 3. OTP VERIFICATION ---
    private void handleVerifyOtp() {
        String code = otpCodeField.getText().trim();
        String host = ipField.getText().trim();
        if(host.isEmpty()) host = "localhost";
        if (code.isEmpty()) return;

        String command;
        if (isRegistrationMode) {
            String email = regEmailField.getText().trim();
            command = "C:VERIFY_OTP " + email + " " + code;
        } else {
            String email = otpEmailField.getText().trim();
            command = "C:VERIFY_LOGIN_OTP " + email + " " + code;
        }

        connectAndExecute(command, response -> {
            if (isRegistrationMode) {
                // Registration Flow
                if (response.startsWith("REG_SUCCESS")) {
                    Platform.runLater(() -> {
                        // FIX: No Popup, just switch and show message
                        switchView(loginPane);
                        globalErrorLabel.setStyle("-fx-text-fill: #4CAF50;"); // Green color for success
                        showError("Account Created! Please Login.");
                        
                        // Reset color back to red after a few seconds (optional but good practice)
                        new Thread(() -> {
                            try { Thread.sleep(4000); } catch (InterruptedException e) {}
                            Platform.runLater(() -> globalErrorLabel.setStyle("-fx-text-fill: red;")); 
                        }).start();
                    });
                } else {
                    showError("Invalid Code.");
                }
            } else {
                // Login Flow (Keep existing logic)
                if (response.contains("LOGIN_SUCCESS")) {
                    String[] parts = response.split(" ");
                    String username = (parts.length > 1) ? parts[parts.length - 1] : "User";
                    loadChat(username);
                } else {
                    showError("Invalid Code.");
                }
            }
        }, host);
    }

    // --- 4. FORGOT PASSWORD ---
    private void handleSendOtp() {
        String email = otpEmailField.getText().trim();
        String host = ipField.getText().trim();
        if(host.isEmpty()) host = "localhost";

        connectAndExecute("C:REQUEST_LOGIN_OTP " + email, response -> {
            if (response.contains("OTP_SENT")) {
                Platform.runLater(() -> {
                    otpStep1Box.setVisible(false); otpStep1Box.setManaged(false);
                    otpStep2Box.setVisible(true); otpStep2Box.setManaged(true);
                });
            } else { showError("Error: " + response); }
        }, host);
    }

    // --- NETWORKING HELPER ---
    private interface ResponseHandler { void handle(String response); }

    private void connectAndExecute(String command, ResponseHandler handler, String host) {
        new Thread(() -> {
            try (Socket socket = new Socket(host, serverPort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out.println(command);
                String response = in.readLine();
                if (response != null) Platform.runLater(() -> handler.handle(response));
            } catch (IOException e) {
                Platform.runLater(() -> showError("Connection Error: " + e.getMessage()));
            }
        }).start();
    }

    private void showError(String msg) {
        if (globalErrorLabel != null) {
            globalErrorLabel.setText(msg);
            globalErrorLabel.setVisible(true);
            new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException e) {}
                Platform.runLater(() -> globalErrorLabel.setVisible(false));
            }).start();
        }
    }

    // --- LOAD CHAT & STYLING ---
    // --- LOAD CHAT & FORCE FULL SCREEN ---
    private void loadChat(String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
            Parent root = loader.load();

            // 1. Setup Controller
            Object controller = loader.getController();
            try {
                controller.getClass().getMethod("setServerInfo", String.class, int.class)
                          .invoke(controller, serverHost, serverPort);
                controller.getClass().getMethod("setAutoLogin", String.class, String.class)
                          .invoke(controller, username, "OTP_ACCESS");
            } catch (Exception e) { System.out.println("Error init chat: " + e.getMessage()); }

            // 2. Switch Scene
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            Scene scene = new Scene(root);
            
            // 3. Apply CSS
            try {
                String css = this.getClass().getResource("styles.css").toExternalForm();
                scene.getStylesheets().add(css);
            } catch (Exception e) { System.out.println("Warning: styles.css not found."); }

            stage.setScene(scene);
            stage.setTitle("JavaChat - " + username);

            // --- THE FIX: FORCE LAYOUT REFRESH ---
            // We quickly toggle maximize off and on. This forces JavaFX to 
            // recalculate the anchors and stretch the chat to the corners.
            stage.setMaximized(false);
            stage.setMaximized(true); 

        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to load chat: " + e.getMessage());
        }
    }
}