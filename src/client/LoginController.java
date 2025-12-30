package client;

import common.Protocol;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class LoginController {

    // --- UI ELEMENTS ---
    @FXML private VBox loginPane;
    @FXML private VBox registerPane;
    @FXML private VBox otpPane;
    
    @FXML private TextField ipField;
    @FXML private TextField loginUserField;
    @FXML private PasswordField loginPassField;
    @FXML private Button loginBtn;
    @FXML private Button goToRegisterBtn;
    
    @FXML private Hyperlink forgotPasswordLink; // This was missing its action!

    @FXML private TextField regUserField;
    @FXML private PasswordField regPassField;
    @FXML private TextField regEmailField;
    @FXML private Button registerBtn;
    @FXML private Hyperlink backToLoginBtn1;

    @FXML private TextField otpEmailField;
    @FXML private Button sendOtpBtn;
    @FXML private Hyperlink backToLoginBtn2;
    
    @FXML private Label globalErrorLabel; 

    private static final int PORT = 5555;
    
    // Track OTP State (Step 1: Send Email, Step 2: Verify Code)
    private boolean isVerifyingOtp = false; 

    @FXML
    public void initialize() {
        // 1. Login Logic
        loginBtn.setOnAction(e -> handleLogin());
        
        // 2. Navigation: Go to Register
        goToRegisterBtn.setOnAction(e -> {
            switchScreen(registerPane);
        });

        // 3. Navigation: Back to Login
        backToLoginBtn1.setOnAction(e -> switchScreen(loginPane));
        backToLoginBtn2.setOnAction(e -> switchScreen(loginPane));

        // 4. FIX: "Forgot Password" Logic
        forgotPasswordLink.setOnAction(e -> {
            // Reset OTP screen to "Step 1"
            otpEmailField.setPromptText("Enter your Email Address");
            otpEmailField.setText("");
            sendOtpBtn.setText("Send Reset Code");
            isVerifyingOtp = false;
            
            switchScreen(otpPane);
        });

        // 5. Register Logic
        registerBtn.setOnAction(e -> handleRegistration());
        
        // 6. OTP/Reset Logic
        sendOtpBtn.setOnAction(e -> handleOtpFlow());
        
        // Default State
        switchScreen(loginPane);
    }
    
    // Helper to switch screens and clear errors
    private void switchScreen(VBox targetPane) {
        loginPane.setVisible(false);
        registerPane.setVisible(false);
        otpPane.setVisible(false);
        targetPane.setVisible(true);
        clearError();
    }

    private void handleLogin() {
        String ip = ipField.getText().trim();
        String user = loginUserField.getText().trim();
        String pass = loginPassField.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            showError("Please enter username and password.");
            return;
        }

        new Thread(() -> {
            try {
                Socket socket = new Socket(ip, PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println(Protocol.CLIENT_PREFIX + Protocol.CHECK_LOGIN + " " + user + " " + pass);
                
                String response = in.readLine();
                if (response != null && response.startsWith(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS)) {
                    Platform.runLater(() -> loadChatScreen(socket, user, pass, ip));
                } else {
                    Platform.runLater(() -> {
                        showError("Invalid Credentials");
                        try { socket.close(); } catch (IOException ignored) {}
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("Connection Failed: " + e.getMessage()));
            }
        }).start();
    }
    
    private void handleOtpFlow() {
        String ip = ipField.getText().trim();
        String input = otpEmailField.getText().trim();
        
        if (input.isEmpty()) {
            showError("Field cannot be empty.");
            return;
        }
        
        if (!isVerifyingOtp) {
            // STEP 1: SEND CODE
            showError("Sending code... please wait."); // Using error label as status for now
            globalErrorLabel.setStyle("-fx-background-color: #f39c12; -fx-padding: 15; -fx-background-radius: 0 0 8 8; -fx-text-fill: white;");
            
            // TODO: Connect to server and send REQ_LOGIN_OTP <email>
            // For now, we simulate the next step so you can see the UI change
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (Exception ignored) {}
                Platform.runLater(() -> {
                    // Switch UI to Step 2
                    isVerifyingOtp = true;
                    otpEmailField.setText("");
                    otpEmailField.setPromptText("Enter the 4-digit Code");
                    sendOtpBtn.setText("Verify Code");
                    showError("Code sent! Check your email/console.");
                    globalErrorLabel.setStyle("-fx-background-color: #27ae60; -fx-padding: 15; -fx-background-radius: 0 0 8 8; -fx-text-fill: white;");
                });
            }).start();
            
        } else {
            // STEP 2: VERIFY CODE
            // TODO: Connect to server and send VERIFY_LOGIN_OTP <email> <code>
            showError("Verifying...");
        }
    }

    private void handleRegistration() {
        String ip = ipField.getText().trim();
        String user = regUserField.getText().trim();
        String pass = regPassField.getText().trim();
        String email = regEmailField.getText().trim();

        if (user.isEmpty() || pass.isEmpty() || email.isEmpty()) {
            showError("All fields are required.");
            return;
        }

        new Thread(() -> {
            try (Socket socket = new Socket(ip, PORT);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(Protocol.CLIENT_PREFIX + Protocol.REGISTER + " " + user + " " + pass + " " + email);
                String response = in.readLine(); 
                
                if (response != null && response.contains("SUCCESS")) {
                    Platform.runLater(() -> {
                        showError("Registration Successful! Please Login."); 
                        globalErrorLabel.setStyle("-fx-background-color: #27ae60; -fx-padding: 15; -fx-background-radius: 0 0 8 8; -fx-text-fill: white;");
                        switchScreen(loginPane);
                    });
                } else {
                    Platform.runLater(() -> showError("Registration Failed. Username taken?"));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showError("Connection Error"));
            }
        }).start();
    }

    private void loadChatScreen(Socket oldSocket, String user, String pass, String host) {
        try {
            oldSocket.close(); 
            FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
            Parent root = loader.load();
            
            ChatController controller = loader.getController();
            controller.setServerInfo(host, PORT);
            controller.setAutoLogin(user, pass); 

            Stage stage = (Stage) loginBtn.getScene().getWindow();
            Scene scene = new Scene(root);
            
            if (getClass().getResource("styles.css") != null) {
                scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
            }
            
            stage.setTitle("JavaChat - " + user);
            
            // Full Screen Fix
            stage.setMaximized(false); 
            stage.setScene(scene);
            stage.setMaximized(true);  
            
        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to load chat screen.");
        }
    }

    private void showError(String msg) {
        globalErrorLabel.setText(msg);
        globalErrorLabel.setVisible(true);
        // Default Red Error
        globalErrorLabel.setStyle("-fx-background-color: #fa3e3e; -fx-padding: 15; -fx-background-radius: 0 0 8 8; -fx-text-fill: white; -fx-font-weight: bold;");
        
        new Thread(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> globalErrorLabel.setVisible(false));
        }).start();
    }
    
    private void clearError() {
        globalErrorLabel.setVisible(false);
    }
}