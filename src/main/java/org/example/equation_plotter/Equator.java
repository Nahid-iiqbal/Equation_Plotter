package org.example.equation_plotter;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Equator extends Application {

    private long startTime = -1;
    // Trail: each entry is {screenX, screenY}
    private static final int TRAIL_LENGTH = 100; // number of points kept
    private static final Logger LOGGER = Logger.getLogger(Equator.class.getName());
    private final double DRAW_DURATION_NANOS = 5_000_000_000.0;
    private final Deque<double[]> trail = new ArrayDeque<>();
    private double lx = 0.1, ly = 0.0, lz = 0.0;

    @Override
    public void start(Stage stage) throws IOException {
        Stage welcomeStage = new Stage();
        welcomeStage.initStyle(StageStyle.UNDECORATED);
        welcomeStage.getIcons().add(new Image(
                Objects.requireNonNull(Equator.class.getResourceAsStream("/icon.png"))));

        FXMLLoader welcomeLoader = new FXMLLoader(Equator.class.getResource("welcome.fxml"));
        Parent welcomeRoot = welcomeLoader.load();

        Canvas canvas = (Canvas) welcomeRoot.lookup("#waveCanvas");
        ProgressBar bar = (ProgressBar) welcomeRoot.lookup("#loadBar");
        GraphicsContext gc = canvas.getGraphicsContext2D();

        welcomeStage.setScene(new Scene(welcomeRoot));
        welcomeStage.show();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (startTime < 0) startTime = now;
                double progress = Math.min(1.0, (now - startTime) / DRAW_DURATION_NANOS);

                if (bar != null) bar.setProgress(progress);

                double w = canvas.getWidth();
                double h = canvas.getHeight();

                // Fully clear each frame — the fade is drawn manually per segment
                gc.clearRect(0, 0, w, h);
                gc.setFill(Color.BLACK);
                gc.fillRect(0, 0, w, h);

                // Advance Lorenz a few steps to get the next head position
                for (int i = 0; i < 8; i++) {
                    stepLorenz();
                }

                // Map Lorenz X→screenX, Z→screenY
                double scaleX = w * 0.85 / 40.0;
                double scaleZ = h * 0.85 / 50.0;
                double centerX = w / 2.0;
                double centerY = h / 2.0;
                double headX = centerX + lx * scaleX;
                double headY = centerY - (lz - 25.0) * scaleZ;

                // Push new head; trim tail to TRAIL_LENGTH
                trail.addFirst(new double[]{headX, headY});
                while (trail.size() > TRAIL_LENGTH) trail.removeLast();

                // ── Draw trail: each segment fades from opaque → transparent ──
                double hue = (progress * 360.0) % 360.0;
                double[][] points = trail.toArray(new double[0][]);

                for (int i = 0; i < points.length - 1; i++) {
                    // i=0 is the head (most opaque), tail fades to 0
                    double alpha = 1.0 - (double) i / (points.length - 1);
                    double lineWidth = 2.5 * alpha + 0.5; // thick at head, thin at tail

                    gc.setStroke(Color.hsb(hue, 1.0, 1.0, alpha * 0.9));
                    gc.setLineWidth(lineWidth);
                    gc.strokeLine(points[i][0], points[i][1],
                            points[i + 1][0], points[i + 1][1]);
                }

                // ── Source circle at the head ─────────────────────────────────
                // Outer glow ring
                gc.setFill(Color.hsb(hue, 1.0, 1.0, 0.18));
                gc.fillOval(headX - 14, headY - 14, 28, 28);

                // Mid glow
                gc.setFill(Color.hsb(hue, 0.8, 1.0, 0.45));
                gc.fillOval(headX - 8, headY - 8, 16, 16);

                // Solid colored circle
                gc.setFill(Color.hsb(hue, 1.0, 1.0, 0.9));
                gc.fillOval(headX - 5, headY - 5, 10, 10);

                // Bright white core
                gc.setFill(Color.WHITE);
                gc.fillOval(headX - 2, headY - 2, 4, 4);

                if (progress >= 1.0) {
                    this.stop();
                    closeAndTransition(welcomeStage, stage);
                }
            }
        };
        for (int i = 0; i < 5000; i++) {
            stepLorenz();
        }
        timer.start();
    }

    private void stepLorenz() {
        double dt = 0.003;
        // Lorenz parameters — just for driving an interesting path
        double sigma = 10.0;
        double rho = 28.0;
        double beta = 8.0 / 3.0;

        double dx = sigma * (ly - lx) * dt;
        double dy = (lx * (rho - lz) - ly) * dt;
        double dz = (lx * ly - beta * lz) * dt;
        lx += dx;
        ly += dy;
        lz += dz;
    }

    private void closeAndTransition(Stage welcomeStage, Stage mainStage) {
        welcomeStage.getScene().setFill(Color.web("#1e1e1e"));
        Parent welcomeRoot = welcomeStage.getScene().getRoot();

        FadeTransition fadeOut = new FadeTransition(Duration.millis(600), welcomeRoot);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        TranslateTransition slideUp = new TranslateTransition(Duration.millis(600), welcomeRoot);
        slideUp.setFromY(0);
        slideUp.setToY(-40);

        ParallelTransition exitAnim = new ParallelTransition(fadeOut, slideUp);
        exitAnim.setOnFinished(e -> {
            welcomeStage.close();
            javafx.application.Platform.runLater(() -> {
                try {
                    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("view.fxml"));
                    Parent root = fxmlLoader.load();
                    root.setStyle("-fx-background-color: #1e1e1e;");
                    Scene scene = new Scene(root);
                    scene.setFill(Color.web("#1e1e1e"));
                    // initStyle removed from here — already set in start()

                    mainStage.setScene(scene);
                    mainStage.setTitle("Equator");
                    mainStage.getIcons().add(new Image(Objects.requireNonNull(
                            getClass().getResourceAsStream("/icon.png"))));
                    mainStage.setMaximized(true);
                    mainStage.show();

                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to load main screen", ex);
                }
            });
        });
        exitAnim.play();
    }
}