package org.example.equation_plotter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
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

        String css = Objects.requireNonNull(Equator.class.getResource("style.css")).toExternalForm();
        scene.getStylesheets().add(css);

        stage.setTitle("Equator");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
