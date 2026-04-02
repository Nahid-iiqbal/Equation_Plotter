package org.example.equation_plotter;
// ignore this commit
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

public class GraphPlotter extends StackPane {
    private final Canvas gridCanvas;
    private double graphCenterX = 0;
    private double graphCenterY = 0;
    private double scale = 50;
    private static final double MIN_SCALE = 1.0e-2;
    private static final double MAX_SCALE = 1.0e5;
    private static GraphPlotter mainInstance;
    private double prevMouseX;
    private double prevMouseY;
    private final PauseTransition scrollEndTimer = new PauseTransition(Duration.millis(120));
    public boolean isParameterAnimating = false;
    private boolean isInteracting = false;
    private final Map<String, EquationData> currentEquations = new HashMap<>();
    private final Map<String, Points> pointsMap = new HashMap<>();
    private boolean isMouseDown = false;
    private boolean isHovering = false;
    private boolean isSnapPoint = false;
    private boolean polarGrid = false;
    private Runnable postDrawAction = null;
    private Point2D hoverPoint = null;
    private Color hoverColor = Color.CYAN;
    private final List<Point2D> intersectionPoints = new ArrayList<>();
    private final List<Point2D> interceptPoints = new ArrayList<>();
    private final Set<Point2D> selectedPoints = new LinkedHashSet<>();
    private static final double SNAP_THRESHOLD_PX = 30.0;
    private final Canvas graphCanvas;
    private final Canvas overlayCanvas;
    private final Map<String, CachedImplicit> implicitCache = new HashMap<>();
    private final Map<String, javafx.concurrent.Task<?>> activeTasks = new HashMap<>();
    public boolean isLightMode = false;


    private boolean isDirty = true;

