package org.example.equation_plotter;

import javafx.animation.PauseTransition;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

//class EquationData {
//    String raw;
//    EquationParser parser;
//    Color color;
//    int r, g, b;
//
//    private double[] yCache;
//    private double step;
//    private double xStart;
//    private int startIndex;
//    private int size;
//
//    public void buildCacheExplicit(double visibleMinX, double visibleMaxX, double width) {
//        if (parser.isImplicit()) return;
//        double visibleWidth = visibleMaxX - visibleMinX;
//        double bufferWidth = visibleWidth * 3;
//        size = (int) (width * 3 * 2);
//        step = bufferWidth / size;
//        xStart = visibleMinX - visibleWidth;
//        yCache = new double[size];
//
//        // Use all CPU cores to calculate standard functions instantly
//        java.util.stream.IntStream.range(0, size).parallel().forEach(i -> {
//            double x = xStart + i * step;
//            yCache[i] = parser.evaluateExplicit(x);
//        });
//
//        startIndex = 0;
//    }
//
//    public double getY(double graphX) {
//        double fIndex = (graphX - xStart) / step;
//        int i0 = (int) Math.floor(fIndex);
//        int i1 = i0 + 1;
//        if (i0 < 0 || i1 >= size) return Double.NaN;
//        double y0 = yCache[(startIndex + i0) % size];
//        double y1 = yCache[(startIndex + i1) % size];
//        double t = fIndex - i0;
//        return y0 + t * (y1 - y0);
//    }
//
//    public void setColor(Color color) {
//        this.color = color;
//        this.r = (int) (color.getRed() * 255);
//        this.g = (int) (color.getGreen() * 255);
//        this.b = (int) (color.getBlue() * 255);
//    }
//}
//
//record Points(double x, double y, Color color) {
//    public double getX() {
//        return x;
//    }
//
//    public double getY() {
//        return y;
//    }
//
//    public Paint getColor() {
//        return color;
//    }
//}

public class GraphPlotter extends StackPane {
    // Grabs the exact number of logical threads your CPU possesses
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    // Creates a dedicated pool that uses ALL of them
    private static final ForkJoinPool MAX_POWER_POOL = new ForkJoinPool(CPU_CORES);
    private final Canvas gridCanvas;
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
    private final Canvas graphCanvas;
    private final Canvas overlayCanvas;
    // Cache Trackers for Implicit Functions
    private final Map<String, CachedImplicit> implicitCache = new HashMap<>();
    private final Map<String, javafx.concurrent.Task<?>> activeTasks = new HashMap<>();

