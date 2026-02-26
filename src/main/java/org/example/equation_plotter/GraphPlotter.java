package org.example.equation_plotter;

import javafx.animation.PauseTransition;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.IntStream;

class EquationData {
    String raw;
    EquationParser parser;
    Color color;
    int r, g, b;

    private double[] yCache;
    private double step;
    private double xStart;
    private int startIndex;
    private int size;

    public void buildCacheExplicit(double visibleMinX, double visibleMaxX, double width) {
        if (parser.isImplicit()) return;
        double visibleWidth = visibleMaxX - visibleMinX;
        double bufferWidth = visibleWidth * 3;
        size = (int) (width * 3 * 2);
        step = bufferWidth / size;
        xStart = visibleMinX - visibleWidth;
        yCache = new double[size];
        for (int i = 0; i < size; i++) {
            double x = xStart + i * step;
            yCache[i] = parser.evaluateExplicit(x);
        }
        startIndex = 0;
    }

    public double getY(double graphX) {
        double fIndex = (graphX - xStart) / step;
        int i0 = (int) Math.floor(fIndex);
        int i1 = i0 + 1;
        if (i0 < 0 || i1 >= size) return Double.NaN;
        double y0 = yCache[(startIndex + i0) % size];
        double y1 = yCache[(startIndex + i1) % size];
        double t = fIndex - i0;
        return y0 + t * (y1 - y0);
    }

    public void setColor(Color color) {
        this.color = color;
        this.r = (int) (color.getRed() * 255);
        this.g = (int) (color.getGreen() * 255);
        this.b = (int) (color.getBlue() * 255);
    }
}

class Points {
    private final double x;
    private final double y;
    private final Color color;

    public Points(double x, double y, Color color) {
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Color getColor() {
        return color;
    }
}

public class GraphPlotter extends Canvas {
    private double graphCenterX = 0;
    private double graphCenterY = 0;
    private double scale = 50;
    private static final double MIN_SCALE = 1.0e-2;
    private static final double MAX_SCALE = 1.0e5;

    private double prevMouseX;
    private double prevMouseY;
    private final PauseTransition scrollEndTimer = new PauseTransition(Duration.millis(120));

    private boolean isInteracting = false;
    private final Map<String, EquationData> currentEquations = new HashMap<>();
    private final Map<String, Points> pointsMap = new HashMap<>();
    // Interaction States
    private boolean isMouseDown = false;
    private boolean isHovering = false;
    private boolean isSnapPoint = false;

    private Point2D hoverPoint = null;
    private Color hoverColor = Color.CYAN;
    private final List<Point2D> intersectionPoints = new ArrayList<>();
    private final List<Point2D> interceptPoints = new ArrayList<>();
    private final Set<Point2D> selectedPoints = new LinkedHashSet<>();
    private static final double SNAP_THRESHOLD_PX = 30.0;

