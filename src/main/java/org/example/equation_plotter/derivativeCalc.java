package org.example.equation_plotter;

import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.logging.Logger;

public class derivativeCalc {

    private static final Logger LOGGER = Logger.getLogger(derivativeCalc.class.getName());

    private final EquationData eqn;
    private final double screenMinX;
    private final double screenMaxX;
    private EquationData derivativeEqnData;
    private final boolean isLightMode;

    public derivativeCalc(EquationData eqn, double screenMinX, double screenMaxX, boolean isLight) {
        this.eqn = eqn;
        this.screenMinX = screenMinX;
        this.screenMaxX = screenMaxX;
        this.isLightMode = isLight;
    }

    public void calculateDer() {
        if (eqn.parser == null || eqn.eqType != EquationParser.EqType.Explicit) return;

        derivativeEqnData = new EquationData();
        derivativeEqnData.eqType = EquationParser.EqType.Explicit;

        // 1. Get Symbolic Derivative for Display
        String displayLabel;
        try {
            org.matheclipse.core.expression.F.initSymbols();
            org.matheclipse.core.eval.ExprEvaluator util =
                    new org.matheclipse.core.eval.ExprEvaluator(false, (short) 100);

            String symjaInput = toSymjaSyntax(eqn.raw);
            LOGGER.info("Symja input: " + symjaInput);

            String symjaResult = util.eval("D(" + symjaInput + ", x)").toString();
            LOGGER.info("Symja output: " + symjaResult);

            if (symjaResult.startsWith("Hold(") || symjaResult.startsWith("D(")) {
                displayLabel = "d/dx(" + eqn.raw + ")";
            } else {
                displayLabel = toDisplaySyntax(symjaResult);
            }
        } catch (Throwable t) {
            displayLabel = "d/dx(" + eqn.raw + ")";
            LOGGER.warning("Symja failed: " + t.getMessage());
        }
        derivativeEqnData.raw = displayLabel;

        // 2. Numerical Differentiation for Plotting
        final EquationParser originalParser = eqn.parser;
        final double h = 1e-7;

        derivativeEqnData.parser = new EquationParser("x") {
            @Override
            public double evaluateExplicit(double x) {
                // Central Difference Formula: [f(x+h) - f(x-h)] / 2h
                double f1 = originalParser.evaluateExplicit(x + h);
                double f2 = originalParser.evaluateExplicit(x - h);

                if (Double.isNaN(f1) || Double.isNaN(f2)) return Double.NaN;
                return (f1 - f2) / (2.0 * h);
            }
        };

        // Build the cache for the UI thread
        derivativeEqnData.buildCacheExplicit(screenMinX, screenMaxX, 10000);
    }

    public void showInNewWindow() {
        calculateDer();
        if (derivativeEqnData == null) return;

        Stage stage = new Stage();
        stage.setTitle("Derivative: f'(x)");

        GraphPlotter localPlotter = new GraphPlotter(1200, 800);

        EquationData temp = new EquationData(eqn);
        temp.setColor(Color.web("#1EF737"));
        derivativeEqnData.setColor(Color.web("#ed2d63"));

        localPlotter.addEquationToHashmap("original", temp.raw, temp.color);
        localPlotter.addEquationToHashmap("derivative", derivativeEqnData.raw, derivativeEqnData.color);

        // Manually override the derivative equation data with our numerical one
        localPlotter.getEquation("derivative").parser = derivativeEqnData.parser;
        localPlotter.refreshEquationData("derivative");

        final String origRaw = eqn.raw;
        final String derDisplay = derivativeEqnData.raw;
        localPlotter.setPostDrawAction(() -> drawLegend(
                localPlotter.getGraphCanvas().getGraphicsContext2D(),
                origRaw, derDisplay));

        StackPane root = new StackPane(localPlotter);
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(Objects.requireNonNull(
                        getClass().getResource("/org/example/equation_plotter/style.css"))
                .toExternalForm());

        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private void drawLegend(GraphicsContext gc, String origRaw, String derRaw) {
        double boxX = 14, boxY = 14;
        double boxW = Math.max(220, Math.max(origRaw.length(), derRaw.length()) * 8.0 + 60);
        double boxH = 62;

        gc.setFill(isLightMode ? Color.web("#f5f5f5", 0.95) : Color.web("#0d0d1a", 0.88));
        gc.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
        gc.setStroke(isLightMode ? Color.web("#cccccc", 0.8) : Color.web("#00FFFF", 0.25));
        gc.setLineWidth(1);
        gc.strokeRoundRect(boxX, boxY, boxW, boxH, 10, 10);

        gc.setFont(javafx.scene.text.Font.font("JetBrains Mono", 12));
        gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);

        gc.setFill(Color.web("#1EF737"));
        gc.fillRect(boxX + 10, boxY + 18, 18, 3);
        gc.setFill(isLightMode ? Color.BLACK : Color.WHITE);
        gc.fillText("f(x)  = " + origRaw, boxX + 34, boxY + 20);

        gc.setFill(Color.web("#ed2d63"));
        gc.fillRect(boxX + 10, boxY + 40, 18, 3);
        gc.setFill(isLightMode ? Color.BLACK : Color.WHITE);
        gc.fillText("f'(x) = " + derRaw, boxX + 34, boxY + 42);
    }

