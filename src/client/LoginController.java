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

import java.io.*;
import java.net.Socket;

public class LoginController {

    // Panes
    @FXML private VBox loginPane;
    @FXML private VBox registerPane;
    @FXML private VBox otpPane;

    // Login Fields
    @FXML private TextField loginUserField;
    @FXML private PasswordField loginPassField;
    @FXML private Button loginBtn;
    @FXML private Hyperlink forgotPasswordLink;
    @FXML private Button goToRegisterBtn;

    // Register Fields
    @FXML private TextField regUserField;
    @FXML private PasswordField regPassField;
    @FXML private TextField regEmailField;
    @FXML private Button registerBtn;
    @FXML private Button backToLoginBtn1;

    // Email Login Fields
    @FXML private TextField otpEmailField;
    @FXML private Button sendOtpBtn;
    @FXML private Button backToLoginBtn2;

    @FXML private Label globalErrorLabel;

    private static final String HOST = "localhost";
    private static final int PORT = 5555;

    @FXML
    public void initialize() {
        // Switch Screens
        goToRegisterBtn.setOnAction(e -> showPane(registerPane));
        backToLoginBtn1.setOnAction(e -> showPane(loginPane));
        forgotPasswordLink.setOnAction(e -> showPane(otpPane));
        backToLoginBtn2.setOnAction(e -> showPane(loginPane));

        // Actions
        loginBtn.setOnAction(e -> handleStandardLogin());
        registerBtn.setOnAction(e -> handleRegistration());
        sendOtpBtn.setOnAction(e -> handleEmailLogin());
    }

    private void showPane(VBox pane) {
        loginPane.setVisible(false);
        registerPane.setVisible(false);
        otpPane.setVisible(false);
        pane.setVisible(true);
        globalErrorLabel.setVisible(false);
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            globalErrorLabel.setText(msg);
            globalErrorLabel.setVisible(true);
        });
    }

    private void handleStandardLogin() {
        String user = loginUserField.getText().trim();
        String pass = loginPassField.getText().trim();
        if(user.isEmpty() || pass.isEmpty()) { showError("Enter credentials"); return; }
        
        runTask(socket -> {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(Protocol.CLIENT_PREFIX + Protocol.CHECK_LOGIN + " " + user + " " + pass);
            String resp = in.readLine();
            
            if (resp != null && resp.startsWith(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS)) {
                Platform.runLater(() -> loadChat(socket, user, pass));
            } else {
                showError("Invalid username or password");
                socket.close();
            }
        });
    }

    private void handleRegistration() {
        String user = regUserField.getText().trim();
        String pass = regPassField.getText().trim();
        String email = regEmailField.getText().trim();
        if(user.isEmpty() || email.isEmpty()) { showError("Fields required"); return; }

        runTask(socket -> {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(Protocol.CLIENT_PREFIX + Protocol.REGISTER + " " + user + " " + pass + " " + email);
            String resp = in.readLine();

            if (resp != null && resp.contains("OTP_REQ")) {
                String code = promptCode("Enter Verification Code", "Code sent to " + email);
                if(code != null) {
                    out.println(Protocol.CLIENT_PREFIX + "VERIFY_OTP " + code);
                    String verifyResp = in.readLine();
                    if(verifyResp != null && verifyResp.contains("LOGIN_SUCCESS")) {
                        Platform.runLater(() -> {
                            showError("Registration Success! Please Login.");
                            try { socket.close(); } catch(Exception e){}
                            showPane(loginPane);
                        });
                    } else {
                        showError("Invalid Code");
                        socket.close();
                    }
                }
            } else {
                showError("Registration Failed (User exists?)");
                socket.close();
            }
        });
    }

    private void handleEmailLogin() {
        String email = otpEmailField.getText().trim();
        if(email.isEmpty()) { showError("Enter Email"); return; }

        runTask(socket -> {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(Protocol.CLIENT_PREFIX + Protocol.REQUEST_LOGIN_OTP + " " + email);
            String resp = in.readLine();

            if (resp != null && resp.contains("OTP_SENT")) {
                String code = promptCode("Login Verification", "Enter code sent to " + email);
                if(code != null) {
                    out.println(Protocol.CLIENT_PREFIX + Protocol.VERIFY_LOGIN_OTP + " " + email + " " + code);
                    String loginResp = in.readLine();
                    if(loginResp != null && loginResp.startsWith(Protocol.SERVER_PREFIX + Protocol.LOGIN_SUCCESS)) {
                        String username = loginResp.split(" ")[1];
                        Platform.runLater(() -> loadChat(socket, username, "otp-session"));
                    } else {
                        showError("Invalid Code");
                        socket.close();
                    }
                }
            } else {
                showError("Email not found");
                socket.close();
            }
        });
    }

    private void loadChat(Socket socket, String user, String pass) {
        try {
            socket.close(); // Close login socket
            FXMLLoader loader = new FXMLLoader(getClass().getResource("chat.fxml"));
            Parent root = loader.load();
            ChatController controller = loader.getController();
            controller.setAutoLogin(user, pass);
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("JavaChat - " + user);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String promptCode(String title, String content) {
        final String[] res = {null};
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            TextInputDialog td = new TextInputDialog();
            td.setTitle(title); td.setContentText(content);
            td.showAndWait().ifPresent(c -> res[0] = c);
            latch.countDown();
        });
        try { latch.await(); } catch(Exception e) {}
        return res[0];
    }

    private void runTask(SocketTask task) {
        new Thread(() -> {
            try {
                Socket s = new Socket(HOST, PORT);
                task.run(s);
            } catch (Exception e) { showError("Connection Failed"); }
        }).start();
    }
    
    interface SocketTask { void run(Socket s) throws Exception; }
}