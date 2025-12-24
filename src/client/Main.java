package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // CHANGED: Load login.fxml instead of chat.fxml
        FXMLLoader fxml = new FXMLLoader(getClass().getResource("login.fxml"));
        Scene scene = new Scene(fxml.load());
        
        // Use the same styles
        if (getClass().getResource("styles.css") != null) {
            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        }
        
        stage.setTitle("JavaChat - Login");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}