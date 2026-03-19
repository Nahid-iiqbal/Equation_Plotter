package org.example.equation_plotter;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

public class integralCalc {
    private final GraphPlotter localPlotter;
    private final Label resultLabel = new Label("∫ ≈ 0.0000");
    private final TextField lowerField = new TextField("");
    private final TextField upperField = new TextField("");
    // ✅ ComboBoxes now hold EquationData directly — no string lookup needed
    private final ComboBox<EquationData> sel1 = new ComboBox<>();
    private final ComboBox<EquationData> sel2 = new ComboBox<>();
    private final RadioButton xBtn = new RadioButton("X-axis");
    private final RadioButton yBtn = new RadioButton("Y-axis");
    private EquationData eq1, eq2;

    private final javafx.animation.PauseTransition refreshDebounce =
            new javafx.animation.PauseTransition(Duration.millis(300));

    public integralCalc(EquationData initial) {
        this.eq1 = initial;
        this.localPlotter = new GraphPlotter(900, 700);

        // ✅ Snapshot the main map once — we never touch it again after this
        // ✅ Keep a reference to the snapshot taken at open time
        Map<String, EquationData> frozenEquations = Map.copyOf(GraphPlotter.getCurrentEquations());

        // Populate ComboBoxes with EquationData objects
        sel1.getItems().addAll(frozenEquations.values());
        sel2.getItems().addAll(frozenEquations.values());

        // ✅ Add a "None" sentinel object for sel2
        EquationData noneOption = new EquationData();
        noneOption.raw = "None";
        sel2.getItems().addFirst(noneOption);

        // ✅ Display the raw expression string in the dropdown
        javafx.util.StringConverter<EquationData> converter = new javafx.util.StringConverter<>() {
            @Override
            public String toString(EquationData ed) {
                return ed == null ? "" : ed.raw;
            }

            @Override
            public EquationData fromString(String s) {
                return null;
            }
        };
        sel1.setConverter(converter);
        sel2.setConverter(converter);

        // Pre-select the equation that was passed in
        frozenEquations.values().stream()
                .filter(e -> e.raw.equals(initial.raw))
                .findFirst()
                .ifPresent(sel1::setValue);
        sel2.getSelectionModel().select(0); // "None"

        ToggleGroup axisGroup = new ToggleGroup();
        xBtn.setToggleGroup(axisGroup);
        yBtn.setToggleGroup(axisGroup);
        xBtn.setSelected(true);
    }

