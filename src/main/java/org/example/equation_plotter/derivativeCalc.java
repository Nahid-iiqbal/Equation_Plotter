package org.example.equation_plotter;

import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.interfaces.IExpr;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class derivativeCalc {

    private static final Logger LOGGER = Logger.getLogger(derivativeCalc.class.getName());

    private final EquationData eqn;
    private final double screenMinX;
    private final double screenMaxX;
    private EquationData derivativeEqnData;

    public derivativeCalc(EquationData eqn, double screenMinX, double screenMaxX) {
        this.eqn = eqn;
        this.screenMinX = screenMinX;
        this.screenMaxX = screenMaxX;
    }

    // ── Trig bracket normalizer ───────────────────────────────────────────────
    // Symja needs sin(x) not sinx — this ensures arguments are wrapped
    public static String addBracketsToTrig(String input) {
        if (input == null || input.isEmpty()) return input;

        String regex = "(sin|cos|tan|sec|csc|cot|asin|acos|atan|log|ln)\\s*([xy\\d.]+)";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);

        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(input, lastEnd, matcher.start());
            sb.append(matcher.group(1)).append("(").append(matcher.group(2)).append(")");
            lastEnd = matcher.end();
        }
        sb.append(input.substring(lastEnd));
        return sb.toString();
    }

    // ── Derivative calculation ────────────────────────────────────────────────
    public void calculateDer() {
        if (eqn.parser == null || eqn.parser.isImplicit()) return;

        String rawInput = addBracketsToTrig(eqn.raw);
        String derivativeString = SymbolicMathUtils.getDerivative(rawInput);

        derivativeEqnData = new EquationData();
        derivativeEqnData.raw = derivativeString;
        derivativeEqnData.parser = new EquationParser(derivativeString);
        derivativeEqnData.buildCacheExplicit(screenMinX, screenMaxX, 10000);
    }

    // ── Interactive derivative window ─────────────────────────────────────────
    public void showInNewWindow() {
        calculateDer();
        if (derivativeEqnData == null) return;

        Stage stage = new Stage();
        stage.setTitle("Derivative: f'(x) = " + derivativeEqnData.raw);

        GraphPlotter localPlotter = new GraphPlotter(1200, 800);

        EquationData temp = new EquationData(eqn);
        temp.setColor(Color.web("#1EF737"));
        derivativeEqnData.setColor(Color.web("#ed2d63"));

        localPlotter.addEquationToHashmap("original", temp.raw, temp.color);
        localPlotter.addEquationToHashmap("derivative", derivativeEqnData.raw, derivativeEqnData.color);

        // ── Legend via postDrawAction — always on top ─────────────────────────
        final String origRaw = eqn.raw;
        final String derRaw = derivativeEqnData.raw;

        localPlotter.setPostDrawAction(() -> drawLegend(
                localPlotter.getGraphCanvas().getGraphicsContext2D(),
                origRaw, derRaw));

        StackPane root = new StackPane(localPlotter);
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(Objects.requireNonNull(
                        getClass().getResource("/org/example/equation_plotter/style.css"))
                .toExternalForm());

        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    // ── Legend drawing — extracted for clarity ────────────────────────────────
    private void drawLegend(GraphicsContext gc, String origRaw, String derRaw) {
        double boxX = 14;
        double boxY = 14;
        double boxW = Math.max(220,
                Math.max(origRaw.length(), derRaw.length()) * 8.0 + 60);
        double boxH = 62;

        // Dark background
        gc.setFill(Color.web("#0d0d1a", 0.88));
        gc.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);

        // Cyan border
        gc.setStroke(Color.web("#00FFFF", 0.25));
        gc.setLineWidth(1);
        gc.strokeRoundRect(boxX, boxY, boxW, boxH, 10, 10);

        gc.setFont(javafx.scene.text.Font.font("JetBrains Mono", 12));
        gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);

        // f(x) — green swatch + label
        gc.setFill(Color.web("#1EF737"));
        gc.fillRect(boxX + 10, boxY + 18, 18, 3);
        gc.fillText("f(x)  = " + origRaw, boxX + 34, boxY + 20);

        // f'(x) — red swatch + label
        gc.setFill(Color.web("#ed2d63"));
        gc.fillRect(boxX + 10, boxY + 40, 18, 3);
        gc.fillText("f'(x) = " + derRaw, boxX + 34, boxY + 42);
    }

    // ── Symbolic differentiation via Symja ────────────────────────────────────
    public static class SymbolicMathUtils {
        public static String getDerivative(String rawInput) {
            try {
                ExprEvaluator util = new ExprEvaluator();
                IExpr result = util.eval("diff(" + rawInput + ", x)");

                String der = result.toString();
                der = der.toLowerCase();           // Sin -> sin
                der = der.replace(" ", "*");        // 3 x -> 3*x

                return der;
            } catch (Exception e) {
                Logger.getLogger(SymbolicMathUtils.class.getName())
                        .log(Level.WARNING, "Failed to compute derivative of: " + rawInput, e);
                return "0";
            }
        }
    }
}