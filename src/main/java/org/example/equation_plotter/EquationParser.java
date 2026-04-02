package org.example.equation_plotter;

import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EquationParser {

    private final Map<Character, Parameter> parameters = new HashMap<>();
    private Node mathExpr;
    private Node paramXExpr;
    private Node paramYExpr;
    public EqType eqtype = EqType.Explicit;
    private Node limitExpr;
    private boolean isLinearInY = false;
    private boolean hasLimit = false;
    private boolean isValid = true;
    public boolean isInequality = false;
    private String paramX, paramY;
    private Points points;

    public EquationParser(String fullInput) {
        try {
            Pattern pointPattern = Pattern.compile("^\\s*\\((-?\\d+\\.?\\d*)\\s*,\\s*(-?\\d+\\.?\\d*)\\)\\s*$");
            Matcher pointMatcher = pointPattern.matcher(fullInput);
            if (pointMatcher.matches()) {
                double x = Double.parseDouble(pointMatcher.group(1));
                double y = Double.parseDouble(pointMatcher.group(2));
                this.points = new Points(x, y, Color.WHITE);
                return;
            }

            String mathPart = fullInput.toLowerCase().trim();
            String limitPart = "";

            if (fullInput.contains("{")) {
                mathPart = fullInput.substring(0, fullInput.indexOf("{")).trim();
                limitPart = fullInput.substring(fullInput.indexOf("{") + 1, fullInput.lastIndexOf("}")).trim();
                limitPart = formatLimit(limitPart);
                hasLimit = true;
            }

            if (mathPart.contains("<") || mathPart.contains(">")) {
                isInequality = true;
                eqtype = EqType.Implicit;
            }  else if (mathPart.contains("t") && mathPart.contains(",")
                && mathPart.startsWith("(") && mathPart.endsWith(")")) {
                eqtype = EqType.Parametric;

                // Find top-level comma BEFORE removing parens
                int depth = 0, splitAt = -1;
                for (int i = 0; i < mathPart.length(); i++) {
                    char c = mathPart.charAt(i);
                    if (c == '(') depth++;
                    else if (c == ')') depth--;
                    else if (c == ',' && depth == 1) {
                        splitAt = i;
                        break;
                    }
                }
                if (splitAt == -1) throw new RuntimeException("Invalid parametric");

                // Extract between outer parens using splitAt
                // \b ensures we only replace 't' as a standalone variable, ignoring letters inside words
                this.paramX = mathPart.substring(1, splitAt).trim().replaceAll("\\bt\\b", "x");
                this.paramY = mathPart.substring(splitAt + 1, mathPart.length() - 1).trim().replaceAll("\\bt\\b", "x");

            } else if (mathPart.matches("^r\\s*=.*")) {
                eqtype = EqType.Polar;
                mathPart = mathPart.substring(mathPart.indexOf('=') + 1).trim().replaceAll("\\bt\\b", "x");

            } else if (mathPart.matches("^(y\\s*=|f\\(x\\)\\s*=).*")) {
                mathPart = mathPart.replaceAll("^(y\\s*=|f\\(x\\)\\s*=)", "").trim();
                eqtype = EqType.Explicit;
            } else if (mathPart.contains("=")) {
                String[] parts = mathPart.split("=");
                if (parts.length == 2) {
                    mathPart = "(" + parts[0] + ") - (" + parts[1] + ")";
                }
                eqtype = EqType.Implicit;
            } else {
                eqtype = EqType.Explicit;
            }

            // For parametric, detect free parameters from the component expressions, not raw mathPart
            if (eqtype == EqType.Parametric) {
                detectParameters(paramX + "," + paramY);
            } else {
                detectParameters(mathPart);
            }

            // Compile string into blazing fast Java Lambdas
            if (eqtype == EqType.Parametric) {
                this.paramXExpr = new ASTCompiler(paramX, parameters).parse();
                this.paramYExpr = new ASTCompiler(paramY, parameters).parse();
                this.isValid = true;
                return;
            } else {
                this.mathExpr = new ASTCompiler(mathPart, parameters).parse();
            }

            if (hasLimit) {
                this.limitExpr = new ASTCompiler(limitPart, parameters).parse();
            }

            if (eqtype == EqType.Implicit) checkLinearity();

        } catch (Exception e) {
            isValid = false;
            // Fallback for incomplete equations while typing
            this.mathExpr = (x, y) -> Double.NaN;
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
//                this.isImplicit = false;
            }
        }
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

    public EqType getEqType() {
        return eqtype;
    }

    public enum EqType {Implicit, Explicit, Polar, Parametric}

    private void detectParameters(String expr) {
        parameters.clear(); // Ensure old sliders are removed

        // Ensure all multi-letter functions are stripped before checking for variables
        String cleanedExpr = expr.toLowerCase()
                .replaceAll("arc|sec|csc|cot|sin|cos|tan|sqrt|cbrt|abs|log|ln|exp|floor|ceil|round|sign|pi", "");

        Pattern p = Pattern.compile("[a-zθ]");
        Matcher m = p.matcher(cleanedExpr);
        while (m.find()) {
            char c = m.group().charAt(0);
            if (c == 'x' || c == 'y' || c == 'e' || c == 'r' || c == 'p' || c == 'θ' || c == 't') continue;

            parameters.put(c, new Parameter());
        }
    }

    public Map<Character, Parameter> getParameters() {
        return parameters;
    }

    public double evaluatePolar(double theta) {
        if (!isValid || eqtype != EqType.Polar || mathExpr == null) return Double.NaN;
        try {
            return mathExpr.eval(theta, 0);
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public double[] evaluateParametric(double p) {
        double[] nan = {Double.NaN, Double.NaN};
        if (!isValid) return nan;

        try {
            double px = paramXExpr.eval(p, 0);
            double py = paramYExpr.eval(p, 0);
            return new double[]{px, py};

        } catch (Exception e) {
            return nan;
        }
    }

    private String formatLimit(String limit) {
        String pattern = "([\\w.]+)\\s*(<=|>=|<|>)\\s*([a-zA-Z])\\s*(<=|>=|<|>)\\s*([\\w.]+)";
        return limit.replaceAll(pattern, "$1 $2 $3 && $3 $4 $5")
                .replace(",", " && ")
                .replace("and", " && ");
    }

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
                    if (ch == '(' || (ch >= '0' && ch <= '9') || ch == '.' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == 'θ') {
                        Node a = x, b = parseFactor();
                        x = (X, Y) -> a.eval(X, Y) * b.eval(X, Y);
                    } else {
                        return x;
                    }
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
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == 'θ') {
                while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == 'θ') nextChar();
                String rawName = str.substring(startPos, this.pos).toLowerCase();

                String name = rawName.replace("left", "").replace("right", "");

                if (eat('(')) {
                    Node a = parseExpression();
                    eat(')');
                    xNode = buildFunctionNode(name, a);
                } else {
                    xNode = parseImplicitLetters(name);
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

        private Node buildFunctionNode(String name, Node a) {
            return switch (name) {
                case "sin" -> (X, Y) -> Math.sin(a.eval(X, Y));
                case "cos" -> (X, Y) -> Math.cos(a.eval(X, Y));
                case "tan" -> (X, Y) -> Math.tan(a.eval(X, Y));
                case "sec" -> (X, Y) -> 1.0 / Math.cos(a.eval(X, Y));
                case "csc" -> (X, Y) -> 1.0 / Math.sin(a.eval(X, Y));
                case "cot" -> (X, Y) -> 1.0 / Math.tan(a.eval(X, Y));
                case "arcsin" -> (X, Y) -> Math.asin(a.eval(X, Y));
                case "arccos" -> (X, Y) -> Math.acos(a.eval(X, Y));
                case "arctan" -> (X, Y) -> Math.atan(a.eval(X, Y));
                case "arcsec" -> (X, Y) -> Math.acos(1.0 / a.eval(X, Y));
                case "arccsc" -> (X, Y) -> Math.asin(1.0 / a.eval(X, Y));
                case "arccot" -> (X, Y) -> Math.atan(1.0 / a.eval(X, Y));
                case "sinh" -> (X, Y) -> Math.sinh(a.eval(X, Y));
                case "cosh" -> (X, Y) -> Math.cosh(a.eval(X, Y));
                case "tanh" -> (X, Y) -> Math.tanh(a.eval(X, Y));
                case "sqrt" -> (X, Y) -> Math.sqrt(a.eval(X, Y));
                case "cbrt" -> (X, Y) -> Math.cbrt(a.eval(X, Y));
                case "abs" -> (X, Y) -> Math.abs(a.eval(X, Y));
                case "log" -> (X, Y) -> Math.log10(a.eval(X, Y));
                case "ln" -> (X, Y) -> Math.log(a.eval(X, Y));
                case "exp" -> (X, Y) -> Math.exp(a.eval(X, Y));
                case "floor" -> (X, Y) -> Math.floor(a.eval(X, Y));
                case "ceil" -> (X, Y) -> Math.ceil(a.eval(X, Y));
                case "round" -> (X, Y) -> Math.round(a.eval(X, Y));
                case "sign", "signum" -> (X, Y) -> Math.signum(a.eval(X, Y));
                case "power" -> {
                    if (eat(',')) {
                        Node b = parseExpression();
                        yield (X, Y) -> Math.pow(a.eval(X, Y), b.eval(X, Y));
                    }
                    yield (X, Y) -> a.eval(X, Y);
                }
                default -> throw new RuntimeException("Unknown function: " + name);
            };
        }

        private Node parseImplicitLetters(String name) {
            if (name.isEmpty()) return (X, Y) -> 1.0;

            Node chain = null;
            int i = 0;

            String[] funcs = {"arcsec", "arccsc", "arccot", "sec", "csc", "cot", "sin", "cos", "tan", "arcsin", "arccos", "arctan", "signum", "sinh", "cosh", "tanh", "sqrt", "cbrt", "floor", "round", "sign", "ceil", "abs", "log", "exp", "ln"};

            while (i < name.length()) {
                Node part = null;
                boolean foundFunc = false;

                for (String func : funcs) {
                    if (name.startsWith(func, i)) {
                        foundFunc = true;
                        int nextIdx = i + func.length();
                        Node argNode;

                        if (nextIdx < name.length()) {
                            argNode = parseImplicitLetters(name.substring(nextIdx));
                        } else {
                            argNode = parseFactor();
                        }

                        i = name.length();
                        part = buildFunctionNode(func, argNode);
                        break;
                    }
                }

                if (!foundFunc) {
                    if (name.startsWith("pi", i)) {
                        part = (X, Y) -> Math.PI;
                        i += 2;
                    } else if (name.charAt(i) == 'e') {
                        part = (X, Y) -> Math.E;
                        i++;
                    } else if (name.charAt(i) == 'x' || name.charAt(i) == 'p' || name.charAt(i) == 'θ' || name.charAt(i) == 't') {
                        part = (X, Y) -> X;
                        i++;
                    } else if (name.charAt(i) == 'y') {
                        part = (X, Y) -> Y;
                        i++;
                    } else {
                        final char c = name.charAt(i);
                        if (params != null && params.containsKey(c)) {
                            Parameter p = params.get(c);
                            part = (X, Y) -> p.getArgumentValue();
                        } else {
                            part = (X, Y) -> 1.0;
                        }
                        i++;
                    }
                }

                if (chain == null) {
                    chain = part;
                } else {
                    Node prev = chain;
                    Node curr = part;
                    chain = (X, Y) -> prev.eval(X, Y) * curr.eval(X, Y);
                }
            }

            return chain;
        }
    }
}