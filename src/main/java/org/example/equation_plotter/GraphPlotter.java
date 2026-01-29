package org.example.equation_plotter;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;

public class GraphPlotter extends Canvas {

    //graph center
    private double graphCenterX = 0;
    private double graphCenterY = 0;
    private double scale = 50;
    //mouse position
    private double prevMouseX;
    private double prevMouseY;
    //equation
    private String currentEquation = "";

    //handles mouse functionalities in graph
    public GraphPlotter(double width, double height) {
        super(width, height);
        widthProperty().addListener(e -> draw());
        heightProperty().addListener(e -> draw());


        // gets position of mouse when hold-click
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                prevMouseX = e.getX();
                prevMouseY = e.getY();
                getScene().setCursor(javafx.scene.Cursor.CLOSED_HAND);
            }
        });

        //mouse dragged
        setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double dx = e.getX() - prevMouseX;
                double dy = e.getY() - prevMouseY;
                graphCenterX += dx;
                graphCenterY += dy;
                prevMouseX = e.getX();
                prevMouseY = e.getY();
                draw();
            }
        });

        //mouse released
        setOnMouseReleased(e -> getScene().setCursor(javafx.scene.Cursor.DEFAULT));

        //mouse wheel
        setOnScroll(e -> {
            double zoom = 1.1;
            if (e.getDeltaY() < 0) {
                scale /= zoom;
            }
            if (e.getDeltaY() > 0) {
                scale *= zoom;
            }
            draw();
        });


    }

    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);

        drawGrid(gc, w, h);
        if (!currentEquation.isEmpty()) {
            drawFunction(gc, w, h);
        }
    }

    private void drawGrid(GraphicsContext gc, double w, double h) {
        double left = graphCenterX - w / 2 / scale;
        double top = graphCenterY - h / 2 / scale;


        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1);

        double gridSpacing = 1.0;
        if (scale < 20) gridSpacing = 5.0;
        if (scale > 100) gridSpacing = 0.5;

        double startX = (Math.floor(left / gridSpacing) * gridSpacing);
        for (double x = startX; (x - startX) * scale < w + scale; x += gridSpacing) {
            double pixelX = (x - graphCenterX) * scale + w / 2;
            gc.strokeLine(pixelX, 0, pixelX, h);
        }

        double startY = (Math.floor((graphCenterY - (h / 2) / scale) / gridSpacing) * gridSpacing);
        for (double y = startY; (y - startY) * scale < h + scale; y += gridSpacing) {
            double pixelY = h / 2 - (y - graphCenterY) * scale;
            gc.strokeLine(0, pixelY, w, pixelY);
        }

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);

        double yAxisPixel = (0 - graphCenterX) * scale + w / 2;
        gc.strokeLine(yAxisPixel, 0, yAxisPixel, h);

        double xAxisPixel = h / 2 - (0 - graphCenterY) * scale;
        gc.strokeLine(0, xAxisPixel, w, xAxisPixel);
    }

    private void drawFunction(GraphicsContext gc, double w, double h) {

    }

    //set equation
    public void plotEquation(String equationIn) {
        if (equationIn == null) return;
        this.currentEquation = equationIn;
        draw();
    }


    //graph buttons
    public void zoomIn() {
        scale *= 1.1;
        draw();
    }

    public void zoomOut() {
        scale /= 1.1;
        draw();
    }

    public void reset() {
        scale = 50;
        graphCenterX = 0;
        graphCenterY = 0;
        draw();
    }
}