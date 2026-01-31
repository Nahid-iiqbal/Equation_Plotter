package org.example.equation_plotter;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

// New class for storing equation data and parser
class EquationData {
    String raw;
    EquationParser parser;
}

public class GraphPlotter extends Canvas {

    //graph center
    private double graphCenterX = 0;
    private double graphCenterY = 0;
    private double scale = 50;
    //mouse position
    private double prevMouseX;
    private double prevMouseY;
    //equation

    // Used hashmap<id, eq> for storing equations instead of array.
    private Map<String, EquationData> currentEquations = new HashMap<>();
    // private String[] currentEquations = new String[50];
    private int eqCount = 0;

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
                double dx = (e.getX() - prevMouseX)/scale;
                double dy = (e.getY() - prevMouseY)/scale;
                graphCenterX -= dx;
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
            double mouseX = e.getX();
            double mouseY = e.getY();
            double prevScale = scale;

            // corresponding point (x,y) of the graph
            double graphX = graphCenterX + (mouseX - getWidth()/2)/prevScale;
            double graphY = graphCenterY + (getHeight()/2 - mouseY)/prevScale;

            // Initial zoom logic (unchanged)
            double zoom = 1.1;
            if (e.getDeltaY() < 0) {
                scale /= zoom;
            }
            if (e.getDeltaY() > 0) {
                scale *= zoom;
            }

            // Converted graphCenter
            graphCenterX = graphX - (mouseX - getWidth() / 2) / scale;
            graphCenterY = graphY - (getHeight() / 2 - mouseY) / scale;

            draw();
        });


    }

    // Avoid redraw storms.
    private boolean isDrawing = false;

    public void draw() {
        if (isDrawing) return;

        // Draws the graph (Axis lines, gridboxes)
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);

        drawGrid(gc, w, h);

        // I HAVE TO HANDLE THIS CASE LATER -R
//        if (eqCount > 0 && currentEquations[0] != null) {
//            drawFunction(gc, w, h);
//        }
        drawFunction(gc, w, h);
        isDrawing = false;
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
        for (EquationData equation : currentEquations.values()) {
            if (equation == null || equation.raw.trim().isEmpty()) continue;
            EquationParser parser = equation.parser;

            gc.beginPath();
            gc.setStroke(Color.CYAN);
            gc.setLineWidth(2);

            boolean firstPoint = true;

            // Iterate over pixel X coordinates
            double step = Math.max(1,scale/50);  // slight optimization
            for (double pixelX = 0; pixelX < w; pixelX+=step) {
                // Screen X -> Graph X
                double graphX = graphCenterX + (pixelX - w / 2.0) / scale;

                // Calculate Math Y
                double graphY = parser.evaluate(graphX);

                // Check for valid result (handle division by zero, etc.)
                if (Double.isNaN(graphY) || Double.isInfinite(graphY)) {
                    firstPoint = true;
                    continue;
                }

                // Math Y -> Screen Y
                double pixelY = h / 2.0 - (graphY - graphCenterY) * scale;

                // Draw
                if (firstPoint) {
                    gc.moveTo(pixelX, pixelY);
                    firstPoint = false;
                } else {
                    gc.lineTo(pixelX, pixelY);
                }
            }
            gc.stroke();
        }
    }

    //set equation [COMMENTED OUT FOR NOW, WILL REMOVE LATER - R]
//    public void plotEquation(String equationIn) {
//        if (equationIn == null) return;
//        this.currentEquations[eqCount++] = equationIn;
//        draw();
//    }

    // plotEquation is "addEquationToHashmap" now :)
    public void addEquationToHashmap(String id, String equation){
        if (equation == null) return;

        EquationData data = new EquationData();
        data.raw = equation;
        data.parser = new EquationParser(equation);  // equation is parsed here

        currentEquations.put(id, data);
        draw();
    }

    public void removeEquation(String id){
        currentEquations.remove(id);
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