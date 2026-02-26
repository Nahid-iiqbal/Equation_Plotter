package org.example.equation_plotter;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Equator extends Application {
    private long startTime = -1;
    private final double DRAW_DURATION_NANOS = 3_000_000_000.0; // 3 seconds to draw
    private static final Logger LOGGER = Logger.getLogger(Equator.class.getName());

    @Override
    public void start(Stage stage) throws IOException {
        Stage welcomeStage = new Stage();
        welcomeStage.initStyle(StageStyle.UNDECORATED);
        welcomeStage.getIcons().add(new Image(Objects.requireNonNull(Equator.class.getResourceAsStream("/icon.png"))));

        FXMLLoader welcomeLoader = new FXMLLoader(Equator.class.getResource("welcome.fxml"));
        Parent welcomeRoot = welcomeLoader.load();

        Canvas canvas = (Canvas) welcomeRoot.lookup("#waveCanvas");
        GraphicsContext gc = canvas.getGraphicsContext2D();

        welcomeStage.setScene(new Scene(welcomeRoot));
        welcomeStage.show();
        //welcomeStage.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        String fontPath = "fonts/Megrim-Regular.ttf";
        Font loadedFont = Font.loadFont(getClass().getResourceAsStream(fontPath), 14);

        if (loadedFont == null) {
            System.out.println("Error: Could not find font at " + fontPath);
        } else {
            // This prints the EXACT name you must use in your CSS
            System.out.println("Loaded Font Family: " + loadedFont.getFamily());
        }
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (startTime < 0) startTime = now;

                // Calculate progress (0.0 to 1.0) based on elapsed time
                double progress = Math.min(1.0, (now - startTime) / DRAW_DURATION_NANOS);

                drawSelfDrawingWave(gc, canvas.getWidth(), canvas.getHeight(), progress, now);

                if (progress >= 1.0) {
                    // Stop the timer and transition once the curve is fully drawn
                    this.stop();
                    closeAndTransition(welcomeStage, stage);
                }
            }
        };
        timer.start();
    }

    private void drawSelfDrawingWave(GraphicsContext gc, double w, double h, double progress, long now) {
        gc.clearRect(0, 0, w, h);
        double centerY = h / 2;
        double currentMaxX = w * progress;
        double timeOffset = now / 250_000_000.0;
        // 1. Draw the actual wave line
        gc.setStroke(Color.web("#00FFFF"));
        gc.setLineWidth(3);
        gc.setFill(Color.web("#00FFFF", 0.3));
        gc.fillOval(-10, centerY - 10, 20, 20);
        gc.setFill(Color.web("#00FFFF"));
        gc.fillOval(-4, centerY - 4, 8, 8);


        gc.beginPath();
        gc.moveTo(0, centerY);

        double lastY = centerY;
        for (double x = 0; x <= currentMaxX; x++) {
            double localAmplitude = Math.min(30, x * 0.2);
            lastY = centerY + Math.sin(x * 0.05 - timeOffset) * localAmplitude;
            gc.lineTo(x, lastY);
        }
        gc.stroke();

        // 2. Add the Leading Point (The Head)
        if (progress > 0 && progress < 1) {
            // Draw a outer glow for the point
            gc.setFill(Color.web("#00FFFF", 0.3)); // Transparent cyan for glow
            gc.fillOval(currentMaxX - 8, lastY - 8, 16, 16);

            // Draw the solid core of the point
            gc.setFill(Color.web("#FFFFFF")); // White core for high-intensity look
            gc.fillOval(currentMaxX - 4, lastY - 4, 8, 8);
        }
    }

    private void closeAndTransition(Stage welcomeStage, Stage mainStage) {
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(Duration.seconds(0.5));
        delay.setOnFinished(e -> {
            welcomeStage.close();
            try {
                FXMLLoader fxmlLoader = new FXMLLoader(Equator.class.getResource("view.fxml"));
                Scene scene = new Scene(fxmlLoader.load());
                mainStage.setScene(scene);
                mainStage.setTitle("Equator");
                mainStage.getIcons().add(new Image(Objects.requireNonNull(Equator.class.getResourceAsStream("/icon.png"))));
                mainStage.setMaximized(true);
                mainStage.show();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Error transitioning from welcome screen to main stage", ex);
            }
        });
        delay.play();
    }
}