    private VBox createSidebar() {
        VBox box = new VBox(12);
        box.getStyleClass().add("glass-panel");
        box.setPadding(new Insets(20));
        box.setPrefWidth(320);

        // ✅ These listeners now fire correctly because sel1/sel2 hold real objects
        sel1.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> refresh());
        sel2.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> refresh());
        xBtn.selectedProperty().addListener((obs, old, newVal) -> refresh());
        yBtn.selectedProperty().addListener((obs, old, newVal) -> refresh());
        xBtn.setStyle("-fx-text-fill: #00FFFF;");
        yBtn.setStyle("-fx-text-fill: #00FFFF;");
        lowerField.textProperty().addListener((obs, old, newVal) -> refresh());
        upperField.textProperty().addListener((obs, old, newVal) -> refresh());
        Label lblPrimary = new Label("PRIMARY EQUATION");
        lblPrimary.setStyle("-fx-text-fill: #00FFFF; -fx-font-weight: bold;"); // Cyan

        Label lblSecondary = new Label("SECONDARY (BOUND)");
        lblSecondary.setStyle("-fx-text-fill: #00FFFF; -fx-font-weight: bold;");

        Label lblAxis = new Label("INTEGRATION AXIS");
        lblAxis.setStyle("-fx-text-fill: #00FFFF; -fx-font-weight: bold;");

        Label lblLimits = new Label("LIMITS (A to B)");
        lblLimits.setStyle("-fx-text-fill: #00FFFF; -fx-font-weight: bold;");
        box.getChildren().addAll(
                lblPrimary, sel1,
                lblSecondary, sel2,
                lblAxis, new HBox(15, xBtn, yBtn),
                lblLimits, lowerField, upperField
        );
        return box;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Integral & Area Analyzer");

        resultLabel.getStyleClass().add("result-box-top");
        VBox hud = new VBox(resultLabel);
        hud.setPickOnBounds(false);
        hud.setPadding(new Insets(20));

        VBox sidebar = createSidebar();
        HBox nav = createNav();

        StackPane center = new StackPane(localPlotter, hud);
        BorderPane root = new BorderPane();
        root.setCenter(center);
        root.setRight(sidebar);
        root.setBottom(nav);

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm());
        stage.setScene(scene);

        // ✅ Scene is attached now — localPlotter has real dimensions, safe to draw
        stage.show();
        refresh();
    }

    private void refresh() {
        // Debounce — wait 300ms after last keystroke before recalculating
        refreshDebounce.setOnFinished(e -> doRefresh());
        refreshDebounce.playFromStart();
    }

    private void doRefresh() {
        this.eq1 = sel1.getValue();
        EquationData sel2Val = sel2.getValue();
        this.eq2 = (sel2Val == null || "None".equals(sel2Val.raw)) ? null : sel2Val;

        String lowTxt = lowerField.getText().trim();
        String upTxt = upperField.getText().trim();

        // Guard — incomplete input, just redraw without shading
        if (eq1 == null
                || lowTxt.isEmpty() || upTxt.isEmpty()
                || lowTxt.equals("-") || upTxt.equals("-")
                || lowTxt.equals(".") || upTxt.equals(".")) {
            localPlotter.setPostDrawAction(null);
            rebuildLocalPlotter();
            localPlotter.draw();
            resultLabel.setText("∫ ≈ 0.0000");
            return;
        }

        double a, b;
        try {
            a = Double.parseDouble(lowTxt);
            b = Double.parseDouble(upTxt);
        } catch (NumberFormatException ignored) {
            localPlotter.setPostDrawAction(null);
            rebuildLocalPlotter();
            localPlotter.draw();
            return;
        }

        boolean isX = xBtn.isSelected();

        // Show a loading state while Simpson runs off-thread
        resultLabel.setText("calculating...");

        Thread t = getThread(a, b, isX);
        t.start();
    }

    private @NotNull Thread getThread(double a, double b, boolean isX) {
        final double fa = a, fb = b;
        final boolean fx = isX;

        // Run Simpson off the UI thread so the window stays responsive
        javafx.concurrent.Task<Double> calcTask = new javafx.concurrent.Task<>() {
            @Override
            protected Double call() {
                return calculateSimpson(fa, fb, fx);
            }
        };

        calcTask.setOnSucceeded(event -> {
            double area = calcTask.getValue();
            resultLabel.setText(String.format("Area ≈ %.6f", Math.abs(area)));
            localPlotter.setPostDrawAction(() -> drawAdvancedShading(fa, fb, fx));
            rebuildLocalPlotter();
            localPlotter.draw();
        });

        calcTask.setOnFailed(event -> {
            resultLabel.setText("Error");
            localPlotter.setPostDrawAction(null);
            rebuildLocalPlotter();
            localPlotter.draw();
        });

        Thread t = new Thread(calcTask);
        t.setDaemon(true);
        return t;
    }

    /**
     * ✅ Rebuilds the localPlotter's equation set from scratch using COPIES of the
     * selected EquationData objects. This avoids touching the main window's static map
     * and ensures the local cache is built against the local plotter's actual dimensions.
     */
    private void rebuildLocalPlotter() {
        // Clear only localPlotter's own equations (safe after the static→instance fix)
        localPlotter.clearAllEquations();
        if (eq1 != null) {
            localPlotter.addEquationToHashmap("p", eq1.raw, eq1.color);
        }
        if (eq2 != null) {
            localPlotter.addEquationToHashmap("s", eq2.raw, eq2.color);
        }
    }

    private void drawAdvancedShading(double a, double b, boolean isX) {
        GraphicsContext gc = localPlotter.getGraphCanvas().getGraphicsContext2D();
        double w = localPlotter.getWidth();
        double h = localPlotter.getHeight();
        double s = localPlotter.getScale();
        double cX = localPlotter.getCenterX();
        double cY = localPlotter.getCenterY();

        EquationData localEq1 = localPlotter.getEquation("p");
        EquationData localEq2 = (eq2 != null) ? localPlotter.getEquation("s") : null;
        if (localEq1 == null) return;

        boolean imp1 = (localEq1.parser.eqtype == EquationParser.EqType.Implicit);
        boolean imp2 = localEq2 != null && (localEq2.parser.eqtype == EquationParser.EqType.Implicit);

        double start = Math.min(a, b);
        double end = Math.max(a, b);
        double xMin = cX - (w / 2) / s;
        double xMax = cX + (w / 2) / s;
        double yMin = cY - (h / 2) / s;
        double yMax = cY + (h / 2) / s;

        // ── Gradient: fade from curve color (top) to transparent (bottom) ──────────
        javafx.scene.paint.Color baseColor = localEq1.color;
        javafx.scene.paint.LinearGradient shadingGradient = new javafx.scene.paint.LinearGradient(
                0, 0, 0, h,
                false,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, baseColor.deriveColor(0, 1, 1, 0.55)),
                new javafx.scene.paint.Stop(1, baseColor.deriveColor(0, 1, 1, 0.08))
        );

        // ── Boundary stroke style ────────────────────────────────────────────────
        javafx.scene.paint.Color boundaryColor = baseColor.deriveColor(0, 1.1, 1.1, 0.9);

        gc.save();

        if (isX) {
            // ── Step 1: Collect top/bottom pixel edges for every column ─────────
            int colStart = (int) Math.max(0, Math.floor((start - cX) * s + w / 2));
            int colEnd = (int) Math.min(w - 1, Math.ceil((end - cX) * s + w / 2));

            double[] pyTops = new double[colEnd - colStart + 1];
            double[] pyBots = new double[colEnd - colStart + 1];
            java.util.Arrays.fill(pyTops, Double.NaN);
            java.util.Arrays.fill(pyBots, Double.NaN);

            for (int col = colStart; col <= colEnd; col++) {
                double gx = cX + (col - w / 2) / s;
                double topGy, botGy;

                if (imp1) {
                    java.util.List<Double> roots = solveForY(localEq1.parser, gx, yMin, yMax);
                    if (roots.isEmpty()) continue;
                    topGy = roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
                    botGy = roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                } else {
                    topGy = localEq1.parser.evaluateExplicit(gx);
                    if (Double.isNaN(topGy)) continue;

                    if (localEq2 == null) {
                        botGy = 0;
                    } else if (imp2) {
                        java.util.List<Double> roots = solveForY(localEq2.parser, gx, yMin, yMax);
                        botGy = roots.isEmpty() ? 0
                                : roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
                    } else {
                        botGy = localEq2.parser.evaluateExplicit(gx);
                        if (Double.isNaN(botGy)) botGy = 0;
                    }
                }

                double pyTop = h / 2 - (Math.max(topGy, botGy) - cY) * s;
                double pyBot = h / 2 - (Math.min(topGy, botGy) - cY) * s;
                pyTops[col - colStart] = Math.max(pyTop, 0);
                pyBots[col - colStart] = Math.min(pyBot, h);
            }

            // ── Step 2: Fill with gradient using a clipping polygon ─────────────
            // Build a closed polygon: top edge left→right, bottom edge right→left
            gc.beginPath();
            boolean started = false;
            for (int i = 0; i < pyTops.length; i++) {
                if (Double.isNaN(pyTops[i])) continue;
                if (!started) {
                    gc.moveTo(colStart + i, pyTops[i]);
                    started = true;
                } else gc.lineTo(colStart + i, pyTops[i]);
            }
            for (int i = pyBots.length - 1; i >= 0; i--) {
                if (Double.isNaN(pyBots[i])) continue;
                gc.lineTo(colStart + i, pyBots[i]);
            }
            gc.closePath();
            gc.setFill(shadingGradient);
            gc.fill();

            // ── Step 3: Top boundary stroke (along the curve) ───────────────────
            gc.setStroke(boundaryColor);
            gc.setLineWidth(1.8);
            gc.setLineDashes(null);
            gc.beginPath();
            started = false;
            for (int i = 0; i < pyTops.length; i++) {
                if (Double.isNaN(pyTops[i])) {
                    started = false;
                    continue;
                }
                if (!started) {
                    gc.moveTo(colStart + i, pyTops[i]);
                    started = true;
                } else gc.lineTo(colStart + i, pyTops[i]);
            }
            gc.stroke();

            // ── Step 4: Bottom boundary stroke (eq2 or x-axis) ──────────────────
            // Use a dimmer stroke for the x-axis / eq2 bound
            gc.setStroke(localEq2 != null ? localEq2.color.deriveColor(0, 1, 1, 0.8)
                    : baseColor.deriveColor(0, 0.5, 0.8, 0.5));
            gc.setLineWidth(1.2);
            gc.beginPath();
            started = false;
            for (int i = 0; i < pyBots.length; i++) {
                if (Double.isNaN(pyBots[i])) {
                    started = false;
                    continue;
                }
                if (!started) {
                    gc.moveTo(colStart + i, pyBots[i]);
                    started = true;
                } else gc.lineTo(colStart + i, pyBots[i]);
            }
            gc.stroke();

            // ── Step 5: Vertical limit lines at x=a and x=b ─────────────────────
            gc.setStroke(baseColor.deriveColor(0, 1, 1, 0.7));
            gc.setLineWidth(1.2);
            gc.setLineDashes(6, 4);
            for (double limit : new double[]{start, end}) {
                double px = (limit - cX) * s + w / 2;
                if (px >= 0 && px <= w) gc.strokeLine(px, 0, px, h);
            }
            gc.setLineDashes(null); // reset

        } else {
            // ── Step 1: Collect left/right pixel edges for every row ─────────────
            int rowStart = (int) Math.max(0, Math.floor(h / 2 - (end - cY) * s));
            int rowEnd = (int) Math.min(h - 1, Math.ceil(h / 2 - (start - cY) * s));

            double[] pxLefts = new double[rowEnd - rowStart + 1];
            double[] pxRights = new double[rowEnd - rowStart + 1];
            java.util.Arrays.fill(pxLefts, Double.NaN);
            java.util.Arrays.fill(pxRights, Double.NaN);

            for (int row = rowStart; row <= rowEnd; row++) {
                double gy = cY + (h / 2 - row) / s;
                double leftGx, rightGx;

                if (imp1) {
                    java.util.List<Double> roots = solveForX(localEq1.parser, gy, xMin, xMax);
                    if (roots.isEmpty()) continue;
                    rightGx = roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
                    leftGx = roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                } else {
                    double xStep = (xMax - xMin) / w;
                    double prevGx = xMin;
                    double prevDy = localEq1.parser.evaluateExplicit(prevGx) - gy;
                    rightGx = Double.NaN;

                    for (int xi = 1; xi <= (int) w; xi++) {
                        double gx = xMin + xi * xStep;
                        double fy = localEq1.parser.evaluateExplicit(gx);
                        if (Double.isNaN(fy)) {
                            prevGx = gx;
                            prevDy = Double.NaN;
                            continue;
                        }
                        double dy = fy - gy;
                        if (!Double.isNaN(prevDy) && prevDy * dy <= 0) {
                            double t = Math.abs(prevDy) / (Math.abs(prevDy) + Math.abs(dy) + 1e-15);
                            rightGx = prevGx + t * (gx - prevGx);
                        }
                        prevGx = gx;
                        prevDy = dy;
                    }
                    if (Double.isNaN(rightGx)) continue;

                    leftGx = 0;
                    if (localEq2 != null) {
                        if (imp2) {
                            java.util.List<Double> roots = solveForX(localEq2.parser, gy, xMin, xMax);
                            if (!roots.isEmpty())
                                leftGx = roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                        } else {
                            double bound = invertFunction(localEq2.parser, gy, xMin, xMax);
                            if (!Double.isNaN(bound)) leftGx = bound;
                        }
                    }
                }

                pxLefts[row - rowStart] = Math.max(0, (Math.min(leftGx, rightGx) - cX) * s + w / 2);
                pxRights[row - rowStart] = Math.min(w, (Math.max(leftGx, rightGx) - cX) * s + w / 2);
            }

            // ── Step 2: Fill with gradient (horizontal: left=opaque, right=transparent)
            // For Y-axis integration we use a horizontal gradient instead
            javafx.scene.paint.LinearGradient hGradient = new javafx.scene.paint.LinearGradient(
                    0, 0, w, 0,
                    false,
                    javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, baseColor.deriveColor(0, 1, 1, 0.55)),
                    new javafx.scene.paint.Stop(1, baseColor.deriveColor(0, 1, 1, 0.08))
            );

            gc.beginPath();
            boolean started = false;
            for (int i = 0; i < pxLefts.length; i++) {
                if (Double.isNaN(pxLefts[i])) continue;
                if (!started) {
                    gc.moveTo(pxLefts[i], rowStart + i);
                    started = true;
                } else gc.lineTo(pxLefts[i], rowStart + i);
            }
            for (int i = pxRights.length - 1; i >= 0; i--) {
                if (Double.isNaN(pxRights[i])) continue;
                gc.lineTo(pxRights[i], rowStart + i);
            }
            gc.closePath();
            gc.setFill(hGradient);
            gc.fill();

            // ── Step 3: Right boundary stroke (along the curve) ─────────────────
            gc.setStroke(boundaryColor);
            gc.setLineWidth(1.8);
            gc.beginPath();
            started = false;
            for (int i = 0; i < pxRights.length; i++) {
                if (Double.isNaN(pxRights[i])) {
                    started = false;
                    continue;
                }
                if (!started) {
                    gc.moveTo(pxRights[i], rowStart + i);
                    started = true;
                } else gc.lineTo(pxRights[i], rowStart + i);
            }
            gc.stroke();

            // ── Step 4: Left boundary stroke (eq2 or y-axis) ────────────────────
            gc.setStroke(localEq2 != null ? localEq2.color.deriveColor(0, 1, 1, 0.8)
                    : baseColor.deriveColor(0, 0.5, 0.8, 0.5));
            gc.setLineWidth(1.2);
            gc.beginPath();
            started = false;
            for (int i = 0; i < pxLefts.length; i++) {
                if (Double.isNaN(pxLefts[i])) {
                    started = false;
                    continue;
                }
                if (!started) {
                    gc.moveTo(pxLefts[i], rowStart + i);
                    started = true;
                } else gc.lineTo(pxLefts[i], rowStart + i);
            }
            gc.stroke();

            // ── Step 5: Horizontal limit lines at y=a and y=b ───────────────────
            gc.setStroke(baseColor.deriveColor(0, 1, 1, 0.7));
            gc.setLineWidth(1.2);
            gc.setLineDashes(6, 4);
            for (double limit : new double[]{start, end}) {
                double py = h / 2 - (limit - cY) * s;
                if (py >= 0 && py <= h) gc.strokeLine(0, py, w, py);
            }
            gc.setLineDashes(null); // reset
        }

        gc.restore();
    }

    // ── Helper: solve f(x,y)=0 for y, given x, searching in [yLo, yHi] ──────────
