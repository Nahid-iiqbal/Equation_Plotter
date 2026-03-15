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

import java.util.Map;

public class integralCalc {
    private final GraphPlotter localPlotter;
    private final Label resultLabel = new Label("∫ ≈ 0.0000");
    private final TextField lowerField = new TextField("");
    private final TextField upperField = new TextField("");
    // ✅ ComboBoxes now hold EquationData directly — no string lookup needed
    private final ComboBox<EquationData> sel1 = new ComboBox<>();
    private final ComboBox<EquationData> sel2 = new ComboBox<>();
    private final ToggleGroup axisGroup = new ToggleGroup();
    private final RadioButton xBtn = new RadioButton("X-axis");
    private final RadioButton yBtn = new RadioButton("Y-axis");
    // ✅ Keep a reference to the snapshot taken at open time
    private final Map<String, EquationData> frozenEquations;
    private EquationData eq1, eq2;

    public integralCalc(EquationData initial) {
        this.eq1 = initial;
        this.localPlotter = new GraphPlotter(900, 700);

        // ✅ Snapshot the main map once — we never touch it again after this
        this.frozenEquations = Map.copyOf(GraphPlotter.getCurrentEquations());

        // Populate ComboBoxes with EquationData objects
        sel1.getItems().addAll(frozenEquations.values());
        sel2.getItems().addAll(frozenEquations.values());

        // ✅ Add a "None" sentinel object for sel2
        EquationData noneOption = new EquationData();
        noneOption.raw = "None";
        sel2.getItems().add(0, noneOption);

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

        xBtn.setToggleGroup(axisGroup);
        yBtn.setToggleGroup(axisGroup);
        xBtn.setSelected(true);
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
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        stage.setScene(scene);

        // ✅ Scene is attached now — localPlotter has real dimensions, safe to draw
        stage.show();
        refresh();
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

    private void refresh() {
        // ✅ Get eq1/eq2 directly from the ComboBox selection — no map lookup, no NullPointerException
        this.eq1 = sel1.getValue();
        EquationData sel2Val = sel2.getValue();
        this.eq2 = (sel2Val == null || "None".equals(sel2Val.raw)) ? null : sel2Val;

        try {
            String lowTxt = lowerField.getText().trim();
            String upTxt = upperField.getText().trim();

            if (lowTxt.isEmpty() || upTxt.isEmpty()
                    || lowTxt.equals("-") || upTxt.equals("-") || lowTxt.equals(".")) {
                // ✅ Only touch localPlotter — main window's map is NEVER cleared here
                localPlotter.setPostDrawAction(null);
                rebuildLocalPlotter();
                localPlotter.draw();
                resultLabel.setText("∫ ≈ 0.0000");
                return;
            }

            double a = Double.parseDouble(lowTxt);
            double b = Double.parseDouble(upTxt);
            boolean isX = xBtn.isSelected();

            if (eq1 == null) return;

            double area = calculateSimpson(a, b, isX);
            resultLabel.setText(String.format("Area ≈ %.6f", Math.abs(area)));

            final double fa = a, fb = b;
            final boolean fx = isX;
            localPlotter.setPostDrawAction(() -> drawAdvancedShading(fa, fb, fx));

            rebuildLocalPlotter();
            localPlotter.draw();

        } catch (NumberFormatException ignored) {
            rebuildLocalPlotter();
            localPlotter.draw();
        }
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

        boolean imp1 = localEq1.parser.isImplicit();
        boolean imp2 = localEq2 != null && localEq2.parser.isImplicit();

        double start = Math.min(a, b);
        double end = Math.max(a, b);
        double xMin = cX - (w / 2) / s;
        double xMax = cX + (w / 2) / s;
        double yMin = cY - (h / 2) / s;
        double yMax = cY + (h / 2) / s;

        gc.save();
        gc.setFill(localEq1.color.deriveColor(0, 1, 1, 0.35));

        if (isX) {
            // ── Scan pixel columns between x=start and x=end ──────────────────────
            // For each column: find eq1's y (top) and eq2's y / x-axis (bottom)
            // then fillRect for the vertical strip.
            int colStart = (int) Math.max(0, Math.floor((start - cX) * s + w / 2));
            int colEnd = (int) Math.min(w - 1, Math.ceil((end - cX) * s + w / 2));

            for (int col = colStart; col <= colEnd; col++) {
                double gx = cX + (col - w / 2) / s;

                double topGy, botGy;

                if (imp1) {
                    // Implicit eq1: find all y-roots at this x, use max and min
                    java.util.List<Double> roots = solveForY(localEq1.parser, gx, yMin, yMax);
                    if (roots.isEmpty()) continue;
                    topGy = roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
                    botGy = roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                } else {
                    // Explicit eq1: y = f(x) is the top edge
                    topGy = localEq1.parser.evaluateExplicit(gx);
                    if (Double.isNaN(topGy)) continue;

                    // Bottom edge: eq2 or x-axis (graph y=0)
                    if (localEq2 == null) {
                        botGy = 0; // x-axis
                    } else if (imp2) {
                        java.util.List<Double> roots = solveForY(localEq2.parser, gx, yMin, yMax);
                        botGy = roots.isEmpty() ? 0
                                : roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
                    } else {
                        botGy = localEq2.parser.evaluateExplicit(gx);
                        if (Double.isNaN(botGy)) botGy = 0;
                    }
                }

                // Convert graph-y to pixel-y (higher graph-y = smaller pixel-y)
                double pyTop = h / 2 - (Math.max(topGy, botGy) - cY) * s;
                double pyBot = h / 2 - (Math.min(topGy, botGy) - cY) * s;
                pyTop = Math.max(pyTop, 0);
                pyBot = Math.min(pyBot, h);
                if (pyBot > pyTop) gc.fillRect(col, pyTop, 1, pyBot - pyTop);
            }

        } else {
            // ── Scan pixel rows between y=start and y=end ─────────────────────────
            // For each row: find the horizontal extent of eq1 at that y-value.
            int rowStart = (int) Math.max(0, Math.floor(h / 2 - (end - cY) * s));
            int rowEnd = (int) Math.min(h - 1, Math.ceil(h / 2 - (start - cY) * s));

            for (int row = rowStart; row <= rowEnd; row++) {
                double gy = cY + (h / 2 - row) / s;

                double leftGx, rightGx;

                if (imp1) {
                    // Implicit: find all x-roots at this y, shade between leftmost and rightmost
                    java.util.List<Double> roots = solveForX(localEq1.parser, gy, xMin, xMax);
                    if (roots.isEmpty()) continue;
                    rightGx = roots.stream().mapToDouble(Double::doubleValue).max().getAsDouble();
                    leftGx = roots.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
                } else {
                    // Explicit: find x where f(x) = gy via sign-change scan
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
                            // keep looking for the furthest crossing
                        }
                        prevGx = gx;
                        prevDy = dy;
                    }
                    if (Double.isNaN(rightGx)) continue;

                    // Left bound: eq2's crossing at this y, or the y-axis (x=0)
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

                double pxLeft = Math.max(0, (Math.min(leftGx, rightGx) - cX) * s + w / 2);
                double pxRight = Math.min(w, (Math.max(leftGx, rightGx) - cX) * s + w / 2);
                if (pxRight > pxLeft) gc.fillRect(pxLeft, row, pxRight - pxLeft, 1);
            }
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

        int n = 10000;
        if (n % 2 != 0) n++;
        double h = (b - a) / n;

        boolean eq1Implicit = eq1.parser.isImplicit();
        boolean eq2Implicit = eq2 != null && eq2.parser != null && eq2.parser.isImplicit();

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