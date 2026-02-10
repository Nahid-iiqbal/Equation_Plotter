package org.example.equation_plotter;

import javafx.animation.PauseTransition;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

// New class for storing equation data and parser
class EquationData {
    String raw;
    String limit;
    EquationParser parser;
    javafx.concurrent.Task<WritableImage> renderTask;
    Color color;
    int r, g, b;

    // ===== CACHE DATA =====
    private double[] yCache;
    private double step;
    private double xStart;
    private int startIndex;
    private int size; // number of stored samples


    // testing the idea right now only for Explicit functions
    public void buildCacheExplicit(double visibleMinX, double visibleMaxX, double width){
        if (parser.isImplicit()) return;

        double visibleWidth = visibleMaxX - visibleMinX;
        double bufferWidth = visibleWidth * 3;

        size = (int)(width * 3 * 2);
        step = bufferWidth/size;  // 2 points per pixel



        xStart = visibleMinX - visibleWidth;
        yCache = new double[size];

        for(int i=0;i<size;i++){
            double x = xStart + i*step;
            yCache[i] = parser.evaluateExplicit(x);
        }

        startIndex = 0;
    }

    public double getY(double graphX){
        double fIndex = (graphX - xStart)/step;

        int i0 = (int)Math.floor(fIndex);
        int i1 = i0 + 1;

//        i0 = Math.max(0, i0);
//        i1 = Math.min(size-1, i1);
        if(i0 < 0 || i1 >= size) return Double.NaN;

        double y0 = yCache[(startIndex + i0) % size];
        double y1 = yCache[(startIndex + i1) % size];

        double t = fIndex - i0;   // fractional part

        return y0 + t*(y1 - y0);  // interpolation
    }




    public void setColor(Color color) {
        this.color = color;
        this.r = (int) (color.getRed() * 255);
        this.g = (int) (color.getGreen() * 255);
        this.b = (int) (color.getBlue() * 255);
    }
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

    // timer for mouse scroll (
    private final PauseTransition scrollEndTimer = new PauseTransition(Duration.millis(120));

    //equation
    private boolean isInteracting = false;
    // Used hashmap<id, eq> for storing equations instead of array.
    private final Map<String, EquationData> currentEquations = new HashMap<>();
    // private String[] currentEquations = new String[50];
    private int eqCount = 0;

    public int getEquationSize() {
        return currentEquations.size();
    }

    // Reloads cache when scrolling is finished
    private void onScrollFinished(){
        // Rebuilds cache (I copied this loop wherever cache is rebuilt.)
        for (EquationData equation : currentEquations.values()) {
            if (equation.parser.isImplicit()) continue;

            double graphMinX = graphCenterX - (getWidth() / 2) / scale;
            double graphMaxX = graphCenterX + (getWidth() / 2) / scale;
            equation.buildCacheExplicit(graphMinX, graphMaxX, getWidth());
        }
    }



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
            isInteracting = true;
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
        setOnMouseReleased(e -> {
            isInteracting = false;

            // Rebuilds cache (I copied this loop wherever cache is rebuilt.)
            for (EquationData equation : currentEquations.values()) {
                if (equation.parser.isImplicit()) continue;

                double graphMinX = graphCenterX - (getWidth() / 2) / scale;
                double graphMaxX = graphCenterX + (getWidth() / 2) / scale;
                equation.buildCacheExplicit(graphMinX, graphMaxX, getWidth());
            }

            draw();
        });

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

            scrollEndTimer.playFromStart();