    /**
     * Converts mXparser-style syntax to Symja-style syntax for input.
     * e.g. sin(x) → Sin[x], x^2 → x^2, ln(x) → Log[x]
     */
    private String toSymjaSyntax(String raw) {
        return raw
                // Functions: parentheses → square brackets, names capitalized
                .replaceAll("\\bsin\\(", "Sin[")
                .replaceAll("\\bcos\\(", "Cos[")
                .replaceAll("\\btan\\(", "Tan[")
                .replaceAll("\\barcsin\\(", "ArcSin[")
                .replaceAll("\\barccos\\(", "ArcCos[")
                .replaceAll("\\barctan\\(", "ArcTan[")
                .replaceAll("\\bsinh\\(", "Sinh[")
                .replaceAll("\\bcosh\\(", "Cosh[")
                .replaceAll("\\btanh\\(", "Tanh[")
                .replaceAll("\\bsec\\(", "Sec[")
                .replaceAll("\\bcsc\\(", "Csc[")
                .replaceAll("\\bcot\\(", "Cot[")
                .replaceAll("\\bsqrt\\(", "Sqrt[")
                .replaceAll("\\babs\\(", "Abs[")
                .replaceAll("\\bexp\\(", "Exp[")
                .replaceAll("\\bln\\(", "Log[")
                .replaceAll("\\blog\\(", "Log[10,")
                // Close brackets: replace ) with ] only for converted functions
                // Safe approach: replace all ) with ] after function conversion
                .replace(")", "]")
                // Constants
                .replaceAll("\\be\\b", "E")
                .replaceAll("\\bpi\\b", "Pi");
    }

    /**
     * Converts Symja output back to clean display syntax.
     * e.g. Cos[x] → cos(x), 2*x → 2x, Sin[x]^2 → sin(x)^2
     */
    private String toDisplaySyntax(String symjaResult) {
        return symjaResult
                // Functions: square brackets → parentheses, names lowercased
                .replaceAll("ArcSin\\[", "arcsin(")
                .replaceAll("ArcCos\\[", "arccos(")
                .replaceAll("ArcTan\\[", "arctan(")
                .replaceAll("Sinh\\[", "sinh(")
                .replaceAll("Cosh\\[", "cosh(")
                .replaceAll("Tanh\\[", "tanh(")
                .replaceAll("Sin\\[", "sin(")
                .replaceAll("Cos\\[", "cos(")
                .replaceAll("Tan\\[", "tan(")
                .replaceAll("Sec\\[", "sec(")
                .replaceAll("Csc\\[", "csc(")
                .replaceAll("Cot\\[", "cot(")
                .replaceAll("Sqrt\\[", "sqrt(")
                .replaceAll("Abs\\[", "abs(")
                .replaceAll("Exp\\[", "exp(")
                .replaceAll("Log\\[10,", "log(")
                .replaceAll("Log\\[", "ln(")
                // Close brackets back to parentheses
                .replace("]", ")")
                // Clean up multiplication signs for display
                .replaceAll("\\*1\\b", "")          // trailing *1
                .replaceAll("\\b1\\*", "")          // leading 1*
                .replaceAll("(?<=\\d)\\*(?=[a-z])", "") // 2*x → 2x
                .replaceAll("(?<=[a-z])\\*(?=[a-z])", "") // x*y → xy (optional)
                // Constants
                .replace("E", "e")
                .replace("Pi", "π")
                .toLowerCase()
                .trim();
    }
}