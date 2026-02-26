package org.example.equation_plotter;

import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EquationParser {

    private final Map<Character, Parameter> parameters = new HashMap<>();
    private Node mathExpr;
    private Node limitExpr;
    private boolean isLinearInY = false;
    private boolean isImplicit = false;
    private boolean hasLimit = false;
    private final String rawInput;
    private boolean isValid = true;

    public EquationParser(String fullInput) {
        this.rawInput = fullInput;

        try {
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

            // Compile string into blazing fast Java Lambdas
            this.mathExpr = new ASTCompiler(mathPart, parameters).parse();
            if (hasLimit) {
                this.limitExpr = new ASTCompiler(limitPart, parameters).parse();
            }

            if (isImplicit) checkLinearity();

        } catch (Exception e) {
            isValid = false;
            // Fallback for incomplete equations while typing
            this.mathExpr = (x, y) -> Double.NaN;
        }
    }

    private Points points;

    private void detectParameters(String expr) {
        // Erase all known math functions and multi-letter constants
        // This prevents the letters in "arcsin" or "floor" from becoming sliders
        String cleanedExpr = expr.toLowerCase()
                .replaceAll("arcsin|arccos|arctan|asin|acos|atan|sinh|cosh|tanh|sin|cos|tan|sqrt|cbrt|abs|log|ln|exp|floor|ceil|round|sign|signum|pi", "");

        Pattern p = Pattern.compile("[a-z]");
        Matcher m = p.matcher(cleanedExpr);
        while (m.find()) {
            char c = m.group().charAt(0);
            if (c == 'x' || c == 'y' || c == 'e') continue;
            if (!parameters.containsKey(c)) {
                parameters.put(c, new Parameter());
            }
        }
    }

    private void checkLinearity() {
        if (mathExpr == null) return;
        double v0 = mathExpr.eval(1.23, 0);
        double v1 = mathExpr.eval(1.23, 1);
        double v2 = mathExpr.eval(1.23, 2);

        if (!Double.isNaN(v0) && !Double.isNaN(v1) && !Double.isNaN(v2)) {
            double d1 = v1 - v0;
            double d2 = v2 - v1;
            if (Math.abs(d1 - d2) < 1e-9 && Math.abs(d1) > 1e-12) {
                this.isLinearInY = true;
                this.isImplicit = false;
            }
        }
    }

    public EquationParser cloneForThread() {
        return this;
    }

    public Points getPoints() {
        return points;
    }

    public double evaluateImplicit(double x, double y) {
        if (!isValid) return Double.NaN;
        try {
            if (hasLimit && limitExpr.eval(x, y) != 1.0) return Double.NaN;
            return mathExpr.eval(x, y);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public double evaluateExplicit(double xValue) {
        if (!isValid) return Double.NaN;
        try {
            double yValue;
            if (isLinearInY) {
                double f0 = mathExpr.eval(xValue, 0);
                double f1 = mathExpr.eval(xValue, 1);
                yValue = -f0 / (f1 - f0);
            } else {
                yValue = mathExpr.eval(xValue, 0);
            }

            if (hasLimit && limitExpr.eval(xValue, yValue) != 1.0) {
                return Double.NaN;
            }
            return yValue;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public boolean isValid() {
        return isValid;
    }

    public Map<Character, Parameter> getParameters() {
        return parameters;
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

    // --- NATIVE AST INTERFACES ---
    @FunctionalInterface
    public interface Node {
        double eval(double x, double y);
    }

    public static class Parameter {
        private volatile double value = 1.0;

        public double getArgumentValue() {
            return value;
        }

        public void setArgumentValue(double value) {
            this.value = value;
        }
    }

    // =========================================================================
    // NATIVE AST COMPILER (Shunting-Yard / Recursive Descent)
    // =========================================================================
    private static class ASTCompiler {
        private final String str;
        private final Map<Character, Parameter> params;
        private int pos = -1, ch;

        public ASTCompiler(String str, Map<Character, Parameter> params) {
            this.str = str;
            this.params = params;
            nextChar();
        }

        private void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        public Node parse() {
            return parseBoolean();
        }

        private Node parseBoolean() {
            Node x = parseCondition();
            for (; ; ) {
                if (eat('&') && eat('&')) {
                    Node a = x, b = parseCondition();
                    x = (X, Y) -> (a.eval(X, Y) > 0 && b.eval(X, Y) > 0) ? 1.0 : 0.0;
                } else if (eat('|') && eat('|')) {
                    Node a = x, b = parseCondition();
                    x = (X, Y) -> (a.eval(X, Y) > 0 || b.eval(X, Y) > 0) ? 1.0 : 0.0;
                } else {
                    return x;
                }
            }
        }

        private Node parseCondition() {
            Node x = parseExpression();
            for (; ; ) {
                if (eat('<')) {
                    if (eat('=')) {
                        Node a = x, b = parseExpression();
                        x = (X, Y) -> a.eval(X, Y) <= b.eval(X, Y) ? 1 : 0;
                    } else {
                        Node a = x, b = parseExpression();
                        x = (X, Y) -> a.eval(X, Y) < b.eval(X, Y) ? 1 : 0;
                    }
                } else if (eat('>')) {
                    if (eat('=')) {
                        Node a = x, b = parseExpression();
                        x = (X, Y) -> a.eval(X, Y) >= b.eval(X, Y) ? 1 : 0;
                    } else {
                        Node a = x, b = parseExpression();
                        x = (X, Y) -> a.eval(X, Y) > b.eval(X, Y) ? 1 : 0;
                    }
                } else if (eat('=')) {
                    eat('=');
                    Node a = x, b = parseExpression();
                    x = (X, Y) -> Math.abs(a.eval(X, Y) - b.eval(X, Y)) < 1e-9 ? 1 : 0;
                } else return x;
            }
        }

        private Node parseExpression() {
            Node x = parseTerm();
            for (; ; ) {
                if (eat('+')) {
                    Node a = x, b = parseTerm();
                    x = (X, Y) -> a.eval(X, Y) + b.eval(X, Y);
                } else if (eat('-')) {
                    Node a = x, b = parseTerm();
                    x = (X, Y) -> a.eval(X, Y) - b.eval(X, Y);
                } else {
                    return x;
                }
            }
        }

        private Node parseTerm() {
            Node x = parseFactor();
            for (; ; ) {
                if (eat('*')) {
                    Node a = x, b = parseFactor();
                    x = (X, Y) -> a.eval(X, Y) * b.eval(X, Y);
                } else if (eat('/')) {
                    Node a = x, b = parseFactor();
                    x = (X, Y) -> a.eval(X, Y) / b.eval(X, Y);
                } else {
                    return x;
                }
            }
        }

        private Node parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) {
                Node a = parseFactor();
                return (X, Y) -> -a.eval(X, Y);
            }

            Node xNode;
            int startPos = this.pos;
            if (eat('(')) {
                xNode = parseBoolean();
                eat(')');
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                double val = Double.parseDouble(str.substring(startPos, this.pos));
                xNode = (X, Y) -> val;
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) nextChar();
                String name = str.substring(startPos, this.pos).toLowerCase();

                if (eat('(')) {
                    Node a = parseExpression();
                    eat(')');
                    // --- FULL STANDARD SCIENTIFIC LIBRARY SUPPORT ---
                    switch (name) {
                        case "sin":
                            xNode = (X, Y) -> Math.sin(a.eval(X, Y));
                            break;
                        case "cos":
                            xNode = (X, Y) -> Math.cos(a.eval(X, Y));
                            break;
                        case "tan":
                            xNode = (X, Y) -> Math.tan(a.eval(X, Y));
                            break;

                        // Supports both 'asin' and 'arcsin' naming conventions
                        case "asin":
                        case "arcsin":
                            xNode = (X, Y) -> Math.asin(a.eval(X, Y));
                            break;
                        case "acos":
                        case "arccos":
                            xNode = (X, Y) -> Math.acos(a.eval(X, Y));
                            break;
                        case "atan":
                        case "arctan":
                            xNode = (X, Y) -> Math.atan(a.eval(X, Y));
                            break;

                        case "sinh":
                            xNode = (X, Y) -> Math.sinh(a.eval(X, Y));
                            break;
                        case "cosh":
                            xNode = (X, Y) -> Math.cosh(a.eval(X, Y));
                            break;
                        case "tanh":
                            xNode = (X, Y) -> Math.tanh(a.eval(X, Y));
                            break;

                        case "sqrt":
                            xNode = (X, Y) -> Math.sqrt(a.eval(X, Y));
                            break;
                        case "cbrt":
                            xNode = (X, Y) -> Math.cbrt(a.eval(X, Y));
                            break;
                        case "abs":
                            xNode = (X, Y) -> Math.abs(a.eval(X, Y));
                            break;
                        case "log":
                            xNode = (X, Y) -> Math.log10(a.eval(X, Y));
                            break;
                        case "ln":
                            xNode = (X, Y) -> Math.log(a.eval(X, Y));
                            break;
                        case "exp":
                            xNode = (X, Y) -> Math.exp(a.eval(X, Y));
                            break;

                        case "floor":
                            xNode = (X, Y) -> Math.floor(a.eval(X, Y));
                            break;
                        case "ceil":
                            xNode = (X, Y) -> Math.ceil(a.eval(X, Y));
                            break;
                        case "round":
                            xNode = (X, Y) -> Math.round(a.eval(X, Y));
                            break;
                        case "sign":
                        case "signum":
                            xNode = (X, Y) -> Math.signum(a.eval(X, Y));
                            break;
                        default:
                            throw new RuntimeException("Unknown function: " + name);
                    }
                } else {
                    if (name.equals("x")) xNode = (X, Y) -> X;
                    else if (name.equals("y")) xNode = (X, Y) -> Y;
                    else if (name.equals("pi")) xNode = (X, Y) -> Math.PI;
                    else if (name.equals("e")) xNode = (X, Y) -> Math.E;
                    else if (name.length() == 1 && params.containsKey(name.charAt(0))) {
                        Parameter p = params.get(name.charAt(0));
                        xNode = (X, Y) -> p.getArgumentValue();
                    } else {
                        // Support for implicit variables like 'ax' -> a * x
                        Node chain = (X, Y) -> 1.0;
                        for (int i = 0; i < name.length(); i++) {
                            char c = name.charAt(i);
                            Node part;
                            if (c == 'x') part = (X, Y) -> X;
                            else if (c == 'y') part = (X, Y) -> Y;
                            else if (params.containsKey(c)) {
                                Parameter p = params.get(c);
                                part = (X, Y) -> p.getArgumentValue();
                            } else part = (X, Y) -> 1.0;

                            Node prev = chain;
                            chain = (X, Y) -> prev.eval(X, Y) * part.eval(X, Y);
                        }
                        xNode = chain;
                    }
                }
            } else {
                throw new RuntimeException("Unexpected char: " + (char) ch);
            }

            if (eat('^')) {
                Node a = xNode, b = parseFactor();
                xNode = (X, Y) -> Math.pow(a.eval(X, Y), b.eval(X, Y));
            }

            return xNode;
        }
    }
}