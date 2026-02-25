package org.example.equation_plotter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

public class Equator extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        URL fxmlLocation = Equator.class.getResource("view.fxml");
        if (fxmlLocation == null) {
            throw new IllegalStateException("Error: FXML file 'view.fxml' not found in package org.example.equation_plotter");
        }

        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);
        Scene scene = new Scene(fxmlLoader.load());
        EquatorController mainController = fxmlLoader.getController();
        NavBar navBarController = (NavBar) fxmlLoader.getNamespace().get("navbarController");
        navBarController.setMainController(mainController);
        String css = Objects.requireNonNull(Equator.class.getResource("style.css")).toExternalForm();
        scene.getStylesheets().add(css);
        Stage primaryStage = new Stage();
        try {
            InputStream iconStream = getClass().getResourceAsStream("/icon.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                System.out.println("Warning: icon.png not found in resources!");
            }
        } catch (Exception e) {
            System.out.println("Error loading icon: " + e.getMessage());
        }
        stage.setTitle("Equator");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
