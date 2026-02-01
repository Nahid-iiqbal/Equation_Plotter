package org.example.equation_plotter;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.text.DecimalFormat;
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
    // Zoom limits
    private static final double MIN_SCALE = 1.0e-2;
    private static final double MAX_SCALE = 1.0e5;

    //mouse position
    private double prevMouseX;
    private double prevMouseY;
    //equation

    // Used hashmap<id, eq> for storing equations instead of array.
    private final Map<String, EquationData> currentEquations = new HashMap<>();
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
                double dx = (e.getX() - prevMouseX) / scale;
                double dy = (e.getY() - prevMouseY) / scale;
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
            double graphX = graphCenterX + (mouseX - getWidth() / 2) / prevScale;
            double graphY = graphCenterY + (getHeight() / 2 - mouseY) / prevScale;

            double zoom = 1.1;
            double newScale = scale;

            if (e.getDeltaY() < 0) {
                newScale /= zoom;
            }
            if (e.getDeltaY() > 0) {
                newScale *= zoom;
            }

            // Clamp the scale
            if (newScale < MIN_SCALE) newScale = MIN_SCALE;
            if (newScale > MAX_SCALE) newScale = MAX_SCALE;
            scale = newScale;

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

        // Calculate axis positions first so we can draw labels relative to them
        double yAxisPixel = (0 - graphCenterX) * scale + w / 2;
        double xAxisPixel = h / 2 - (0 - graphCenterY) * scale;

        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1);
        gc.setFill(Color.GRAY);
        gc.setFont(javafx.scene.text.Font.font(10));

        // Dynamic grid spacing: keep lines roughly 75 pixels apart
        double targetPixels = 100.0;
        double rawStep = targetPixels / scale;

        // Calculate the magnitude (power of 10)
        double exponent = Math.floor(Math.log10(rawStep));
        double magnitude = Math.pow(10, exponent);
        double fraction = rawStep / magnitude;

        // Snap to nice intervals: 1, 2, 5
        double niceFraction;
        int subdivisions;

        if (fraction < 2.0) {
            niceFraction = 1;
            subdivisions = 5;
        } else if (fraction < 5.0) {
            niceFraction = 2;
            subdivisions = 4;
        } else {
            niceFraction = 5;
            subdivisions = 5;
        }

        double majorStep = niceFraction * magnitude;
        double minorStep = majorStep / subdivisions;

        // --- Draw Minor Grid ---
        gc.setStroke(Color.web("#2A2A2A"));
        gc.setLineWidth(1);

        // Vertical Minor
        double startXMinor = (Math.floor(left / minorStep) * minorStep);
        for (double x = startXMinor; (x - startXMinor) * scale < w + scale; x += minorStep) {
            double pixelX = (x - graphCenterX) * scale + w / 2;
            gc.strokeLine(pixelX, 0, pixelX, h);
        }

        // Horizontal Minor
        double startYMinor = (Math.floor((graphCenterY - (h / 2) / scale) / minorStep) * minorStep);
        for (double y = startYMinor; (y - startYMinor) * scale < h + scale; y += minorStep) {
            double pixelY = h / 2 - (y - graphCenterY) * scale;
            gc.strokeLine(0, pixelY, w, pixelY);
        }

        // --- Draw Major Grid ---
        gc.setStroke(Color.web("#404040"));
        gc.setLineWidth(1);
        gc.setFill(Color.GRAY);

        // Draw Vertical lines (X-axis grid)
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.TOP);

        double startX = (Math.floor(left / majorStep) * majorStep);
        for (double x = startX; (x - startX) * scale < w + scale; x += majorStep) {
            double pixelX = (x - graphCenterX) * scale + w / 2;
            gc.strokeLine(pixelX, 0, pixelX, h);

            // Draw X-axis numbers (skip 0 to avoid overlap)
            if (Math.abs(x) > 1e-9) {
                gc.fillText(formatNumber(x), pixelX, xAxisPixel + 4);
            }
        }

        // Draw Horizontal lines (Y-axis grid)
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.CENTER);

        double startY = (Math.floor((graphCenterY - (h / 2) / scale) / majorStep) * majorStep);
        for (double y = startY; (y - startY) * scale < h + scale; y += majorStep) {
            double pixelY = h / 2 - (y - graphCenterY) * scale;
            gc.strokeLine(0, pixelY, w, pixelY);

            // Draw Y-axis numbers (skip 0)
            if (Math.abs(y) > 1e-9) {
                gc.fillText(formatNumber(y), yAxisPixel - 4, pixelY);
            }
        }

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);

        gc.strokeLine(yAxisPixel, 0, yAxisPixel, h);
        gc.strokeLine(0, xAxisPixel, w, xAxisPixel);

        // Draw Origin (0)
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.TOP);
        gc.fillText("0", yAxisPixel - 4, xAxisPixel + 4);
    }

    private String formatNumber(double d) {
        DecimalFormat df = new DecimalFormat("#.###");
        return df.format(d);
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
            double step = Math.max(1, scale / 50);  // slight optimization
            for (double pixelX = 0; pixelX < w; pixelX += step) {
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
    public void addEquationToHashmap(String id, String equation) {
        if (equation == null) return;

        EquationData data = new EquationData();
        data.raw = equation;
        data.parser = new EquationParser(equation);  // equation is parsed here

        currentEquations.put(id, data);
        draw();
    }

    public void removeEquation(String id) {
        currentEquations.remove(id);
        draw();
    }

    //graph buttons
    public void zoomIn() {
        scale = Math.min(scale * 1.1, MAX_SCALE);
        draw();
    }

    public void zoomOut() {
        scale = Math.max(scale / 1.1, MIN_SCALE);
        draw();
    }

    public void reset() {
        scale = 50;
        graphCenterX = 0;
        graphCenterY = 0;
        draw();
    }

    public int getEqCount() {
        return eqCount;
    }

    public void setEqCount(int eqCount) {
        this.eqCount = eqCount;
    }
}