    public GraphPlotter(double width, double height) {
        setPrefSize(width, height);

        gridCanvas = new Canvas(width, height);
        graphCanvas = new Canvas(width, height);
        overlayCanvas = new Canvas(width, height);

        gridCanvas.widthProperty().bind(this.widthProperty());
        gridCanvas.heightProperty().bind(this.heightProperty());
        graphCanvas.widthProperty().bind(this.widthProperty());
        graphCanvas.heightProperty().bind(this.heightProperty());
        overlayCanvas.widthProperty().bind(this.widthProperty());
        overlayCanvas.heightProperty().bind(this.heightProperty());

        getChildren().addAll(gridCanvas, graphCanvas, overlayCanvas);

        widthProperty().addListener(e -> {
            isDirty = true;
            refreshAllData();
            draw();
        });
        heightProperty().addListener(e -> {
            isDirty = true;
            refreshAllData();
            draw();
        });

        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                isMouseDown = true;
                updateHoverState(e.getX(), e.getY());
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

        // Only redraw overlay on hover — not grid/graph
        setOnMouseMoved(e -> {
            updateHoverState(e.getX(), e.getY());
            getScene().setCursor(isSnapPoint ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
            drawOverlayLayer();
        });

        setOnMouseDragged(e -> {
            isInteracting = true;
            if (e.getButton() == MouseButton.PRIMARY) {
                updateHoverState(e.getX(), e.getY());
                if (!isHovering) {
                    double dx = (e.getX() - prevMouseX) / scale;
                    double dy = (e.getY() - prevMouseY) / scale;
                    graphCenterX -= dx;
                    graphCenterY += dy;
                    isDirty = true;
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
            isInteracting = true;
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

            isDirty = true;
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
        if (!removed) selectedPoints.add(point);
    }

    private void updateHoverState(double mouseX, double mouseY) {
        double gx = graphCenterX + (mouseX - getWidth() / 2) / scale;
        double gy = graphCenterY + (getHeight() / 2 - mouseY) / scale;
        double threshold = SNAP_THRESHOLD_PX / scale;

        hoverPoint = null;
        isHovering = false;
        isSnapPoint = false;

        for (Point2D ip : intersectionPoints) {
            if (ip.distance(gx, gy) < threshold) {
                hoverPoint = ip;
                hoverColor = Color.YELLOW;
                isSnapPoint = true;
                isHovering = true;
                return;
            }
        }
        for (Point2D ip : interceptPoints) {
            if (ip.distance(gx, gy) < threshold) {
                hoverPoint = ip;
                hoverColor = Color.LIGHTGRAY;
                isSnapPoint = true;
                isHovering = true;
                return;
            }
        }

        double bestDist = Double.MAX_VALUE;
        for (EquationData eq : currentEquations.values()) {
            if (eq.parser.eqtype == EquationParser.EqType.Implicit) continue;
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

    public static Map<String, EquationData> getCurrentEquations() {
        return mainInstance != null ? mainInstance.currentEquations : new HashMap<>();
    }

    public void refreshEquationData(String id) {
        double graphMinX = graphCenterX - (getWidth() / 2) / scale;
        double graphMaxX = graphCenterX + (getWidth() / 2) / scale;

        EquationData equation = currentEquations.get(id);
        if (equation == null) return;
        if (equation.parser.eqtype == EquationParser.EqType.Explicit) {
            equation.buildCacheExplicit(graphMinX, graphMaxX, getWidth());
        } else if (equation.eqType == EquationParser.EqType.Polar) {
            equation.buildCachePolar(equation.thetaMin, equation.thetaMax, getWidth());
        } else if (equation.eqType == EquationParser.EqType.Parametric) {
            equation.buildCacheParametric(equation.tMin, equation.tMax, getWidth());
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
            if (e1.parser.eqtype == EquationParser.EqType.Implicit) continue;
            for (int j = i + 1; j < equations.size(); j++) {
                EquationData e2 = equations.get(j);
                if (e2.parser.eqtype == EquationParser.EqType.Implicit) continue;
                double prevX = xMin;
                double prevDiff = e1.getY(prevX) - e2.getY(prevX);
                for (double x = xMin + scanStep; x <= xMax; x += scanStep) {
                    double d1 = e1.getY(x), d2 = e2.getY(x);
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
            if (eq.parser.eqtype == EquationParser.EqType.Implicit) continue;
            if (xMin <= 0 && xMax >= 0) {
                double yVal = eq.getY(0);
                if (!Double.isNaN(yVal)) interceptPoints.add(new Point2D(0, yVal));
            }
            double prevX = xMin, prevY = eq.getY(prevX);
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


    public void cancelAllTasks() {
        activeTasks.values().forEach(t -> t.cancel(true));
        activeTasks.clear();
    }

    public static GraphPlotter getMainInstance() {
        return mainInstance;
    }

    void drawGraphLayer() {
        GraphicsContext gc = graphCanvas.getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        gc.clearRect(0, 0, w, h);
        drawFunction(gc, w, h);

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

    private void drawOverlayLayer() {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        gc.clearRect(0, 0, w, h);

        for (Point2D p : selectedPoints) drawPointMarker(gc, p, Color.web("#FEFEFA"));
        for (Point2D ip : intersectionPoints) drawSmallIndicator(gc, ip, Color.web("#444444"));
        for (Point2D ip : interceptPoints) drawSmallIndicator(gc, ip, Color.web("#666666"));

        if (isMouseDown && isHovering && hoverPoint != null) {
            drawPointMarker(gc, hoverPoint, hoverColor);
        }
    }

    public static void setMainInstance(GraphPlotter p) {
        mainInstance = p;
    }

    private void drawSmallIndicator(GraphicsContext gc, Point2D p, Color color) {
        double px = (p.getX() - graphCenterX) * scale + getWidth() / 2;
        double py = getHeight() / 2 - (p.getY() - graphCenterY) * scale;
        gc.setFill(color);
        gc.fillOval(px - 3, py - 3, 6, 6);
    }

    public void refreshAllData() {
        double w = getWidth();
        if (w <= 0) return;

        double graphMinX = graphCenterX - (w / 2) / scale;
        double graphMaxX = graphCenterX + (w / 2) / scale;

        isDirty = true;

        for (EquationData equation : currentEquations.values()) {
            if (equation.parser != null) {
                if (equation.eqType == EquationParser.EqType.Polar) {
                    equation.buildCachePolar(equation.thetaMin, equation.thetaMax, w);
                } else if (equation.parser.eqtype == EquationParser.EqType.Parametric) {
                    equation.buildCacheParametric(equation.tMin, equation.tMax, w);
                } else if (equation.parser.eqtype != EquationParser.EqType.Implicit) {
                    equation.buildCacheExplicit(graphMinX, graphMaxX, w);
                }
            }
        }
        updateIntersections();
        updateIntercepts();
    }

    private String formatNumber(double d) {
        return new DecimalFormat("#.##").format(d);
    }

    public void draw() {
        drawGridLayer();
        drawGraphLayer();
        if (postDrawAction != null) postDrawAction.run();
        drawOverlayLayer();
    }

    private void drawGridLayer() {
        GraphicsContext gc = gridCanvas.getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        if (!isDirty) return;
        isDirty = false;

        gc.clearRect(0, 0, w, h);
        gc.setFill(ThemeColor.BACKGROUND.getColor(isLightMode));
        gc.fillRect(0, 0, w, h);

        if (polarGrid) drawPolarGrid(gc, w, h);
        else drawCartesianGrid(gc, w, h);
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

        gc.setFill(ThemeColor.BACKGROUND.getColor(isLightMode, 0.9));
        gc.setStroke(color);
        gc.setLineWidth(1.5);
        gc.fillRoundRect(boxX, boxY, textWidth + padding, textHeight + padding, 5, 5);

        gc.setFill(Color.WHITE);
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        gc.fillText(label, px + 20, py + 20 + 2);
    }

    // ── Cartesian grid — dots for minor, soft lines for major ─────────────────
    public void drawCartesianGrid(GraphicsContext gc, double w, double h) {
        double left = graphCenterX - w / 2 / scale;
        double top = graphCenterY - h / 2 / scale;
        double yAxisPixel = (0 - graphCenterX) * scale + w / 2;
        double xAxisPixel = h / 2 - (0 - graphCenterY) * scale;

        gc.setFont(javafx.scene.text.Font.font("JetBrains Mono", 12));

        double targetPixels = 100.0;
        double rawStep = targetPixels / scale;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double fraction = rawStep / magnitude;

        double majorStep = (fraction < 2.0) ? magnitude
                : (fraction < 5.0) ? 2 * magnitude
                  : 5 * magnitude;
        double minorStep = majorStep / 5.0;

        // Minor grid: dots
        if ((minorStep * scale) >= 8) {
            gc.setFill(ThemeColor.GRID_MINOR.getColor(isLightMode));
            for (double x = Math.floor(left / minorStep) * minorStep;
                 (x - left) * scale < w + scale; x += minorStep) {
                double px = (x - graphCenterX) * scale + w / 2;
                for (double y = Math.floor(top / minorStep) * minorStep;
                     (y - top) * scale < h + scale; y += minorStep) {
                    double py = h / 2 - (y - graphCenterY) * scale;
                    gc.fillOval(px - 1, py - 1, 2.2, 2.2);
                }
            }
        }

        // Major grid: soft lines + labels
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setStroke(ThemeColor.GRID_MAJOR.getColor(isLightMode));
        gc.setLineWidth(0.5);

        for (double x = Math.floor(left / majorStep) * majorStep;
             (x - left) * scale < w + scale; x += majorStep) {
            double px = (x - graphCenterX) * scale + w / 2;
            gc.strokeLine(px, 0, px, h);
            if (Math.abs(x) > 1e-9) {
                gc.setFill(ThemeColor.TEXT_PRIMARY.getColor(isLightMode));
                double labelY = Math.clamp(xAxisPixel + 15, 0, Math.max(0, h - 20));
                gc.fillText(formatNumber(x), px, labelY);
            }
        }

        for (double y = Math.floor((graphCenterY - h / 2 / scale) / majorStep) * majorStep;
             (y - (graphCenterY - h / 2 / scale)) * scale < h + scale; y += majorStep) {
            double py = h / 2 - (y - graphCenterY) * scale;
            gc.strokeLine(0, py, w, py);
            if (Math.abs(y) > 1e-9) {
                gc.setFill(ThemeColor.TEXT_PRIMARY.getColor(isLightMode));
                double labelX = Math.clamp(yAxisPixel - 15, Math.min(45, w), Math.max(45, w - 5));
                gc.fillText(formatNumber(y), labelX, py);
            }
        }

        // Axes
        gc.setStroke(ThemeColor.AXIS_MAIN.getColor(isLightMode));
        gc.setLineWidth(1.5);
        gc.strokeLine(yAxisPixel, 0, yAxisPixel, h);
        gc.strokeLine(0, xAxisPixel, w, xAxisPixel);
        gc.setFill(ThemeColor.TEXT_PRIMARY.getColor(isLightMode));
        gc.fillText("0", yAxisPixel - 10, xAxisPixel + 15);
    }

    private void drawFunction(GraphicsContext gc, double w, double h) {
        for (Map.Entry<String, EquationData> entry : currentEquations.entrySet()) {
            String id = entry.getKey();
            EquationData equation = entry.getValue();
            if (!equation.isVisible) continue;
            if (equation.parser.isInequality) {
                drawFunction_MarchingSquares(gc, w, h, equation.parser, equation, id);
            } else if (equation.parser.eqtype == EquationParser.EqType.Implicit) {
                drawFunction_MarchingSquares(gc, w, h, equation.parser, equation, id);
            } else if (equation.eqType == EquationParser.EqType.Polar || equation.parser.eqtype == EquationParser.EqType.Polar) {
                drawFunction_Polar(gc, w, h, equation);
            } else if (equation.eqType == EquationParser.EqType.Parametric || equation.parser.eqtype == EquationParser.EqType.Parametric) {
                drawFunction_Parametric(gc, w, h, equation);
            } else {
                drawFunction_Explicit(gc, w, h, equation);
            }
        }
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

        implicitCache.remove(id);
        if (activeTasks.containsKey(id)) {
            activeTasks.get(id).cancel(true);
            activeTasks.remove(id);
        }

        if (data.parser.getPoints() != null) {
            Points p = data.parser.getPoints();
            pointsMap.put(id, new Points(p.getX(), p.getY(), color));
        } else {
            currentEquations.put(id, data);
            refreshAllData();
        }
        isDirty = true;
        draw();
    }

    private void drawFunction_MarchingSquares(GraphicsContext gc, double w, double h,
                                              EquationParser mainParser, EquationData data, String id) {
        // Serve cache if available (Existing Cache Logic)
        if (implicitCache.containsKey(id)) {
            CachedImplicit cache = implicitCache.get(id);
            if (mainParser.isInequality) {
                gc.setFill(data.color.deriveColor(0, 1, 1, 0.4));
                for (double[] poly : cache.lines) {
                    double[] px = new double[poly.length / 2];
                    double[] py = new double[poly.length / 2];
                    boolean visible = false;
                    for (int i = 0; i < poly.length; i += 2) {
                        px[i / 2] = (poly[i] - graphCenterX) * scale + w / 2.0;
                        py[i / 2] = h / 2.0 - (poly[i + 1] - graphCenterY) * scale;
                        if (px[i / 2] > -100 && px[i / 2] < w + 100 && py[i / 2] > -100 && py[i / 2] < h + 100)
                            visible = true;
                    }
                    if (visible) gc.fillPolygon(px, py, px.length);
                }
            } else {
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
            }
            if (cache.scale == scale && !isInteracting) return;
        }

        if (activeTasks.containsKey(id)) activeTasks.get(id).cancel(true);

        final double viewCx = graphCenterX;
        final double viewCy = graphCenterY;
        final double viewScale = scale;

        // --- DYNAMIC RESOLUTION ---
        final double areaMultiplier = 1.4;
        final double fineStep = mainParser.isInequality
                ? (isParameterAnimating ? 6.0 / viewScale : 1.0 / viewScale)
                : (isParameterAnimating ? 4.0 / viewScale : 0.5 / viewScale);
        final double coarseStepMath = isParameterAnimating ? 20.0 / viewScale : 10.0 / viewScale;

        final double startX = viewCx - (w / viewScale * areaMultiplier) / 2.0;
        final double startY = viewCy + (h / viewScale * areaMultiplier) / 2.0;

        final int mCols = mainParser.isInequality
                ? (int) ((w / viewScale * areaMultiplier) / fineStep) + 2
                : (int) ((w / viewScale * areaMultiplier) / coarseStepMath) + 1;
        final int mRows = mainParser.isInequality
                ? (int) ((h / viewScale * areaMultiplier) / fineStep) + 2
                : (int) ((h / viewScale * areaMultiplier) / coarseStepMath) + 1;

        java.util.function.Supplier<List<double[]>> geometryBuilder = () -> {
            List<double[]> geometries = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            int sub = isParameterAnimating ? 5 : 10;

            if (mainParser.isInequality) {
                java.util.stream.IntStream.range(0, (mRows - 1) * (mCols - 1)).parallel().forEach(i -> {
                    int c = i % (mCols - 1), r = i / (mCols - 1);
                    double x = startX + c * fineStep;
                    double y = startY - r * fineStep;

                    double vtl = mainParser.evaluateImplicit(x, y);
                    double vtr = mainParser.evaluateImplicit(x + fineStep, y);
                    double vbl = mainParser.evaluateImplicit(x, y - fineStep);
                    double vbr = mainParser.evaluateImplicit(x + fineStep, y - fineStep);

                    int s = (vtl > 0 ? 8 : 0) | (vtr > 0 ? 4 : 0) | (vbr > 0 ? 2 : 0) | (vbl > 0 ? 1 : 0);
                    if (s == 0) return;

                    if (s == 15) {
                        geometries.add(new double[]{x, y, x + fineStep, y, x + fineStep, y - fineStep, x, y - fineStep});
                        return;
                    }

                    double xT = x + fineStep * interp(vtl, vtr), yT = y;
                    double xB = x + fineStep * interp(vbl, vbr), yB = y - fineStep;
                    double xL = x, yL = y - fineStep * interp(vtl, vbl);
                    double xR = x + fineStep, yR = y - fineStep * interp(vtr, vbr);
                    double xTL = x, yTL = y, xTR = x + fineStep, yTR = y;
                    double xBR = x + fineStep, yBR = y - fineStep, xBL = x, yBL = y - fineStep;

                    switch (s) {
                        case 1:
                            geometries.add(new double[]{xL, yL, xBL, yBL, xB, yB});
                            break;
                        case 2:
                            geometries.add(new double[]{xB, yB, xBR, yBR, xR, yR});
                            break;
                        case 3:
                            geometries.add(new double[]{xL, yL, xBL, yBL, xBR, yBR, xR, yR});
                            break;
                        case 4:
                            geometries.add(new double[]{xT, yT, xTR, yTR, xR, yR});
                            break;
                        case 5: {
                            double vc = mainParser.evaluateImplicit(x + fineStep * 0.5, y - fineStep * 0.5);
                            if (vc > 0)
                                geometries.add(new double[]{xTL, yTL, xT, yT, xR, yR, xBR, yBR, xB, yB, xL, yL});
                            else {
                                geometries.add(new double[]{xTL, yTL, xT, yT, xL, yL});
                                geometries.add(new double[]{xB, yB, xBR, yBR, xR, yR});
                            }
                            break;
                        }
                        case 6:
                            geometries.add(new double[]{xT, yT, xTR, yTR, xBR, yBR, xB, yB});
                            break;
                        case 7:
                            geometries.add(new double[]{xL, yL, xT, yT, xTR, yTR, xBR, yBR, xBL, yBL});
                            break;
                        case 8:
                            geometries.add(new double[]{xTL, yTL, xT, yT, xL, yL});
                            break;
                        case 9:
                            geometries.add(new double[]{xTL, yTL, xT, yT, xB, yB, xBL, yBL});
                            break;
                        case 10: {
                            double vc = mainParser.evaluateImplicit(x + fineStep * 0.5, y - fineStep * 0.5);
                            if (vc > 0)
                                geometries.add(new double[]{xT, yT, xTR, yTR, xR, yR, xB, yB, xBL, yBL, xL, yL});
                            else {
                                geometries.add(new double[]{xT, yT, xTR, yTR, xR, yR});
                                geometries.add(new double[]{xL, yL, xBL, yBL, xB, yB});
                            }
                            break;
                        }
                        case 11:
                            geometries.add(new double[]{xTL, yTL, xT, yT, xR, yR, xBR, yBR, xBL, yBL});
                            break;
                        case 12:
                            geometries.add(new double[]{xTL, yTL, xTR, yTR, xR, yR, xL, yL});
                            break;
                        case 13:
                            geometries.add(new double[]{xTL, yTL, xTR, yTR, xR, yR, xB, yB, xBL, yBL});
                            break;
                        case 14:
                            geometries.add(new double[]{xTL, yTL, xTR, yTR, xBR, yBR, xB, yB, xL, yL});
                            break;
                    }
                });
            } else {
                java.util.stream.IntStream.range(0, (mRows - 1) * (mCols - 1)).parallel().forEach(i -> {
                    // ... (KEEP YOUR EXISTING STANDARD IMPLICIT LOOP HERE) ...
                    int c = i % (mCols - 1), r = i / (mCols - 1);
                    double bx = startX + c * coarseStepMath, by = startY - r * coarseStepMath;
                    for (int fr = 0; fr < sub; fr++) {
                        for (int fc = 0; fc < sub; fc++) {
                            double x = bx + fc * fineStep;
                            double y = by - fr * fineStep;
                            double vtl = mainParser.evaluateImplicit(x, y);
                            double vtr = mainParser.evaluateImplicit(x + fineStep, y);
                            double vbl = mainParser.evaluateImplicit(x, y - fineStep);
                            double vbr = mainParser.evaluateImplicit(x + fineStep, y - fineStep);
                            int s = (vtl > 0 ? 8 : 0) | (vtr > 0 ? 4 : 0) | (vbr > 0 ? 2 : 0) | (vbl > 0 ? 1 : 0);
                            if (s == 0 || s == 15) continue;
                            double ftX = x + fineStep * interp(vtl, vtr);
                            double fbX = x + fineStep * interp(vbl, vbr);
                            double flY = y - fineStep * interp(vtl, vbl);
                            double frY = y - fineStep * interp(vtr, vbr);
                            if (s == 1 || s == 14) geometries.add(new double[]{x, flY, fbX, y - fineStep});
                            else if (s == 2 || s == 13)
                                geometries.add(new double[]{fbX, y - fineStep, x + fineStep, frY});
                            else if (s == 4 || s == 11) geometries.add(new double[]{ftX, y, x + fineStep, frY});
                            else if (s == 8 || s == 7) geometries.add(new double[]{x, flY, ftX, y});
                            else if (s == 3 || s == 12) geometries.add(new double[]{x, flY, x + fineStep, frY});
                            else if (s == 6 || s == 9) geometries.add(new double[]{ftX, y, fbX, y - fineStep});
                            else if (s == 5) {
                                geometries.add(new double[]{x, flY, ftX, y});
                                geometries.add(new double[]{fbX, y - fineStep, x + fineStep, frY});
                            } else if (s == 10) {
                                geometries.add(new double[]{ftX, y, x + fineStep, frY});
                                geometries.add(new double[]{x, flY, fbX, y - fineStep});
                            }
                        }
                    }
                });
            }
            return geometries;
        };

        if (isParameterAnimating) {
            // --- FAST PATH ---
            // Runs synchronously on UI thread so it draws exactly with the AnimationTimer frame
            List<double[]> fastGeom = geometryBuilder.get();
            implicitCache.put(id, new CachedImplicit(fastGeom, viewScale, viewCx, viewCy));

            if (mainParser.isInequality) {
                gc.setFill(data.color.deriveColor(0, 1, 1, 0.4));
                for (double[] poly : fastGeom) {
                    double[] px = new double[poly.length / 2];
                    double[] py = new double[poly.length / 2];
                    for (int i = 0; i < poly.length; i += 2) {
                        px[i / 2] = (poly[i] - graphCenterX) * scale + w / 2.0;
                        py[i / 2] = h / 2.0 - (poly[i + 1] - graphCenterY) * scale;
                    }
                    gc.fillPolygon(px, py, px.length);
                }
            } else {
                gc.setStroke(data.color);
                gc.setLineWidth(2.5);
                for (double[] line : fastGeom) {
                    double px1 = (line[0] - graphCenterX) * scale + w / 2.0;
                    double py1 = h / 2.0 - (line[1] - graphCenterY) * scale;
                    double px2 = (line[2] - graphCenterX) * scale + w / 2.0;
                    double py2 = h / 2.0 - (line[3] - graphCenterY) * scale;
                    gc.strokeLine(px1, py1, px2, py2);
                }
            }
        } else {
            // --- SLOW PATH ---
            // Full high-resolution static rendering on background thread
            javafx.concurrent.Task<List<double[]>> task = new javafx.concurrent.Task<>() {
                @Override
                protected List<double[]> call() {
                    return geometryBuilder.get();
                }
            };

            task.setOnSucceeded(e -> {
                implicitCache.put(id, new CachedImplicit(task.getValue(), viewScale, viewCx, viewCy));
                activeTasks.remove(id);
                isDirty = true;
                drawGraphLayer();
            });

            activeTasks.put(id, task);
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        }
    }

    public void removeEquation(String id) {
        currentEquations.remove(id);
        refreshAllData();
        implicitCache.remove(id);
        pointsMap.remove(id);
        isDirty = true;
        draw();
    }

    public void zoomIn() {
        scale = Math.min(scale * 1.1, MAX_SCALE);
        isDirty = true;
        draw();
    }

    public void zoomOut() {
        scale = Math.max(scale / 1.1, MIN_SCALE);
        isDirty = true;
        draw();
    }

    public void reset() {
        graphCenterX = 0;
        graphCenterY = 0;
        scale = 50;
        isDirty = true;
        refreshAllData();
        draw();
    }

    public void updateEqColor(String id, Color color) {
        EquationData data = currentEquations.get(id);
        if (data != null) {
            data.setColor(color);
            isDirty = true;
            draw();
        }
    }

    public void clearAllEquations() {
        cancelAllTasks();
        currentEquations.clear();
        pointsMap.clear();
        implicitCache.clear();
        isDirty = true;
        draw();
    }

    public void drawFunction_Explicit(GraphicsContext gc, double w, double h, EquationData data) {
        gc.beginPath();
        gc.setStroke(data.color);
        gc.setLineWidth(2.5);
        boolean firstPoint = true;
        double prevPixelY = 0;
        double prevDy = 0; // Track previous vertical change

        for (double pixelX = 0; pixelX < w; pixelX += 0.1) {
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
                double dy = pixelY - prevPixelY;

                // Distance Check and Direction Check
                if ((prevDy != 0 && dy * prevDy < 0) || Math.abs(dy) > h) {
                    gc.moveTo(pixelX, pixelY);
                } else {
                    gc.lineTo(pixelX, pixelY);
                }
                prevDy = dy;
            }
            prevPixelY = pixelY;
        }
        gc.stroke();
    }

    public void onParameterChanged(String id) {
        EquationData eq = currentEquations.get(id);
        if (eq == null) return;

        if (eq.parser.eqtype == EquationParser.EqType.Implicit || eq.parser.isInequality) {
            // Cancel any in-flight task for this id — it was computing with the old value
            if (activeTasks.containsKey(id)) {
                activeTasks.get(id).cancel(true);
                activeTasks.remove(id);
            }
            // Remove stale cache — next drawGraphLayer() will recompute
            implicitCache.remove(id);
        } else if (eq.parser.eqtype == EquationParser.EqType.Explicit) {
            double graphMinX = graphCenterX - (getWidth() / 2) / scale;
            double graphMaxX = graphCenterX + (getWidth() / 2) / scale;
            eq.buildCacheExplicit(graphMinX, graphMaxX, getWidth());
        } else if (eq.eqType == EquationParser.EqType.Polar) {
            eq.buildCachePolar(eq.thetaMin, eq.thetaMax, getWidth());
        } else if (eq.eqType == EquationParser.EqType.Parametric) {
            eq.buildCacheParametric(eq.tMin, eq.tMax, getWidth());
        }

        drawGraphLayer();
    }

    public EquationData getEquation(String id) {
        return currentEquations.get(id);
    }

    public Canvas getGraphCanvas() {
        return graphCanvas;
    }

    private void drawFunction_Polar(GraphicsContext gc, double w, double h, EquationData data) {
        gc.beginPath();
        gc.setStroke(data.color);
        gc.setLineWidth(2.5);

        if (data.CacheX != null && data.CacheY != null) {
            boolean first = true;
            double prevPx = 0;
            double prevPy = 0;

            for (int i = 0; i < data.CacheX.length; i++) {
                double x = data.CacheX[i], y = data.CacheY[i];

                // Break path for undefined/infinity points
                if (Double.isNaN(x) || Double.isNaN(y) || Double.isInfinite(x) || Double.isInfinite(y)) {
                    first = true;
                    continue;
                }

                double px = (x - graphCenterX) * scale + w / 2.0;
                double py = h / 2.0 - (y - graphCenterY) * scale;

                if (first) {
                    gc.moveTo(px, py);
                    first = false;
                } else {
                    boolean hugeJumpX = Math.abs(px - prevPx) > (w * 0.6);
                    boolean hugeJumpY = Math.abs(py - prevPy) > (h * 0.6);

                    if (hugeJumpX || hugeJumpY) {
                        gc.stroke();
                        gc.beginPath();
                        gc.moveTo(px, py);
                    } else {
                        gc.lineTo(px, py);
                    }
                }
                prevPx = px;
                prevPy = py;
            }
            gc.stroke();
        }
    }

    private void drawFunction_Parametric(GraphicsContext gc, double w, double h, EquationData data) {
        if (data.CacheX == null || data.CacheY == null) return;

        gc.beginPath();
        gc.setStroke(data.color);
        gc.setLineWidth(2.5);

        double lastPx = 0, lastPy = 0;
        double prevDx = 0, prevDy = 0;
        boolean firstPointInSegment = true;

        for (int i = 0; i < data.CacheX.length; i++) {
            double gx = data.CacheX[i];
            double gy = data.CacheY[i];

            // 1. Handle Undefined Points (NaN)
            if (Double.isNaN(gx) || Double.isNaN(gy)) {
                firstPointInSegment = true;
                continue;
            }

            // 2. Transform Graph -> Pixel
            double px = (gx - graphCenterX) * scale + w / 2.0;
            double py = h / 2.0 - (gy - graphCenterY) * scale;

            if (firstPointInSegment) {
                gc.moveTo(px, py);
                firstPointInSegment = false;
            } else {
                double dx = px - lastPx;
                double dy = py - lastPy;

                // 3. The "Sanity Guard" (Asymptote Detection)
                // Distance Check: Jump > Screen Height
                boolean hugeJump = Math.sqrt(dx*dx + dy*dy) > h/2;

                // Direction Check: Vector Flip (Dot Product < 0 means > 90 degree turn)
                double dotProduct = (dx * prevDx) + (dy * prevDy);
                boolean suddenFlip = (prevDx != 0 || prevDy != 0) && dotProduct < 0;

                if (hugeJump && suddenFlip) {
                    gc.moveTo(px, py); // Lift the pen and move to the new side
                } else {
                    gc.lineTo(px, py); // Draw the connection
                }

                prevDx = dx;
                prevDy = dy;
            }

            lastPx = px;
            lastPy = py;
        }
        gc.stroke();
    }

    public void toggleGrid() {
        polarGrid = !polarGrid;
        isDirty = true;
        draw();
    }


    public double getCenterX() {
        return graphCenterX;
    }

    public double getCenterY() {
        return graphCenterY;
    }

    public double getScale() {
        return scale;
    }

    public void setPostDrawAction(Runnable action) {
        this.postDrawAction = action;
    }

    // new overload:
    public void addEquationToHashmap(String id, String fullInput, Color color, EquationParser.EqType eqType) {
        EquationData data = new EquationData();
        data.raw = fullInput;
        data.parser = new EquationParser(fullInput);
        data.setColor(color);
        data.eqType = eqType;

        // default theta and t range:
        data.thetaMin = 0;
        data.thetaMax = 2 * Math.PI;
        data.tMin = 0;
        data.tMax = 1.0;

        // clear caches & active tasks (existing code)
        implicitCache.remove(id);
        if (activeTasks.containsKey(id)) {
            activeTasks.get(id).cancel(true);
            activeTasks.remove(id);
        }

        // FIX: Check if the parser detected a point before saving
        if (data.parser.getPoints() != null) {
            Points p = data.parser.getPoints();
            pointsMap.put(id, new Points(p.getX(), p.getY(), color));
            currentEquations.remove(id); // Ensure it isn't treated as a function
        } else {
            currentEquations.put(id, data);
            pointsMap.remove(id);
        }

        refreshAllData();
        isDirty = true;
        draw();
    }

    // ── Polar grid — dots for minor rings, soft strokes for major ─────────────
    public void drawPolarGrid(GraphicsContext gc, double w, double h) {
        double cx = (0 - graphCenterX) * scale + w / 2.0;
        double cy = h / 2.0 - (0 - graphCenterY) * scale;

        gc.setFont(javafx.scene.text.Font.font("JetBrains Mono", 12));

        double maxRadiusPx = Math.hypot(Math.max(cx, w - cx), Math.max(cy, h - cy));
        double targetPixels = 100.0;
        double rawStepUnits = targetPixels / Math.max(1e-12, scale);
        double magnitude = Math.pow(10, Math.floor(Math.log10(Math.max(1e-12, rawStepUnits))));
        double fraction = rawStepUnits / magnitude;
        double majorStepUnits = (fraction < 2.0) ? magnitude
                : (fraction < 5.0) ? 2 * magnitude
                  : 5 * magnitude;
        double minorStepUnits = majorStepUnits / 5.0;
        double majorStepPx = majorStepUnits * scale;
        double minorStepPx = minorStepUnits * scale;

        // Minor rings: dots
        if (minorStepPx >= 8) {
            gc.setFill(ThemeColor.GRID_MINOR.getColor(isLightMode));
            int dotsPerRing = 360;
            for (double r = minorStepPx; r <= maxRadiusPx + 1e-6; r += minorStepPx) {
                boolean isMajor = Math.abs((r / majorStepPx) - Math.round(r / majorStepPx)) < 0.01;
                if (isMajor) continue;
                for (int i = 0; i < dotsPerRing; i++) {
                    double ang = Math.toRadians(i);
                    double dx = cx + Math.cos(ang) * r;
                    double dy = cy - Math.sin(ang) * r;
                    if (dx > 0 && dx < w && dy > 0 && dy < h)
                        gc.fillOval(dx - 1, dy - 1, 2.2, 2.2);
                }
            }
        }

        // Major rings
        gc.setFill(ThemeColor.GRID_MAJOR.getColor(isLightMode));
        gc.setLineWidth(0.5);
        for (double r = majorStepPx; r <= maxRadiusPx + 1e-6; r += majorStepPx)
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);

        // Spokes
        gc.setFill(ThemeColor.GRID_MAJOR.getColor(isLightMode));
        gc.setLineWidth(0.5);
        int degreesStep = 15;
        for (int deg = 0; deg < 360; deg += degreesStep) {
            double ang = Math.toRadians(deg);
            gc.strokeLine(cx, cy, cx + Math.cos(ang) * maxRadiusPx,
                    cy - Math.sin(ang) * maxRadiusPx);
        }

        // Axis labels
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        double left = graphCenterX - w / 2 / scale;
        double yAxisPixel = (0 - graphCenterX) * scale + w / 2;
        double xAxisPixel = h / 2 - (0 - graphCenterY) * scale;

        for (double x = Math.floor(left / majorStepUnits) * majorStepUnits;
             (x - left) * scale < w + scale; x += majorStepUnits) {
            double px = (x - graphCenterX) * scale + w / 2;
            if (Math.abs(x) > 1e-9) {
                gc.setFill(ThemeColor.TEXT_PRIMARY.getColor(isLightMode));
                double labelY = Math.clamp(xAxisPixel + 15, 0, Math.max(0, h - 20));
                gc.fillText(formatNumber(x), px, labelY);
            }
        }
        for (double y = Math.floor((graphCenterY - h / 2 / scale) / majorStepUnits) * majorStepUnits;
             (y - (graphCenterY - h / 2 / scale)) * scale < h + scale; y += majorStepUnits) {
            double py = h / 2 - (y - graphCenterY) * scale;
            if (Math.abs(y) > 1e-9) {
                gc.setFill(ThemeColor.TEXT_PRIMARY.getColor(isLightMode));
                double labelX = Math.clamp(yAxisPixel - 15, 5, Math.max(5, w - 45));
                gc.fillText(formatNumber(y), labelX, py);
            }
        }

        // Main axes
        gc.setFill(ThemeColor.AXIS_MAIN.getColor(isLightMode));
        gc.setLineWidth(1.5);
        gc.strokeLine(cx - maxRadiusPx, cy, cx + maxRadiusPx, cy);
        gc.strokeLine(cx, cy - maxRadiusPx, cx, cy + maxRadiusPx);

        // Degree labels around outer rim
        gc.setFill(ThemeColor.TEXT_SECONDARY.getColor(isLightMode));
        double padding = 14.0;
        double labelRadius;
        if ((cx > w / 3 && cx < w * 2 / 3) && (Math.abs(cy) > h / 3 && Math.abs(cy) < h * 2 / 3))
            labelRadius = Math.min(cx, Math.min(w - cx, Math.min(Math.abs(cy), h - Math.abs(cy))));
        else if (cx > w / 3 && cx < w * 2 / 3)
            labelRadius = Math.min(cx, w - cx);
        else
            labelRadius = Math.max(Math.abs(cy), h - Math.abs(cy));
        labelRadius = Math.floor(labelRadius / majorStepPx) * majorStepPx - padding;

        for (int deg = 0; deg < 360; deg += 2 * degreesStep) {
            double ang = Math.toRadians(deg);
            double lx = cx + Math.cos(ang) * labelRadius;
            double ly = cy - Math.sin(ang) * labelRadius;
            if (deg == 90) gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
            else if (deg == 270) gc.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
            else gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            if (lx > 5 && lx < w - 5 && ly > 5 && ly < h - 5)
                gc.fillText(deg + "°", lx, ly);
        }
    }

    public void toggleTheme() {
        isLightMode = !isLightMode;
        isDirty = true;
        draw(); // Redraws the canvas with the new colors
    }
}
