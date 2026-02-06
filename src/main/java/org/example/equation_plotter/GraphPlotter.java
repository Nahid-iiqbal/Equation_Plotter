package org.example.equation_plotter;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

// New class for storing equation data and parser
class EquationData {
    String raw;
    EquationParser parser;
    javafx.concurrent.Task<WritableImage> renderTask;
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
    private boolean isInteracting = false;
    // Used hashmap<id, eq> for storing equations instead of array.
    private final Map<String, EquationData> currentEquations = new HashMap<>();
    // private String[] currentEquations = new String[50];
    private int eqCount = 0;

    public int getEquationSize() {
        return currentEquations.size();
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

            draw();
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
                drawFunction_Implicit(gc, w, h, equation.parser, equation.raw);
            } else {
                drawFunction_Explicit(gc, w, h, equation.parser);
            }
        }
    }

    // Use a specific color for the graph (e.g., Cyan with full alpha)
    private static final int GRAPH_COLOR = 0xFF00FFFF; // ARGB (Cyan)

    // CHANGE: Added 'String rawEq' to the arguments
    private void drawFunction_Implicit(GraphicsContext gc, double w, double h, EquationParser mainParser, String rawEq) {
        int width = (int) w;
        int height = (int) h;
        int[] buffer = new int[width * height];

        // --- OPTIMIZATION: Thread-Safe Parallelism ---
        // 1. Create a "Factory" that makes a new Parser for each thread.
        // This prevents the crash because every thread gets its own private object.
        ThreadLocal<EquationParser> threadParser = ThreadLocal.withInitial(() -> new EquationParser(rawEq));

        int tileSize = 32;
        int numTilesX = (int) Math.ceil(w / tileSize);
        int numTilesY = (int) Math.ceil(h / tileSize);

        // 2. ENABLE PARALLELISM (Safe now!)
        // We use .parallel() to use 100% of your CPU.
        IntStream.range(0, numTilesX * numTilesY).parallel().forEach(i -> {
            int tileX = (i % numTilesX) * tileSize;
            int tileY = (i / numTilesX) * tileSize;

            // 3. Get the private parser for THIS specific thread
            EquationParser localParser = threadParser.get();

            // Pass class variables (cx, cy, sc) explicitly
            recursivePlot(buffer, tileX, tileY, tileSize, width, height,
                    graphCenterX, graphCenterY, scale, localParser);
        });

        WritableImage img = new WritableImage(width, height);
        img.getPixelWriter().setPixels(0, 0, width, height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(),
                buffer, 0, width);

        gc.drawImage(img, 0, 0);
    }

    private void recursivePlot(int[] buffer, int x, int y, int size, int w, int h,
                               double cx, double cy, double sc, EquationParser parser) {
        // Boundary checks
        if (x >= w || y >= h) return;

        // --- 1. LAG FIX (Speed Optimization) ---
        // If the user is dragging the graph, stop calculating early (at 2px blocks).
        // This reduces CPU work by 75% during movement, making it buttery smooth.
        if (isInteracting && size <= 0) {
            double gx = cx + (x - w / 2.0) / sc;
            double gy = cy + (h / 2.0 - y) / sc;
            double val = parser.evaluateImplicit(gx, gy);

            // Fast check: Is this block on the line?
            if (Math.abs(val) < (2.0 / sc)) {
                // Fill the small block with solid color (Fast)
                for (int dy = 0; dy < size; dy++) {
                    for (int dx = 0; dx < size; dx++) {
                        int px = x + dx;
                        int py = y + dy;
                        if (py < h && px < w) {
                            buffer[py * w + px] = GRAPH_COLOR;
                        }
                    }
                }
            }
            return;
        }

        // --- 2. SMOOTHNESS FIX (High Quality Anti-Aliasing) ---
        // If we are down to 1 pixel, do the fancy math to make it look smooth.
        // ... inside recursivePlot ...

        // BASE CASE: Single Pixel (Ultra Smooth Mode)
        if (size == 1) {
            // 1. Calculate the Gradient (Slope) ONCE at the center.
            // (Calculating gradient 4 times is too slow, so we assume it's constant across the pixel)
            double gxCenter = cx + (x + 0.5 - w / 2.0) / sc;
            double gyCenter = cy + (h / 2.0 - (y + 0.5)) / sc;

            double valCenter = parser.evaluateImplicit(gxCenter, gyCenter);
            double epsilon = 1.0 / sc;
            double valX = parser.evaluateImplicit(gxCenter + epsilon, gyCenter);
            double valY = parser.evaluateImplicit(gxCenter, gyCenter + epsilon);
            double dx = (valX - valCenter) / epsilon;
            double dy = (valY - valCenter) / epsilon;
            double len = Math.sqrt(dx * dx + dy * dy);

            if (len < 1e-6) return; // Avoid division by zero

            // 2. Sub-Pixel Sampling (Check 4 corners of the pixel)
            // This acts like we recursed down to 0.5 pixels
            double totalAlpha = 0;
            double[] offsets = {0.25, 0.75}; // The centers of the 4 sub-pixels

            for (double offY : offsets) {
                for (double offX : offsets) {
                    // Calculate exact position of sub-pixel
                    double gx = cx + (x + offX - w / 2.0) / sc;
                    double gy = cy + (h / 2.0 - (y + offY)) / sc;

                    // Get value at this tiny point
                    double val = parser.evaluateImplicit(gx, gy);

                    // Use the center gradient to estimate distance
                    double dist = Math.abs(val / len);

                    // Thicker threshold for sub-pixels (2.5 is soft & nice)
                    double thickness = 2.5;

                    if (dist < thickness) {
                        double a = 1.0 - (dist / thickness);
                        totalAlpha += Math.max(0, Math.min(1, a));
                    }
                }
            }

            // 3. Average the result (divide by 4 samples)
            double finalAlpha = totalAlpha / 4.0;

            if (finalAlpha > 0) {
                int a = (int) (finalAlpha * 255);
                // Cyan Color
                int argb = (a << 24) | (0 << 16) | (255 << 8) | 255;
                buffer[y * w + x] = argb;
            }
            return;
        }

        // --- 3. RECURSION (Quadtree Pruning) ---
        // Check the 4 corners of the current block
        double gx0 = cx + (x - w / 2.0) / sc;
        double gy0 = cy + (h / 2.0 - y) / sc;
        double gx1 = cx + ((x + size) - w / 2.0) / sc;
        double gy1 = cy + (h / 2.0 - (y + size)) / sc;

        double v1 = parser.evaluateImplicit(gx0, gy0);
        double v2 = parser.evaluateImplicit(gx1, gy0);
        double v3 = parser.evaluateImplicit(gx0, gy1);
        double v4 = parser.evaluateImplicit(gx1, gy1);

        // Optimization: If all corners are positive (or all negative), the line
        // likely doesn't pass through this block. Skip it!
        boolean allPos = v1 > 0 && v2 > 0 && v3 > 0 && v4 > 0;
        boolean allNeg = v1 < 0 && v2 < 0 && v3 < 0 && v4 < 0;

        // Safety: Don't skip huge blocks (might miss a small closed circle)
        if ((allPos || allNeg) && size < 256) return;

        // Split into 4 smaller blocks and repeat
        int half = size / 2;
        recursivePlot(buffer, x, y, half, w, h, cx, cy, sc, parser);
        recursivePlot(buffer, x + half, y, half, w, h, cx, cy, sc, parser);
        recursivePlot(buffer, x, y + half, half, w, h, cx, cy, sc, parser);
        recursivePlot(buffer, x + half, y + half, half, w, h, cx, cy, sc, parser);
    }

    private void drawFunction_Explicit(GraphicsContext gc, double w, double h, EquationParser parser) {
        gc.beginPath();
        gc.setStroke(Color.CYAN);
        gc.setLineWidth(2.5); // Thicker line covers jaggedness better
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        boolean firstPoint = true;

        // FIX: Step by 0.1 pixels for smooth curves (10x more points)
        // If dragging, use 2.0 for speed.
        double step = isInteracting ? 0.5 : 0.1;

        for (double pixelX = 0; pixelX < w; pixelX += step) {
            // Screen X -> Graph X
            double graphX = graphCenterX + (pixelX - w / 2.0) / scale;

            // Calculate Math Y
            double graphY = parser.evaluateExplicit(graphX);

            // Check for valid result (handle division by zero, etc.)
            if (Double.isNaN(graphY) || Double.isInfinite(graphY)) {
                firstPoint = true;
                continue;
            }

            // Math Y -> Screen Y
            double pixelY = h / 2.0 - (graphY - graphCenterY) * scale;

            // Asymptote Check
            if (!firstPoint) {
                double prevGraphY = parser.evaluateExplicit(graphCenterX + ((pixelX - step) - w / 2.0) / scale);
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

    public void addEquationToHashmap(String id, String equation) {
        if (equation == null || equation.trim().isEmpty()) {
            removeEquation(id);
            return;
        }

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