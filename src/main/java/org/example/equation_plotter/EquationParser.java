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

    // Public constructor (used once by UI)
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
    }

    // --- KEY FIX: Private constructor for fast cloning ---
    private EquationParser(String rawInput, boolean isImplicit, boolean hasLimit) {
        this.rawInput = rawInput;
        this.isImplicit = isImplicit;
        this.hasLimit = hasLimit;
        this.xArg = new Argument("x", 0);
        this.yArg = new Argument("y", 0);
    }

    // Fixed cloning method
    public EquationParser cloneForThread() {
        // 1. Use private constructor (Fast, no regex)
        EquationParser copy = new EquationParser(this.rawInput, this.isImplicit, this.hasLimit);

        // 2. Re-create expressions attached to the NEW xArg/yArg
        copy.mathExpr = new Expression(this.mathExpr.getExpressionString(), copy.xArg, copy.yArg);

        if (this.hasLimit) {
            copy.limitExpr = new Expression(this.limitExpr.getExpressionString(), copy.xArg, copy.yArg);
        }

        return copy;
    }

    public double evaluateImplicit(double x, double y) {
        try {
            // Set values on the THREAD-LOCAL arguments
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
            xArg.setArgumentValue(xValue);
            double yValue = mathExpr.calculate();

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