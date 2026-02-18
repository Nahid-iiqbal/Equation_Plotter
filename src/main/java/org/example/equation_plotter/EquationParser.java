package org.example.equation_plotter;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

public class EquationParser {
    private Expression mathExpr;
    private Expression limitExpr;
    private final Argument xArg;
    private final Argument yArg;
    private boolean isImplicit = false;
    private boolean hasLimit = false;
    private final String rawInput;
    private boolean isLinearInY = false; // Flag for explicit-able implicit equations

    public EquationParser(String fullInput) {
        this.rawInput = fullInput;
        this.xArg = new Argument("x", 0);
        this.yArg = new Argument("y", 0);

        String mathPart = fullInput;
        String limitPart = "";

        if (fullInput.contains("{")) {
            mathPart = fullInput.substring(0, fullInput.indexOf("{")).trim();
            limitPart = fullInput.substring(fullInput.indexOf("{") + 1, fullInput.lastIndexOf("}")).trim();
            limitPart = formatLimit(limitPart);
            hasLimit = true;
        }

        if (mathPart.contains("=") && !mathPart.toLowerCase().startsWith("y=") && !mathPart.toLowerCase().startsWith("f(x)=")) {
            String[] parts = mathPart.split("=");
            if (parts.length == 2) {
                mathPart = "(" + parts[0] + ") - (" + parts[1] + ")";
            }
            isImplicit = true;
        } else {
            mathPart = mathPart.replaceAll("^(y\\s*=|f\\(x\\)\\s*=)", "").trim();
            isImplicit = false;
        }

        this.mathExpr = new Expression(mathPart, xArg, yArg);
        if (hasLimit) {
            this.limitExpr = new Expression(limitPart, xArg, yArg);
        }

        // Automatic conversion: If implicit but linear in y, treat as explicit to avoid lag
        if (isImplicit) {
            checkLinearity();
        }
    }

    private void checkLinearity() {
        // Test linearity by checking if the change in expression value is constant over y
        xArg.setArgumentValue(1.23); // Sample x
        yArg.setArgumentValue(0);
        double v0 = mathExpr.calculate();
        yArg.setArgumentValue(1);
        double v1 = mathExpr.calculate();
        yArg.setArgumentValue(2);
        double v2 = mathExpr.calculate();

        if (!Double.isNaN(v0) && !Double.isNaN(v1) && !Double.isNaN(v2)) {
            double d1 = v1 - v0;
            double d2 = v2 - v1;
            // If the slopes between (0,1) and (1,2) match, it's linear in y
            // Ensure the y-coefficient (d1) is not zero so we can solve for y
            if (Math.abs(d1 - d2) < 1e-9 && Math.abs(d1) > 1e-12) {
                this.isLinearInY = true;
                this.isImplicit = false; // Switch to the fast explicit renderer
            }
        }
    }

    private EquationParser(String rawInput, boolean isImplicit, boolean hasLimit, boolean isLinearInY) {
        this.rawInput = rawInput;
        this.isImplicit = isImplicit;
        this.hasLimit = hasLimit;
        this.isLinearInY = isLinearInY;
        this.xArg = new Argument("x", 0);
        this.yArg = new Argument("y", 0);
    }

    public EquationParser cloneForThread() {
        EquationParser copy = new EquationParser(this.rawInput, this.isImplicit, this.hasLimit, this.isLinearInY);
        copy.mathExpr = new Expression(this.mathExpr.getExpressionString(), copy.xArg, copy.yArg);
        if (this.hasLimit) {
            copy.limitExpr = new Expression(this.limitExpr.getExpressionString(), copy.xArg, copy.yArg);
        }
        return copy;
    }

    public double evaluateImplicit(double x, double y) {
        try {
            this.xArg.setArgumentValue(x);
            this.yArg.setArgumentValue(y);
            if (hasLimit) {
                if (limitExpr.calculate() != 1.0) return Double.NaN;
            }
            return mathExpr.calculate();
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public double evaluateExplicit(double xValue) {
        try {
            double yValue;
            if (isLinearInY) {
                // For linear implicit y: f(x,y) = Ay + B = 0 -> y = -B/A
                // Where B = f(x, 0) and A = f(x, 1) - f(x, 0)
                xArg.setArgumentValue(xValue);
                yArg.setArgumentValue(0);
                double f0 = mathExpr.calculate();
                yArg.setArgumentValue(1);
                double f1 = mathExpr.calculate();
                yValue = -f0 / (f1 - f0);
            } else {
                xArg.setArgumentValue(xValue);
                yValue = mathExpr.calculate();
            }

            if (hasLimit) {
                yArg.setArgumentValue(yValue);
                if (limitExpr.calculate() != 1.0) return Double.NaN;
            }
            return yValue;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private String formatLimit(String limit) {
        String pattern = "([\\w\\d.]+)\\s*(<=|>=|<|>)\\s*([a-zA-Z])\\s*(<=|>=|<|>)\\s*([\\w\\d.]+)";
        return limit.replaceAll(pattern, "$1 $2 $3 && $3 $4 $5")
                .replace(",", " && ")
                .replace("and", " && ");
    }

    public boolean isImplicit() {
        return isImplicit;
    }
}