            draw();

        });

        scrollEndTimer.setOnFinished(e -> {
            onScrollFinished();
        });

    }


    public void draw() {

        // Draws the graph (Axis lines, gridboxes)
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);

        drawGrid(gc, w, h);

        drawFunction(gc, w, h);
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
        gc.setFont(javafx.scene.text.Font.font("JetBrains Mono", 12));

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
        gc.setFill(Color.WHITE); // Changed from GRAY to WHITE for visibility

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
            if (equation == null) continue;

            if (equation.parser.isImplicit()) {
                drawFunction_Implicit(gc, w, h, equation.parser, equation.raw, equation);
            } else {
                drawFunction_Explicit(gc, w, h, equation.parser, equation);
            }
        }
    }

    // Use a specific color for the graph (e.g., Cyan with full alpha)
    private static final int GRAPH_COLOR = 0xFF00FFFF; // ARGB (Cyan)

    // CHANGE: Added 'String rawEq' to the arguments
    private void drawFunction_Implicit(GraphicsContext gc, double w, double h, EquationParser mainParser, String rawEq, EquationData data) {
        int width = (int) w;
        int height = (int) h;
        int[] buffer = new int[width * height];

        ThreadLocal<EquationParser> threadParser = ThreadLocal.withInitial(mainParser::cloneForThread);

        int tileSize = 32;
        int numTilesX = (int) Math.ceil(w / tileSize);
        int numTilesY = (int) Math.ceil(h / tileSize);

        IntStream.range(0, numTilesX * numTilesY).parallel().forEach(i -> {
            int tileX = (i % numTilesX) * tileSize;
            int tileY = (i / numTilesX) * tileSize;
            EquationParser localParser = threadParser.get();
            recursivePlot(buffer, tileX, tileY, tileSize, width, height,
                    graphCenterX, graphCenterY, scale, localParser, data.r, data.g, data.b, 0);
        });

        WritableImage img = new WritableImage(width, height);
        img.getPixelWriter().setPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(), buffer, 0, width);
        gc.drawImage(img, 0, 0);
    }

    private void recursivePlot(int[] buffer, int x, int y, int size, int w, int h,
                               double cx, double cy, double sc, EquationParser parser,
                               int r, int g, int b, int depth) {

        // 1. Safety Guard
        if (depth > 20 || x >= w || y >= h || x + size < 0 || y + size < 0) return;

        // 2. Base Case: Drawing the Pixel
        int minSize = isInteracting ? 4 : 1;

        if (size <= minSize) {
            // Sample the CENTER of the block
            double gx = cx + (x + size / 2.0 - w / 2.0) / sc;
            double gy = cy + (h / 2.0 - (y + size / 2.0)) / sc;
            double val = parser.evaluateImplicit(gx, gy);

            // FIX 1: INCREASE THRESHOLD MULTIPLIER
            // Changed from 4.0 to 15.0. This ensures the line doesn't break when the function gets steep.
            double threshold = 15.0 * size / sc;

            if (!Double.isNaN(val) && Math.abs(val) < threshold) {
                int argb = (255 << 24) | (r << 16) | (g << 8) | b;
                for (int dy = 0; dy < size; dy++) {
                    for (int dx = 0; dx < size; dx++) {
                        int px = x + dx;
                        int py = y + dy;
                        if (px >= 0 && px < w && py >= 0 && py < h) {
                            buffer[py * w + px] = argb;
                        }
                    }
                }
            }
            return;
        }

        // 3. Recursive Step & Pruning
        double gx0 = cx + (x - w / 2.0) / sc;
        double gy0 = cy + (h / 2.0 - y) / sc;
        double gx1 = cx + ((x + size) - w / 2.0) / sc;
        double gy1 = cy + (h / 2.0 - (y + size)) / sc;

        double v1 = parser.evaluateImplicit(gx0, gy0);
        double v2 = parser.evaluateImplicit(gx1, gy0);
        double v3 = parser.evaluateImplicit(gx0, gy1);
        double v4 = parser.evaluateImplicit(gx1, gy1);

        // FIX 2: STRICTER PRUNING
        // Only prune if ALL corners are far from zero (using > 0.1 safety buffer)
        // and if the block is relatively large.
        // If the block is small (size < 16), force it to check pixels to avoid gaps.
        boolean allPos = v1 > 0 && v2 > 0 && v3 > 0 && v4 > 0;
        boolean allNeg = v1 < 0 && v2 < 0 && v3 < 0 && v4 < 0;

        if ((allPos || allNeg) && size > 16) {
            return;
        }

        int half = size / 2;
        if (half < 1) half = 1;

        recursivePlot(buffer, x, y, half, w, h, cx, cy, sc, parser, r, g, b, depth + 1);
        recursivePlot(buffer, x + half, y, half, w, h, cx, cy, sc, parser, r, g, b, depth + 1);
        recursivePlot(buffer, x, y + half, half, w, h, cx, cy, sc, parser, r, g, b, depth + 1);
        recursivePlot(buffer, x + half, y + half, half, w, h, cx, cy, sc, parser, r, g, b, depth + 1);
    }

    private void drawFunction_Explicit(GraphicsContext gc, double w, double h, EquationParser parser, EquationData data) {
        gc.beginPath();
        gc.setStroke(data.color);
        gc.setLineWidth(2.5);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        boolean firstPoint = true;

        // "step" = int, unit = pixels
        // FIX: Step by 0.1 pixels for smooth curves (10x more points)
        // If dragging, use 2.0 for speed.
        double step = isInteracting ? 1.0 : 1.0;

        for (double pixelX = 0; pixelX < w; pixelX += step) {
            // Screen X -> Graph X
            double graphX = graphCenterX + (pixelX - w / 2.0) / scale;

            // Calculate Math Y
            // double graphY = parser.evaluateExplicit(graphX);
            double graphY = data.getY(graphX);

            // Check for valid result (handle division by zero, etc.)
            if (Double.isNaN(graphY) || Double.isInfinite(graphY)) {
                firstPoint = true;
                continue;
            }

            // Math Y -> Screen Y
            double pixelY = h / 2.0 - (graphY - graphCenterY) * scale;

            // Asymptote Check
            if (!firstPoint) {
                double prevGraphY = data.getY(graphCenterX + ((pixelX - step) - w / 2.0) / scale);
                double prevPixelY = h / 2.0 - (prevGraphY - graphCenterY) * scale;
                if (Math.abs(pixelY - prevPixelY) > h) firstPoint = true;
            }

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


    // Inside GraphPlotter.java
    public void addEquationToHashmap(String id, String fullInput, Color color) {
        EquationData data = new EquationData();
        data.raw = fullInput;

        // The new EquationParser handles the {limit} extraction internally now
        data.parser = new EquationParser(fullInput);

        data.setColor(color);
        currentEquations.put(id, data);

        // builds cache
        if (!data.parser.isImplicit()) {
            double graphMinX = graphCenterX - (getWidth() / 2) / scale;
            double graphMaxX = graphCenterX + (getWidth() / 2) / scale;
            currentEquations.get(id).buildCacheExplicit(graphMinX, graphMaxX, getWidth());
        }
        draw();
    }

    public void updateEqColor(String id, Color color) {
        EquationData data = currentEquations.get(id);
        if (data != null) {
            data.setColor(color);
            draw();
        }
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

        // Rebuilds cache (I copied this loop wherever cache is rebuilt.)
        for (EquationData equation : currentEquations.values()) {
            if (equation.parser.isImplicit()) continue;

            double graphMinX = graphCenterX - (getWidth() / 2) / scale;
            double graphMaxX = graphCenterX + (getWidth() / 2) / scale;
            equation.buildCacheExplicit(graphMinX, graphMaxX, getWidth());
        }

        draw();
    }

    public int getEqCount() {
        return eqCount;
    }

    public void setEqCount(int eqCount) {
        this.eqCount = eqCount;
    }
}