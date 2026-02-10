package org.example.equation_plotter;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

public class EquationParser {
    private Expression exp;
    private Argument argx;
    private Argument argy;
    private boolean isImplicit = false;

    public EquationParser(String eq) {
        // Handles circles, parabolas, etc. (Implicit: x^2 + y^2 = 9)
        eq = eq.replaceAll("\\s+", "");  // deletes whitespace ("y = " -> "y=")
        if (eq.contains("=") && !eq.trim().startsWith("y=") && !eq.trim().startsWith("f(x)=")) {
            String[] part = eq.split("=");

            // --- CRASH FIX START ---
            // If the user types "x=", split gives 1 part. We must handle this safely.
            if (part.length < 2 || part[0].trim().isEmpty() || part[1].trim().isEmpty()) {
                this.argx = new Argument("x");
                this.argy = new Argument("y");
                // Create a "dummy" expression that returns NaN (Not a Number)
                // This prevents the plotter from drawing anything, but stops the crash.
                this.exp = new Expression("NaN", argx, argy);
                isImplicit = true;
                return;
            }
            // --- CRASH FIX END ---

            String impEq = ("( " + part[0] + " ) - ( " + part[1] + " )");
            this.argx = new Argument("x");
            this.argy = new Argument("y");
            this.exp = new Expression(impEq, argx, argy);
            isImplicit = true;
        }
        // Handles Standard Functions (Explicit: y = sin(x))
        else {
            String parsed = eq.replaceAll("^(y\\s*=|f\\(x\\)\\s*=)", "").trim();
            this.argx = new Argument("x");
            // Initialize argy even for explicit mode to prevent NullPointer if methods interact
            this.argy = new Argument("y");
            this.exp = new Expression(parsed, argx);
            isImplicit = false;
        }
    }

    public double evaluateExplicit(double xValue) {
        // Safety check
        if (argx == null || exp == null) return Double.NaN;

        argx.setArgumentValue(xValue);
        return exp.calculate();
    }

    public double evaluateImplicit(double x, double y) {
        // Safety check
        if (argx == null || argy == null || exp == null) return Double.NaN;

        argx.setArgumentValue(x);
        argy.setArgumentValue(y);
        try {
            return exp.calculate();
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public boolean isImplicit() {
        return isImplicit;
    }
}