    public GraphPlotter(double width, double height) {
        setPrefSize(width, height);

        // 1. Initialize the canvases
        gridCanvas = new Canvas(width, height);
        graphCanvas = new Canvas(width, height);
        overlayCanvas = new Canvas(width, height);

        // 2. Bind their sizes so they resize perfectly when the window resizes
        gridCanvas.widthProperty().bind(this.widthProperty());
        gridCanvas.heightProperty().bind(this.heightProperty());
        graphCanvas.widthProperty().bind(this.widthProperty());
        graphCanvas.heightProperty().bind(this.heightProperty());
        overlayCanvas.widthProperty().bind(this.widthProperty());
        overlayCanvas.heightProperty().bind(this.heightProperty());

        // 3. Add them to the StackPane (Order matters! Bottom to Top)
        getChildren().addAll(gridCanvas, graphCanvas, overlayCanvas);


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
            draw();
        });

        setOnScroll(e -> {
            isInteracting = true; // FIX: Prevent thread spam while scrolling
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
            isInteracting = false;
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

            // FIX: This line MUST be uncommented to prevent NPE lag while panning
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


    // Call this when you need to completely refresh everything (e.g., resizing, panning, zooming)
    public void draw() {
        drawGridLayer();
        drawGraphLayer();
        drawOverlayLayer();
    }

    private void drawGridLayer() {
        GraphicsContext gc = gridCanvas.getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        if (w == 0 || h == 0) return;

        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);

        drawGrid(gc, w, h); // Your existing drawGrid method
    }

    void drawGraphLayer() {
        GraphicsContext gc = graphCanvas.getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        if (w == 0 || h == 0) return;

        gc.clearRect(0, 0, w, h); // Clear only the math layer!

        drawFunction(gc, w, h); // Your existing drawFunction method

        // Draw manually added user points on the graph layer
        for (Points p : pointsMap.values()) {
            double px = (p.getX() - graphCenterX) * scale + w / 2;
            double py = h / 2 - (p.getY() - graphCenterY) * scale;
            gc.setFill(p.getColor());
            gc.fillOval(px - 4, py - 4, 8, 8);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1);
            gc.strokeOval(px - 4, py - 4, 8, 8);
        }
    }

    // This is the MAGIC. This layer clears and draws instantly without touching math.
    private void drawOverlayLayer() {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        if (w == 0 || h == 0) return;

        gc.clearRect(0, 0, w, h); // Erase the old hover states

        // Draw pinned points
        for (Point2D p : selectedPoints) {
            drawPointMarker(gc, p, Color.web("#FEFEFA"));
        }

        // Draw neon indicators for special points
        for (Point2D ip : intersectionPoints) {
            drawSmallIndicator(gc, ip, Color.web("#444444"));
        }
        for (Point2D ip : interceptPoints) {
            drawSmallIndicator(gc, ip, Color.web("#666666"));
        }

        // Coordinate label for hover
        if (isMouseDown && isHovering && hoverPoint != null) {
            drawPointMarker(gc, hoverPoint, hoverColor);
        }
    }

    private void drawPointMarker(GraphicsContext gc, Point2D p, Color color) {
        double px = (p.getX() - graphCenterX) * scale + getWidth() / 2;
        double py = getHeight() / 2 - (p.getY() - graphCenterY) * scale;

        gc.setFill(color);
        gc.fillOval(px - 5, py - 5, 10, 10);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeOval(px - 5, py - 5, 10, 10);

        String label = "(" + formatNumber(p.getX()) + ", " + formatNumber(p.getY()) + ")";
        gc.setFont(javafx.scene.text.Font.font("JetBrains Mono", 13));

        double textWidth = label.length() * 8.0;
        double textHeight = 15;
        double padding = 8;

        double boxX = px + 20 - (textWidth / 2) - (padding / 2);
        double boxY = py + 20 - (textHeight / 2) - (padding / 2);

        gc.setFill(Color.web("#1e1e1e", 0.9));
        gc.setStroke(color);
        gc.setLineWidth(1.5);
        gc.fillRoundRect(boxX, boxY, textWidth + padding, textHeight + padding, 5, 5);

        gc.setFill(Color.WHITE);
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        gc.fillText(label, px + 20, py + 20 + 2);
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
        for (Map.Entry<String, EquationData> entry : currentEquations.entrySet()) {
            String id = entry.getKey();
            EquationData equation = entry.getValue();

            if (equation.parser.isImplicit()) {
                drawFunction_MarchingSquares(gc, w, h, equation.parser, equation, id);
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

    private void drawFunction_MarchingSquares(GraphicsContext gc, double w, double h, EquationParser mainParser, EquationData data, String id) {
        // --- 1. CHECK CACHE FOR INSTANT PANNING ---
        if (implicitCache.containsKey(id)) {
            CachedImplicit cache = implicitCache.get(id);

            gc.setStroke(data.color);
            gc.setLineWidth(2.5);

            for (double[] line : cache.lines) {
                double px1 = (line[0] - graphCenterX) * scale + w / 2.0;
                double py1 = h / 2.0 - (line[1] - graphCenterY) * scale;
                double px2 = (line[2] - graphCenterX) * scale + w / 2.0;
                double py2 = h / 2.0 - (line[3] - graphCenterY) * scale;

                if ((px1 > -100 && px1 < w + 100 && py1 > -100 && py1 < h + 100) ||
                        (px2 > -100 && px2 < w + 100 && py2 > -100 && py2 < h + 100)) {
                    gc.strokeLine(px1, py1, px2, py2);
                }
            }

            boolean scaleChanged = cache.scale != scale;
            boolean pannedOutOfBounds = Math.abs(graphCenterX - cache.cx) > (w / scale) * 0.1 ||
                    Math.abs(graphCenterY - cache.cy) > (h / scale) * 0.1;

            if (!scaleChanged && !pannedOutOfBounds) return;
            if (isInteracting) return;
        }

        // --- 2. CANCEL OLD TASKS ---
        if (activeTasks.containsKey(id)) {
            activeTasks.get(id).cancel(true);
        }

        final double viewCx = graphCenterX;
        final double viewCy = graphCenterY;
        final double viewScale = scale;

        // --- 3. FAST PROGRESSIVE RENDER (PREVIEW) ---
        int coarseStep = 20;
        int coarseCols = (int) w / coarseStep + 1;
        int coarseRows = (int) h / coarseStep + 1;
        double[][] coarseVals = new double[coarseCols][coarseRows];

        // Since AST is completely thread-safe, we no longer need ThreadLocal clones!
        IntStream.range(0, coarseCols * coarseRows).parallel().forEach(i -> {
            int c = i % coarseCols;
            int r = i / coarseCols;
            double gx = viewCx + (c * coarseStep - w / 2.0) / viewScale;
            double gy = viewCy + (h / 2.0 - r * coarseStep) / viewScale;
            coarseVals[c][r] = mainParser.evaluateImplicit(gx, gy);
        });

        gc.setStroke(data.color.deriveColor(0, 1, 1, 0.4));
        gc.setLineWidth(4.0);

        for (int r = 0; r < coarseRows - 1; r++) {
            for (int c = 0; c < coarseCols - 1; c++) {
                double vtl = coarseVals[c][r], vtr = coarseVals[c + 1][r];
                double vbl = coarseVals[c][r + 1], vbr = coarseVals[c + 1][r + 1];

                int state = 0;
                if (vtl > 0) state |= 8;
                if (vtr > 0) state |= 4;
                if (vbr > 0) state |= 2;
                if (vbl > 0) state |= 1;

                if (state == 0 || state == 15) continue;

                double topX = c * coarseStep + coarseStep * interp(vtl, vtr);
                double topY = r * coarseStep;
                double botX = c * coarseStep + coarseStep * interp(vbl, vbr);
                double botY = (r + 1) * coarseStep;
                double leftX = c * coarseStep;
                double leftY = r * coarseStep + coarseStep * interp(vtl, vbl);
                double rightX = (c + 1) * coarseStep;
                double rightY = r * coarseStep + coarseStep * interp(vtr, vbr);

                switch (state) {
                    case 1:
                    case 14:
                        gc.strokeLine(leftX, leftY, botX, botY);
                        break;
                    case 2:
                    case 13:
                        gc.strokeLine(botX, botY, rightX, rightY);
                        break;
                    case 4:
                    case 11:
                        gc.strokeLine(topX, topY, rightX, rightY);
                        break;
                    case 8:
                    case 7:
                        gc.strokeLine(leftX, leftY, topX, topY);
                        break;
                    case 3:
                    case 12:
                        gc.strokeLine(leftX, leftY, rightX, rightY);
                        break;
                    case 6:
                    case 9:
                        gc.strokeLine(topX, topY, botX, botY);
                        break;
                    case 5:
                        gc.strokeLine(leftX, leftY, topX, topY);
                        gc.strokeLine(botX, botY, rightX, rightY);
                        break;
                    case 10:
                        gc.strokeLine(topX, topY, rightX, rightY);
                        gc.strokeLine(leftX, leftY, botX, botY);
                        break;
                }
            }
        }

        // --- 4. HIGH-RES ADAPTIVE BACKGROUND CALCULATION ---
        final double fineStep = 1.5 / viewScale; // 1.5-pixel HD resolution (Fixed jagged edges)
        final double coarseStepMath = 15.0 / viewScale;
        final double areaMultiplier = 1.2;
        final double viewWidthMath = w / viewScale;
        final double viewHeightMath = h / viewScale;

        final double startX = viewCx - (viewWidthMath * areaMultiplier) / 2.0;
        final double startY = viewCy + (viewHeightMath * areaMultiplier) / 2.0;

        final int mathCoarseCols = (int) ((viewWidthMath * areaMultiplier) / coarseStepMath) + 1;
        final int mathCoarseRows = (int) ((viewHeightMath * areaMultiplier) / coarseStepMath) + 1;

        javafx.concurrent.Task<List<double[]>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<double[]> call() {
                double[][] mathCoarseVals = new double[mathCoarseCols][mathCoarseRows];

                // 4A. Evaluate coarse grid
                IntStream.range(0, mathCoarseCols * mathCoarseRows).parallel().forEach(i -> {
                    if (isCancelled()) return;
                    int c = i % mathCoarseCols;
                    int r = i / mathCoarseCols;
                    double gx = startX + c * coarseStepMath;
                    double gy = startY - r * coarseStepMath;
                    mathCoarseVals[c][r] = mainParser.evaluateImplicit(gx, gy);
                });

                if (isCancelled()) return null;

                // Use synchronized list because we are parallelizing the line construction
                List<double[]> lines = Collections.synchronizedList(new ArrayList<>());
                int subdivisions = 10; // 15 / 1.5 = 10 cells per coarse box

                // 4B. FULLY PARALLELIZED High-Res Refinement
                IntStream.range(0, (mathCoarseRows - 1) * (mathCoarseCols - 1)).parallel().forEach(i -> {
                    if (isCancelled()) return;
                    int c = i % (mathCoarseCols - 1);
                    int r = i / (mathCoarseCols - 1);

                    double vtl = mathCoarseVals[c][r], vtr = mathCoarseVals[c + 1][r];
                    double vbl = mathCoarseVals[c][r + 1], vbr = mathCoarseVals[c + 1][r + 1];

                    int state = 0;
                    if (vtl > 0) state |= 8;
                    if (vtr > 0) state |= 4;
                    if (vbr > 0) state |= 2;
                    if (vbl > 0) state |= 1;

                    if (state == 0 || state == 15) return; // Skip empty boxes

                    double boxStartX = startX + c * coarseStepMath;
                    double boxStartY = startY - r * coarseStepMath;

                    double[][] fineVals = new double[subdivisions + 1][subdivisions + 1];
                    fineVals[0][0] = vtl;
                    fineVals[subdivisions][0] = vtr;
                    fineVals[0][subdivisions] = vbl;
                    fineVals[subdivisions][subdivisions] = vbr;

                    for (int fr = 0; fr <= subdivisions; fr++) {
                        for (int fc = 0; fc <= subdivisions; fc++) {
                            if ((fr == 0 && fc == 0) || (fr == 0 && fc == subdivisions) ||
                                    (fr == subdivisions && fc == 0) || (fr == subdivisions && fc == subdivisions)) {
                                continue;
                            }
                            double fx = boxStartX + fc * fineStep;
                            double fy = boxStartY - fr * fineStep;
                            fineVals[fc][fr] = mainParser.evaluateImplicit(fx, fy);
                        }
                    }

                    List<double[]> localLines = new ArrayList<>();
                    for (int fr = 0; fr < subdivisions; fr++) {
                        for (int fc = 0; fc < subdivisions; fc++) {
                            double fvtl = fineVals[fc][fr], fvtr = fineVals[fc + 1][fr];
                            double fvbl = fineVals[fc][fr + 1], fvbr = fineVals[fc + 1][fr + 1];

                            int fstate = 0;
                            if (fvtl > 0) fstate |= 8;
                            if (fvtr > 0) fstate |= 4;
                            if (fvbr > 0) fstate |= 2;
                            if (fvbl > 0) fstate |= 1;

                            if (fstate == 0 || fstate == 15) continue;

                            double ftopX = boxStartX + (fc + interp(fvtl, fvtr)) * fineStep;
                            double ftopY = boxStartY - fr * fineStep;
                            double fbotX = boxStartX + (fc + interp(fvbl, fvbr)) * fineStep;
                            double fbotY = boxStartY - (fr + 1) * fineStep;
                            double fleftX = boxStartX + fc * fineStep;
                            double fleftY = boxStartY - (fr + interp(fvtl, fvbl)) * fineStep;
                            double frightX = boxStartX + (fc + 1) * fineStep;
                            double frightY = boxStartY - (fr + interp(fvtr, fvbr)) * fineStep;

                            switch (fstate) {
                                case 1:
                                case 14:
                                    localLines.add(new double[]{fleftX, fleftY, fbotX, fbotY});
                                    break;
                                case 2:
                                case 13:
                                    localLines.add(new double[]{fbotX, fbotY, frightX, frightY});
                                    break;
                                case 4:
                                case 11:
                                    localLines.add(new double[]{ftopX, ftopY, frightX, frightY});
                                    break;
                                case 8:
                                case 7:
                                    localLines.add(new double[]{fleftX, fleftY, ftopX, ftopY});
                                    break;
                                case 3:
                                case 12:
                                    localLines.add(new double[]{fleftX, fleftY, frightX, frightY});
                                    break;
                                case 6:
                                case 9:
                                    localLines.add(new double[]{ftopX, ftopY, fbotX, fbotY});
                                    break;
                                case 5:
                                    localLines.add(new double[]{fleftX, fleftY, ftopX, ftopY});
                                    localLines.add(new double[]{fbotX, fbotY, frightX, frightY});
                                    break;
                                case 10:
                                    localLines.add(new double[]{ftopX, ftopY, frightX, frightY});
                                    localLines.add(new double[]{fleftX, fleftY, fbotX, fbotY});
                                    break;
                            }
                        }
                    }
                    lines.addAll(localLines);
                });

                return lines;
            }
        };

        task.setOnSucceeded(e -> {
            List<double[]> result = task.getValue();
            if (result == null) return;
            implicitCache.put(id, new CachedImplicit(result, viewScale, viewCx, viewCy));
            activeTasks.remove(id);
            drawGraphLayer();
        });

        activeTasks.put(id, task);
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private double interp(double v1, double v2) {
        if (Double.isNaN(v1) || Double.isNaN(v2)) return 0.5;
        double sum = Math.abs(v1) + Math.abs(v2);
        if (sum == 0.0) return 0.5;
        return Math.abs(v1) / sum;
    }

    public void addEquationToHashmap(String id, String fullInput, Color color) {
        EquationData data = new EquationData();
        data.raw = fullInput;
        data.parser = new EquationParser(fullInput);
        data.setColor(color);
        pointsMap.remove(id);

        // --- FIX: CLEAR THE CACHE WHEN EQUATION CHANGES ---
        implicitCache.remove(id);
        if (activeTasks.containsKey(id)) {
            activeTasks.get(id).cancel(true);
            activeTasks.remove(id);
        }
        // --------------------------------------------------

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
        implicitCache.remove(id); // Clear cache if equation is removed
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
        implicitCache.clear();
        draw();
    }

    public EquationData getEquation(String id) {
        return currentEquations.get(id);
    }
}

//class CachedImplicit {
//    List<double[]> lines;
//    double scale;
//    double cx, cy;
//
//    public CachedImplicit(List<double[]> lines, double scale, double cx, double cy) {
//        this.lines = lines;
//        this.scale = scale;
//        this.cx = cx;
//        this.cy = cy;
//    }
//}