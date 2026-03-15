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
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Equator extends Application {
    private long startTime = -1;
    // Increased duration slightly to allow the chaos to build up
    private final double DRAW_DURATION_NANOS = 3_500_000_000.0;
    private static final Logger LOGGER = Logger.getLogger(Equator.class.getName());
    private final double sigma = 10.0;
    private final double rho = 28.0;
    private final double beta = 8.0 / 3.0;
    private final double dt = 0.01; // Integration time step
    // --- Chaos Theory: Lorenz Attractor State ---
    private double lx = 0.1, ly = 0, lz = 0;
    private int frameCount = 0;

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
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (startTime < 0) startTime = now;

                double progress = Math.min(1.0, (now - startTime) / DRAW_DURATION_NANOS);

                // Create a "motion blur" trail effect by not fully clearing
                if (frameCount % 120 == 0) {
                    gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    gc.setFill(Color.BLACK);
                    gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    frameCount = 0;
                } else {
                    // 2. Standard motion blur trail
                    gc.setFill(Color.rgb(0, 0, 0, 0.045)); // Slightly increased for faster fade
                    gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                }
                // Calculate and draw multiple steps per frame for smooth animation
                for (int i = 0; i < 9; i++) {
                    drawChaosStep(gc, canvas.getWidth(), canvas.getHeight(), progress);
                }
                frameCount++;
                if (progress >= 1.0) {
                    this.stop();
                    closeAndTransition(welcomeStage, stage);
                }
            }
        };
        timer.start();
    }

    private void drawChaosStep(GraphicsContext gc, double w, double h, double progress) {
        // Calculate the next point in the attractor using Lorenz equations
        double dx = (sigma * (ly - lx)) * dt;
        double dy = (lx * (rho - lz) - ly) * dt;
        double dz = (lx * ly - beta * lz) * dt;

        double prevX = lx;
        double prevZ = lz;

        lx += dx;
        ly += dy;
        lz += dz;

        // Scaling for the 500x100 canvas
        double scaleX = 7;
        double scaleZ = 3.5;
        double centerX = w / 2;
        double centerY = h - 5;

        // Project 3D points to 2D canvas coordinates
        double x1 = centerX + prevX * scaleX;
        double y1 = centerY - prevZ * scaleZ;
        double x2 = centerX + lx * scaleX;
        double y2 = centerY - lz * scaleZ;

        // Neon coloring that shifts as the workspace loads
        double hue = (180 + progress * 120) % 360;
        gc.setStroke(Color.hsb(hue, 1.0, 1.0, 0.9));
        gc.setLineWidth(3);

        gc.strokeLine(x1, y1, x2, y2);

        // Leading glow point
        if (progress < 1.0) {
            gc.setFill(Color.web("#FFFFFF", 0.5));
            gc.fillOval(x2 - 1.5, y2 - 1.5, 3, 3);
            gc.setFill(Color.WHITE);
            gc.fillOval(x2 - 1, y2 - 1, 2, 2);
        }
    }

    private void closeAndTransition(Stage welcomeStage, Stage mainStage) {
        // 1. Create a Fade out for the welcome screen to make it stylish
        welcomeStage.getScene().setFill(Color.web("#1e1e1e"));
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
                Duration.millis(500), welcomeStage.getScene().getRoot());
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(e -> {
            welcomeStage.close();

            javafx.application.Platform.runLater(() -> {
                try {
                    // Ensure the path to view.fxml is correct relative to the class
                    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("view.fxml"));
                    Parent root = fxmlLoader.load();
                    Scene scene = new Scene(root);

                    mainStage.setScene(scene);
                    mainStage.setTitle("Equator");

                    // Add your icon (ensure /icon.png exists in resources)
                    mainStage.getIcons().add(new Image(Objects.requireNonNull(
                            getClass().getResourceAsStream("/icon.png"))));

                    // Workaround for Maximized state bug in some JavaFX versions
                    mainStage.setMaximized(true);
                    mainStage.show();

                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to load main screen", ex);
                }
            });
        });
        fadeOut.play();
    }
}