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
    private final double DRAW_DURATION_NANOS = 5_000_000_000.0;
    private static final Logger LOGGER = Logger.getLogger(Equator.class.getName());
    private final double sigma = 10.0;
    private final double rho = 28.0;
    private final double beta = 8.0 / 3.0;
    private final double dt = 0.01; // Integration time step
    // --- Chaos Theory: Lorenz Attractor State ---
    private double lx = 0.1, ly = 0, lz = 0;

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
                gc.setFill(Color.rgb(0, 0, 0, 0.037));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                // Calculate and draw multiple steps per frame for smooth animation
                for (int i = 0; i < 9; i++) {
                    drawChaosStep(gc, canvas.getWidth(), canvas.getHeight(), progress);
                }

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
        double scaleX = 8.5;
        double scaleZ = 1.7;
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
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(Duration.seconds(0.8));
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