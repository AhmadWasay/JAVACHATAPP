package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            
            try {
                String css = getClass().getResource("styles.css").toExternalForm();
                scene.getStylesheets().add(css);
            } catch (Exception e) { System.out.println("CSS missing"); }

            stage.setTitle("JavaChat Enterprise");
            stage.setScene(scene);
            
            stage.show();
            
            stage.setMaximized(true);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}