    public GraphPlotter(double width, double height) {
        super(width, height);
        widthProperty().addListener(e -> draw());
        heightProperty().addListener(e -> draw());

        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                isMouseDown = true;
                updateHoverState(e.getX(), e.getY());

                // Snap points (intersections/intercepts) can be pinned
                if (hoverPoint != null && isSnapPoint) {
                    togglePointSelection(hoverPoint);
                } else if (e.getClickCount() == 2) {
                    selectedPoints.clear();
                }
                prevMouseX = e.getX();
                prevMouseY = e.getY();
                getScene().setCursor(javafx.scene.Cursor.CLOSED_HAND);
                draw();
            }
        });

        setOnMouseMoved(e -> {
            updateHoverState(e.getX(), e.getY());
            // Show HAND cursor only for snappable points
            getScene().setCursor(isSnapPoint ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
            draw();
        });

        setOnMouseDragged(e -> {
            isInteracting = true;
            if (e.getButton() == MouseButton.PRIMARY) {
                updateHoverState(e.getX(), e.getY());

                // Only pan if we aren't hovering over a specific coordinate point
                if (!isHovering) {
                    double dx = (e.getX() - prevMouseX) / scale;
                    double dy = (e.getY() - prevMouseY) / scale;
                    graphCenterX -= dx;
                    graphCenterY += dy;
                }
                prevMouseX = e.getX();
                prevMouseY = e.getY();
                draw();
            }
        });

        setOnMouseReleased(e -> {
            isMouseDown = false;
            isInteracting = false;
            refreshAllData();
            getScene().setCursor(isSnapPoint ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
            draw();
        });

        setOnScroll(e -> {
            double mouseX = e.getX();
            double mouseY = e.getY();
            double prevScale = scale;
            double graphX = graphCenterX + (mouseX - getWidth() / 2) / prevScale;
            double graphY = graphCenterY + (getHeight() / 2 - mouseY) / prevScale;

            double zoom = 1.1;
            double newScale = (e.getDeltaY() < 0) ? scale / zoom : scale * zoom;
            scale = Math.clamp(newScale, MIN_SCALE, MAX_SCALE);

            graphCenterX = graphX - (mouseX - getWidth() / 2) / scale;
            graphCenterY = graphY - (getHeight() / 2 - mouseY) / scale;

            scrollEndTimer.playFromStart();
            draw();
        });

        scrollEndTimer.setOnFinished(e -> {
            refreshAllData();
            draw();
        });
    }

    private void togglePointSelection(Point2D point) {
        boolean removed = selectedPoints.removeIf(p -> p.distance(point) < 0.01);
        if (!removed) {
            selectedPoints.add(point);
        }
    }

    private void updateHoverState(double mouseX, double mouseY) {
        double gx = graphCenterX + (mouseX - getWidth() / 2) / scale;
        double gy = graphCenterY + (getHeight() / 2 - mouseY) / scale;
        double threshold = SNAP_THRESHOLD_PX / scale;

        hoverPoint = null;
        isHovering = false;
        isSnapPoint = false;

        // 1. Priority: Intersections
        for (Point2D ip : intersectionPoints) {
            if (ip.distance(gx, gy) < threshold) {
                hoverPoint = ip;
                hoverColor = Color.YELLOW;
                isSnapPoint = true;
                isHovering = true;
                return;
            }
        }

        // 2. Priority: Intercepts
        for (Point2D ip : interceptPoints) {
            if (ip.distance(gx, gy) < threshold) {
                hoverPoint = ip;
                hoverColor = Color.LIGHTGRAY;
                isSnapPoint = true;
                isHovering = true;
                return;
            }
        }

        // 3. Smooth Curve tracking
        double bestDist = Double.MAX_VALUE;
        for (EquationData eq : currentEquations.values()) {
            if (eq.parser.isImplicit()) continue;

            double cy = eq.getY(gx);
            if (!Double.isNaN(cy)) {
                double dist = Math.abs(cy - gy);
                if (dist < threshold && dist < bestDist) {
                    bestDist = dist;
                    hoverPoint = new Point2D(gx, cy);
                    hoverColor = eq.color;
                    isHovering = true;
                }
            }
        }
    }

    public void refreshAllData() {
        double graphMinX = graphCenterX - (getWidth() / 2) / scale;
        double graphMaxX = graphCenterX + (getWidth() / 2) / scale;

        for (EquationData equation : currentEquations.values()) {
            if (!equation.parser.isImplicit()) {
                equation.buildCacheExplicit(graphMinX, graphMaxX, getWidth());
            }
        }
        updateIntersections();
        updateIntercepts();
    }

    public void refreshEquationData(String id) {
        double graphMinX = graphCenterX - (getWidth() / 2) / scale;
        double graphMaxX = graphCenterX + (getWidth() / 2) / scale;

        EquationData equation = currentEquations.get(id);
        if (!equation.parser.isImplicit()) {
            equation.buildCacheExplicit(graphMinX, graphMaxX, getWidth());
        }

        updateIntersections();
        updateIntercepts();
    }


    private void updateIntersections() {
        intersectionPoints.clear();
        List<EquationData> equations = new ArrayList<>(currentEquations.values());
        double xMin = graphCenterX - (getWidth() / 2) / scale;
        double xMax = graphCenterX + (getWidth() / 2) / scale;
        double scanStep = 2.0 / scale;

        for (int i = 0; i < equations.size(); i++) {
            EquationData e1 = equations.get(i);
            if (e1.parser.isImplicit()) continue;
            for (int j = i + 1; j < equations.size(); j++) {
                EquationData e2 = equations.get(j);
                if (e2.parser.isImplicit()) continue;

                double prevX = xMin;
                double prevDiff = e1.getY(prevX) - e2.getY(prevX);

                for (double x = xMin + scanStep; x <= xMax; x += scanStep) {
                    double d1 = e1.getY(x);
                    double d2 = e2.getY(x);
                    if (Double.isNaN(d1) || Double.isNaN(d2)) continue;

                    double diff = d1 - d2;
                    if (prevDiff * diff <= 0 && !Double.isNaN(prevDiff)) {
                        double t = Math.abs(prevDiff) / (Math.abs(prevDiff) + Math.abs(diff));
                        double ix = prevX + t * (x - prevX);
                        intersectionPoints.add(new Point2D(ix, e1.getY(ix)));
                    }
                    prevX = x;
                    prevDiff = diff;
                }
            }
        }
    }

    private void updateIntercepts() {
        interceptPoints.clear();
        double xMin = graphCenterX - (getWidth() / 2) / scale;
        double xMax = graphCenterX + (getWidth() / 2) / scale;
        double scanStep = 2.0 / scale;

        for (EquationData eq : currentEquations.values()) {
            if (eq.parser.isImplicit()) continue;

            // Y-Intercept
            if (xMin <= 0 && xMax >= 0) {
                double yVal = eq.getY(0);
                if (!Double.isNaN(yVal)) interceptPoints.add(new Point2D(0, yVal));
            }

            // X-Intercepts
            double prevX = xMin;
            double prevY = eq.getY(prevX);
            for (double x = xMin + scanStep; x <= xMax; x += scanStep) {
                double y = eq.getY(x);
                if (Double.isNaN(y)) {
                    prevX = x;
                    prevY = y;
                    continue;
                }

                if (prevY * y <= 0 && !Double.isNaN(prevY)) {
                    double t = Math.abs(prevY) / (Math.abs(prevY) + Math.abs(y));
                    interceptPoints.add(new Point2D(prevX + t * (x - prevX), 0));
                }
                prevX = x;
                prevY = y;
            }
        }
    }

    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);

        drawGrid(gc, w, h);
        drawFunction(gc, w, h);

        // Draw manually added points
        for (Points p : pointsMap.values()) {
            double px = (p.getX() - graphCenterX) * scale + w / 2;
            double py = h / 2 - (p.getY() - graphCenterY) * scale;

            gc.setFill(p.getColor());
            gc.fillOval(px - 4, py - 4, 8, 8); // Slightly larger for visibility
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1);
            gc.strokeOval(px - 4, py - 4, 8, 8);
        }


        // Draw pinned points
        for (Point2D p : selectedPoints) {
            drawPointMarker(gc, p, Color.web("#FEFEFA"));
        }

        // Draw neon indicators for special points (subtle guides)
        for (Point2D ip : intersectionPoints) {
            drawSmallIndicator(gc, ip, Color.web("#444444"));
        }
        for (Point2D ip : interceptPoints) {
            drawSmallIndicator(gc, ip, Color.web("#666666"));
        }

        // Coordinate label: Only visible when actively clicking/holding on a curve
        if (isMouseDown && isHovering && hoverPoint != null) {
            drawPointMarker(gc, hoverPoint, hoverColor);
        }
    }

    private void drawPointMarker(GraphicsContext gc, Point2D p, Color color) {
        double px = (p.getX() - graphCenterX) * scale + getWidth() / 2;
        double py = getHeight() / 2 - (p.getY() - graphCenterY) * scale;

        // Main Point
        gc.setFill(color);
        gc.fillOval(px - 5, py - 5, 10, 10);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeOval(px - 5, py - 5, 10, 10);

        String label = "(" + formatNumber(p.getX()) + ", " + formatNumber(p.getY()) + ")";
        gc.setFont(javafx.scene.text.Font.font("JetBrains Mono", 13));

        // Calculate box dimensions
        double textWidth = label.length() * 8.0; // Estimate for Monospace font
        double textHeight = 15;
        double padding = 8;

        // Position the box (relative to your px + 20, py + 20)
        double boxX = px + 20 - (textWidth / 2) - (padding / 2);
        double boxY = py + 20 - (textHeight / 2) - (padding / 2);

        // Draw Background Box
        gc.setFill(Color.web("#1e1e1e", 0.9)); // Matching your UI background with slight transparency
        gc.setStroke(color); // Neon border matching the point color
        gc.setLineWidth(1.5);
        gc.fillRoundRect(boxX, boxY, textWidth + padding, textHeight + padding, 5, 5);
        //gc.strokeRoundRect(boxX, boxY, textWidth + padding, textHeight + padding, 5, 5);

        // Draw Text
        gc.setFill(Color.WHITE);
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        gc.fillText(label, px + 20, py + 20 + 2); // Small vertical adjustment for baseline
    }

    private void drawSmallIndicator(GraphicsContext gc, Point2D p, Color color) {
        double px = (p.getX() - graphCenterX) * scale + getWidth() / 2;
        double py = getHeight() / 2 - (p.getY() - graphCenterY) * scale;
        gc.setFill(color);
        gc.fillOval(px - 3, py - 3, 6, 6);
    }

    private void drawGrid(GraphicsContext gc, double w, double h) {
        double left = graphCenterX - w / 2 / scale;
        double yAxisPixel = (0 - graphCenterX) * scale + w / 2;
        double xAxisPixel = h / 2 - (0 - graphCenterY) * scale;
        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1);
        gc.setFill(Color.GRAY);
        gc.setFont(javafx.scene.text.Font.font("JetBrains Mono", 12));

        double targetPixels = 100.0;
        double rawStep = targetPixels / scale;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double fraction = rawStep / magnitude;

        double majorStep = (fraction < 2.0) ? 1 * magnitude : (fraction < 5.0) ? 2 * magnitude : 5 * magnitude;
        double minorStep = majorStep / 5.0;

        gc.setStroke(Color.web("#2A2A2A"));
        for (double x = Math.floor(left / minorStep) * minorStep; (x - left) * scale < w + scale; x += minorStep) {
            double px = (x - graphCenterX) * scale + w / 2;
            gc.strokeLine(px, 0, px, h);
        }
        for (double y = Math.floor((graphCenterY - h / 2 / scale) / minorStep) * minorStep; (y - (graphCenterY - h / 2 / scale)) * scale < h + scale; y += minorStep) {
            double py = h / 2 - (y - graphCenterY) * scale;
            gc.strokeLine(0, py, w, py);
        }
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setStroke(Color.web("#404040"));
        for (double x = Math.floor(left / majorStep) * majorStep; (x - left) * scale < w + scale; x += majorStep) {
            double px = (x - graphCenterX) * scale + w / 2;
            gc.strokeLine(px, 0, px, h);
            if (Math.abs(x) > 1e-9) {
                gc.setFill(Color.WHITE);
                double labelY = Math.clamp(xAxisPixel + 15, 0, Math.max(0, h - 20));
                gc.fillText(formatNumber(x), px, labelY);
            }
        }
        for (double y = Math.floor((graphCenterY - h / 2 / scale) / majorStep) * majorStep; (y - (graphCenterY - h / 2 / scale)) * scale < h + scale; y += majorStep) {
            double py = h / 2 - (y - graphCenterY) * scale;
            gc.strokeLine(0, py, w, py);
            if (Math.abs(y) > 1e-9) {
                gc.setFill(Color.WHITE);
                double labelX = Math.clamp(yAxisPixel - 15, Math.min(45, w), Math.max(45, w - 5));
                gc.fillText(formatNumber(y), labelX, py);
            }
        }

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeLine(yAxisPixel, 0, yAxisPixel, h);
        gc.strokeLine(0, xAxisPixel, w, xAxisPixel);
        gc.fillText("0", yAxisPixel - 10, xAxisPixel + 15);
    }

    private String formatNumber(double d) {
        return new DecimalFormat("#.##").format(d);
    }

    private void drawFunction(GraphicsContext gc, double w, double h) {
        for (EquationData equation : currentEquations.values()) {
            if (equation == null) continue;
            if (equation.parser.isImplicit()) {
                drawFunction_Implicit(gc, w, h, equation.parser, equation);
            } else {
                drawFunction_Explicit(gc, w, h, equation);
            }
        }
    }

    private void drawFunction_Explicit(GraphicsContext gc, double w, double h, EquationData data) {
        gc.beginPath();
        gc.setStroke(data.color);
        gc.setLineWidth(2.5);
        boolean firstPoint = true;
        for (double pixelX = 0; pixelX < w; pixelX += 1.0) {
            double graphX = graphCenterX + (pixelX - w / 2.0) / scale;
            double graphY = data.getY(graphX);
            if (Double.isNaN(graphY) || Double.isInfinite(graphY)) {
                firstPoint = true;
                continue;
            }
            double pixelY = h / 2.0 - (graphY - graphCenterY) * scale;
            if (firstPoint) {
                gc.moveTo(pixelX, pixelY);
                firstPoint = false;
            } else {
                gc.lineTo(pixelX, pixelY);
            }
        }
        gc.stroke();
    }

    private void drawFunction_Implicit(GraphicsContext gc, double w, double h, EquationParser mainParser, EquationData data) {
        int width = (int) w, height = (int) h;
        int[] buffer = new int[width * height];
        ThreadLocal<EquationParser> threadParser = ThreadLocal.withInitial(mainParser::cloneForThread);
        int tileSize = 32;
        int numTilesX = (int) Math.ceil(w / tileSize), numTilesY = (int) Math.ceil(h / tileSize);
        IntStream.range(0, numTilesX * numTilesY).parallel().forEach(i -> {
            int tileX = (i % numTilesX) * tileSize, tileY = (i / numTilesX) * tileSize;
            recursivePlot(buffer, tileX, tileY, tileSize, width, height, graphCenterX, graphCenterY, scale, threadParser.get(), data.r, data.g, data.b, 0);
        });
        WritableImage img = new WritableImage(width, height);
        img.getPixelWriter().setPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), buffer, 0, width);
        gc.drawImage(img, 0, 0);
    }

    private void recursivePlot(int[] buffer, int x, int y, int size, int w, int h, double cx, double cy, double sc, EquationParser parser, int r, int g, int b, int depth) {
        if (depth > 20 || x >= w || y >= h || x + size < 0 || y + size < 0) return;
        int minSize = isInteracting ? 4 : 1;
        if (size <= minSize) {
            double gx = cx + (x + size / 2.0 - w / 2.0) / sc;
            double gy = cy + (h / 2.0 - (y + size / 2.0)) / sc;
            double val = parser.evaluateImplicit(gx, gy);
            if (!Double.isNaN(val) && Math.abs(val) < 15.0 * size / sc) {
                int argb = (255 << 24) | (r << 16) | (g << 8) | b;
                for (int dy = 0; dy < size; dy++)
                    for (int dx = 0; dx < size; dx++) {
                        int px = x + dx, py = y + dy;
                        if (px >= 0 && px < w && py >= 0 && py < h) buffer[py * w + px] = argb;
                    }
            }
            return;
        }
        int half = size / 2;
        recursivePlot(buffer, x, y, half, w, h, cx, cy, sc, parser, r, g, b, depth + 1);
        recursivePlot(buffer, x + half, y, half, w, h, cx, cy, sc, parser, r, g, b, depth + 1);
        recursivePlot(buffer, x, y + half, half, w, h, cx, cy, sc, parser, r, g, b, depth + 1);
        recursivePlot(buffer, x + half, y + half, half, w, h, cx, cy, sc, parser, r, g, b, depth + 1);
    }

    public void addEquationToHashmap(String id, String fullInput, Color color) {
        EquationData data = new EquationData();
        data.raw = fullInput;
        data.parser = new EquationParser(fullInput);
        data.setColor(color);
        pointsMap.remove(id);
        if (data.parser.getPoints() != null) {
            Points p = data.parser.getPoints();
            pointsMap.put(id, new Points(p.getX(), p.getY(), color));
        } else {
            currentEquations.put(id, data);
            refreshAllData();
        }
        draw();
    }

    public void removeEquation(String id) {
        currentEquations.remove(id);
        refreshAllData();
        pointsMap.remove(id);
        draw();
    }

    public void zoomIn() {
        scale = Math.min(scale * 1.1, MAX_SCALE);
        draw();
    }

    public void zoomOut() {
        scale = Math.max(scale / 1.1, MIN_SCALE);
        draw();
    }

    public void reset() {
        graphCenterX = 0;
        graphCenterY = 0;
        scale = 50;
        refreshAllData();
        draw();
    }

    public void updateEqColor(String id, Color color) {
        EquationData data = currentEquations.get(id);
        if (data != null) {
            data.setColor(color);
            draw();
        }
    }

    public void clearAllEquations() {
        currentEquations.clear();
        pointsMap.clear();
        draw();
    }

    public EquationData getEquation(String id) {
        return currentEquations.get(id);
    }

}