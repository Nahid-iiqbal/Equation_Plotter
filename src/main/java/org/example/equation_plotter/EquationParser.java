package org.example.equation_plotter;

import javafx.scene.paint.Color;
import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EquationParser {
    private Expression mathExpr;
    private Expression limitExpr;
    private final Argument xArg;
    private final Argument yArg;
    private boolean isImplicit = false;
    private boolean hasLimit = false;
    private final String rawInput;
    private boolean isLinearInY = false; // Flag for explicit-able implicit equations
    private final Map<Character, Argument> parameters = new HashMap<>();
    private Points points;

    private void detectParameters(String expr) {

        Pattern p = Pattern.compile("[a-zA-Z]");
        Matcher m = p.matcher(expr);

        while (m.find()) {
            char c = m.group().charAt(0);

            if (c == 'x' || c == 'y' || c == 'e') continue; // ignore built-ins

            if (!parameters.containsKey(c)) {
                Argument arg = new Argument(String.valueOf(c), 1); // default = 1
                parameters.put(c, arg);
            }
        }
    }

    public EquationParser(String fullInput) {
        this.rawInput = fullInput;
        this.xArg = new Argument("x", 0);
        this.yArg = new Argument("y", 0);

        // FIX 1: Use Regex for point parsing to avoid conflicts with function brackets like sin(x)
        Pattern pointPattern = Pattern.compile("^\\s*\\((-?\\d+\\.?\\d*)\\s*,\\s*(-?\\d+\\.?\\d*)\\)\\s*$");
        Matcher pointMatcher = pointPattern.matcher(fullInput);
        if (pointMatcher.matches()) {
            double x = Double.parseDouble(pointMatcher.group(1));
            double y = Double.parseDouble(pointMatcher.group(2));
            this.points = new Points(x, y, Color.WHITE);
            return;
        }

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

        detectParameters(mathPart);
        List<Argument> argsList = new ArrayList<>();
        argsList.add(xArg);
        argsList.add(yArg);
        argsList.addAll(parameters.values());
        Argument[] argsArray = argsList.toArray(new Argument[0]);

        this.mathExpr = new Expression(mathPart, argsArray);

        // FIX 2: Correctly initialize limitExpr with the limit string and full argument list
        if (hasLimit) {
            this.limitExpr = new Expression(limitPart, argsArray);
        }

        if (isImplicit) {
            checkLinearity();
        }
    }

    // FIX 3: Getter for points so GraphPlotter can retrieve them
    public Points getPoints() {
        return points;
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

        // Standard initialization for older mXparser versions
        copy.mathExpr = new Expression(this.mathExpr.getExpressionString(), copy.xArg, copy.yArg);

        // If disableCheckingSyntax is missing, manually check once to "prime" the expression
        if (copy.mathExpr.checkSyntax()) {
            // Successfully primed
        }

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

    public Map<Character, Argument> getParameters() {
        return parameters;
    }
}