// Returns all y-roots found (handles multi-valued implicit curves like circles)
    private java.util.List<Double> solveForY(EquationParser parser, double x, double yLo, double yHi) {
        java.util.List<Double> roots = new java.util.ArrayList<>();
        int steps = 400;
        double step = (yHi - yLo) / steps;
        double prevY = yLo;
        double prevF = parser.evaluateImplicit(x, yLo);

        for (int i = 1; i <= steps; i++) {
            double y = yLo + i * step;
            double f = parser.evaluateImplicit(x, y);
            if (Double.isNaN(f)) {
                prevY = y;
                prevF = Double.NaN;
                continue;
            }
            if (!Double.isNaN(prevF) && prevF * f <= 0) {
                // bisect
                double lo = prevY, hi = y;
                for (int b = 0; b < 52; b++) {
                    double mid = (lo + hi) / 2.0;
                    double fm = parser.evaluateImplicit(x, mid);
                    if (fm == 0 || Double.isNaN(fm)) break;
                    if (prevF * fm <= 0) hi = mid;
                    else lo = mid;
                }
                roots.add((lo + hi) / 2.0);
            }
            prevY = y;
            prevF = f;
        }
        return roots;
    }

    // ── Helper: solve f(x,y)=0 for x, given y, searching in [xLo, xHi] ──────────
    private java.util.List<Double> solveForX(EquationParser parser, double y, double xLo, double xHi) {
        java.util.List<Double> roots = new java.util.ArrayList<>();
        int steps = 400;
        double step = (xHi - xLo) / steps;
        double prevX = xLo;
        double prevF = parser.evaluateImplicit(xLo, y);

        for (int i = 1; i <= steps; i++) {
            double x = xLo + i * step;
            double f = parser.evaluateImplicit(x, y);
            if (Double.isNaN(f)) {
                prevX = x;
                prevF = Double.NaN;
                continue;
            }
            if (!Double.isNaN(prevF) && prevF * f <= 0) {
                double lo = prevX, hi = x;
                for (int b = 0; b < 52; b++) {
                    double mid = (lo + hi) / 2.0;
                    double fm = parser.evaluateImplicit(mid, y);
                    if (fm == 0 || Double.isNaN(fm)) break;
                    if (prevF * fm <= 0) hi = mid;
                    else lo = mid;
                }
                roots.add((lo + hi) / 2.0);
            }
            prevX = x;
            prevF = f;
        }
        return roots;
    }

    private double calculateSimpson(double a, double b, boolean isX) {
        if (eq1 == null || eq1.parser == null) return 0;

        int n = 1000;
        double h = (b - a) / n;

        boolean eq1Implicit = (eq1.parser.eqtype == EquationParser.EqType.Implicit);
        boolean eq2Implicit = eq2 != null && eq2.parser != null && (eq2.parser.eqtype == EquationParser.EqType.Implicit);

        double yLo = localPlotter.getCenterY() - (localPlotter.getHeight() / 2) / localPlotter.getScale();
        double yHi = localPlotter.getCenterY() + (localPlotter.getHeight() / 2) / localPlotter.getScale();
        double xLo = localPlotter.getCenterX() - (localPlotter.getWidth() / 2) / localPlotter.getScale();
        double xHi = localPlotter.getCenterX() + (localPlotter.getWidth() / 2) / localPlotter.getScale();

        java.util.function.DoubleUnaryOperator diff;

        if (isX) {
            // Integrate [f(x) - g(x)] dx
            diff = (x) -> {
                double y1, y2;

                if (eq1Implicit) {
                    // For implicit: take the max y root (top half) minus min y root (bottom half)
                    // so the integral counts the full vertical span at each x
                    java.util.List<Double> roots = solveForY(eq1.parser, x, yLo, yHi);
                    if (roots.isEmpty()) return 0;
                    y1 = roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble()
                            - roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                    // y1 is the full height span — integrate directly (no subtraction of eq2 below)
                    return Double.isNaN(y1) ? 0 : y1;
                } else {
                    y1 = eq1.parser.evaluateExplicit(x);
                }

                if (eq2 == null || eq2.parser == null) {
                    y2 = 0;
                } else if (eq2Implicit) {
                    java.util.List<Double> roots = solveForY(eq2.parser, x, yLo, yHi);
                    y2 = roots.isEmpty() ? 0
                            : roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble()
                            - roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                } else {
                    y2 = eq2.parser.evaluateExplicit(x);
                }

                if (Double.isNaN(y1)) y1 = 0;
                if (Double.isNaN(y2)) y2 = 0;
                return y1 - y2;
            };
        } else {
            // Integrate [x_right(y) - x_left(y)] dy
            diff = (y) -> {
                double x1, x2;

                if (eq1Implicit) {
                    java.util.List<Double> roots = solveForX(eq1.parser, y, xLo, xHi);
                    if (roots.isEmpty()) return 0;
                    x1 = roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble()
                            - roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                    return Double.isNaN(x1) ? 0 : x1;
                } else {
                    x1 = invertFunction(eq1.parser, y, xLo, xHi);
                }

                if (eq2 == null || eq2.parser == null) {
                    x2 = 0;
                } else if (eq2Implicit) {
                    java.util.List<Double> roots = solveForX(eq2.parser, y, xLo, xHi);
                    x2 = roots.isEmpty() ? 0
                            : roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble()
                            - roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                } else {
                    x2 = invertFunction(eq2.parser, y, xLo, xHi);
                }

                if (Double.isNaN(x1)) x1 = 0;
                if (Double.isNaN(x2)) x2 = 0;
                return x1 - x2;
            };
        }

        double sum = diff.applyAsDouble(a) + diff.applyAsDouble(b);
        for (int i = 1; i < n; i++) {
            double val = a + i * h;
            sum += (i % 2 != 0 ? 4 : 2) * diff.applyAsDouble(val);
        }
        return sum * (h / 3.0);
    }

    /**
     * Numerically inverts f(x) = targetY by scanning xMin→xMax for a sign change,
     * then bisecting to find x where f(x) ≈ targetY.
     * Returns NaN if no crossing is found in the given range.
     */
    private double invertFunction(EquationParser parser, double targetY, double xMin, double xMax) {
        int scanSteps = 2000;
        double step = (xMax - xMin) / scanSteps;
        double bestX = Double.NaN;
        double bestDist = Double.MAX_VALUE;

        double prevX = xMin;
        double prevDiff = parser.evaluateExplicit(xMin) - targetY;

        for (int i = 1; i <= scanSteps; i++) {
            double x = xMin + i * step;
            double fx = parser.evaluateExplicit(x);
            if (Double.isNaN(fx)) {
                prevX = x;
                prevDiff = Double.NaN;
                continue;
            }

            double d = fx - targetY;

            // Sign change → bisect for precision
            if (!Double.isNaN(prevDiff) && prevDiff * d <= 0) {
                double lo = prevX, hi = x;
                for (int b = 0; b < 52; b++) {   // 52 iterations → ~machine epsilon
                    double mid = (lo + hi) / 2.0;
                    double fmid = parser.evaluateExplicit(mid) - targetY;
                    if (fmid == 0) {
                        lo = hi = mid;
                        break;
                    }
                    if (prevDiff * fmid <= 0) hi = mid;
                    else lo = mid;
                }
                double crossX = (lo + hi) / 2.0;
                double dist = Math.abs(parser.evaluateExplicit(crossX) - targetY);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestX = crossX;
                }
            }
            prevX = x;
            prevDiff = d;
        }
        return bestX;
    }

    private HBox createNav() {
        Button home = new Button("Home");
        home.setOnAction(e -> {
            localPlotter.reset();
            refresh();
        });

        HBox h = new HBox(10, home);
        h.getStyleClass().add("nav-holder");
        h.setAlignment(Pos.CENTER);
        h.setPadding(new Insets(10));
        return h;
    }
}