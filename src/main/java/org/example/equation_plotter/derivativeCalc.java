package org.example.equation_plotter;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.interfaces.IExpr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class derivativeCalc {
    private final EquationData eqn;
    private final double screenMinX;
    private final double screenMaxX;
    private final GraphPlotter gp;
    private EquationData derivativeEqnData; // Stores the new symbolic derivative data

    public derivativeCalc(EquationData eqn, double screenMinX, double screenMaxX) {
        this.eqn = eqn;
        this.screenMinX = screenMinX;
        this.screenMaxX = screenMaxX;
        this.gp = new GraphPlotter(1000, 750);
    }

    public static String addBracketsToTrig(String input) {
        if (input == null || input.isEmpty()) return input;

        // Pattern matches common trig/math functions followed by a variable or number
        // Group 1: The function name
        // Group 2: The argument (x, y, or numbers)
        String regex = "(sin|cos|tan|sec|csc|cot|asin|acos|atan|log|ln)\\s*([xy\\d.]+)";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);

        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            // Append the text before the match
            sb.append(input, lastEnd, matcher.start());

            // Reconstruct with brackets: function(argument)
            sb.append(matcher.group(1)).append("(").append(matcher.group(2)).append(")");

            lastEnd = matcher.end();
        }

        // Append the remaining text
        sb.append(input.substring(lastEnd));

        return sb.toString();
    }

    public void calculateDer() {
        if (eqn.parser.isImplicit()) return;

        // 1. Get the symbolic derivative string
        String rawInput = addBracketsToTrig(eqn.raw);
        String derivativeString = SymbolicMathUtils.getDerivative(rawInput);

        // 2. Create a new EquationData object for the derivative
        derivativeEqnData = new EquationData();
        derivativeEqnData.raw = derivativeString;
        derivativeEqnData.parser = new EquationParser(derivativeString); //

        // Set a distinct color (e.g., a brighter version of the original)
        //derivativeEqnData.setColor(eqn.color.deriveColor(0, 1.2, 1.2, 1.0)); //

        // 3. Build the cache for the derivative so it can be plotted
        derivativeEqnData.buildCacheExplicit(screenMinX, screenMaxX, 10000); //
    }

    public void showInNewWindow() {
        // Ensure the derivative is calculated before showing
        calculateDer();
        if (derivativeEqnData == null) return;

        Stage stage = new Stage();
        // Set title to show the derived equation (e.g., "3*x^2")
        stage.setTitle("Derivative: " + derivativeEqnData.raw);

        double w = 1000;
        double h = 750;
        Canvas canvas = new Canvas(w, h);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#1e1e1e"));
        gc.fillRect(0, 0, w, h);
        // Use the existing GraphPlotter logic to draw the coordinate system and the curve
        if (gp.polarGrid())
            gp.drawPolarGrid(gc, w, h);
        else
            gp.drawCartesianGrid(gc, w, h);
        EquationData temp = new EquationData(eqn);
        temp.setColor(Color.web("#1EF737"));
        derivativeEqnData.setColor(Color.web("#ed2d63"));
        gp.drawFunction_Explicit(gc, w, h, temp);
        drawDer(gc);


        StackPane root = new StackPane(canvas);
        stage.setScene(new Scene(root, w, h));
        stage.show();
    }

    public void drawDer(GraphicsContext gc) {
        if (derivativeEqnData == null) return;

        // Use the plotter's existing line-drawing logic to plot the derivative curve
        // This assumes your GraphPlotter has a method to plot EquationData
        gp.drawFunction_Explicit(gc, 1000, 750, derivativeEqnData);
    }

    public static class SymbolicMathUtils {
        public static String getDerivative(String rawInput) {
            try {
                // Passing 'true' to the constructor enables relaxed (case-insensitive) mode
                ExprEvaluator util = new ExprEvaluator();

                // Differentiate the input string with respect to x
                IExpr result = util.eval("diff(" + rawInput + ", x)");

                String der = result.toString();

                // Cleanup Symja's output for your EquationParser
                der = der.toLowerCase(); // Handle Case Sensitivity (Sin -> sin)
                der = der.replace(" ", "*"); // Handle missing multiplication (3 x -> 3*x)

                return der;
            } catch (Exception e) {
                return "0";
            }
        }
    }
}
