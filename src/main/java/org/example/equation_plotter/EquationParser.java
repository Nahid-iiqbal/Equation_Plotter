package org.example.equation_plotter;

import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EquationParser {

    private final Map<Character, Parameter> parameters = new HashMap<>();
    private Node mathExpr;
    private Node polarExpr;
    public EqType eqtype = EqType.Explicit;
    private Node paramXExpr;
    private Node paramYExpr;
    private Node limitExpr;
    private boolean isLinearInY = false;
    private boolean hasLimit = false;
    private boolean isValid = true;
    private String paramX, paramY;

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

            if (mathPart.contains("t") && mathPart.contains(",") && mathPart.startsWith("(") && mathPart.endsWith(")")) {
                eqtype = EqType.Parametric;
                mathPart = mathPart.replace("(", "").replace(")", "");
                String[] parts = mathPart.split(",");

                this.paramX = parts[0].replace("t", "x");
                this.paramY = parts[1].replace("t", "x");

                System.out.println("Parametric: " + this.paramX + ", " + this.paramY + " " + eqtype);
            } else if (mathPart.matches("^r\\s*=.*")) {
                eqtype = EqType.Polar;
                // extract right-hand side after r=
                String rhs = mathPart.substring(mathPart.indexOf('=') + 1).trim();
                // normalize theta tokens so parser sees x as the variable (theta -> x)
                rhs = rhs.replace("θ", "t").replace("t", "x");
                // Also replace standalone 't' with 'x' (word boundary)
//                rhs = rhs.replaceAll("\\t\\b", "x");
                mathPart = rhs;
            } else if (mathPart.contains("=")) {
                String[] parts = mathPart.split("=");
                if (parts.length == 2) {
                    // Rearranges everything to one side: (left) - (right) = 0
                    mathPart = "(" + parts[0] + ") - (" + parts[1] + ")";
                }
                eqtype = EqType.Implicit;
            } else {
                mathPart = mathPart.replaceAll("^(y\\s*=|f\\(x\\)\\s*=)", "").trim();
                eqtype = EqType.Explicit;
            }

            detectParameters(mathPart);

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

    public boolean isImplicit() {
        return eqtype == EqType.Implicit;
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

    private Points points;

    private void detectParameters(String expr) {
        parameters.clear(); // Ensure old sliders are removed

        // Ensure all multi-letter functions are stripped before checking for variables
        String cleanedExpr = expr.toLowerCase()
                .replaceAll("arc|sec|csc|cot|sin|cos|tan|sqrt|cbrt|abs|log|ln|exp|floor|ceil|round|sign|pi", "");

        Pattern p = Pattern.compile("[a-z]");
        Matcher m = p.matcher(cleanedExpr);
        while (m.find()) {
            char c = m.group().charAt(0);
            if (c == 'x' || c == 'y' || c == 'e' || c == 'r' || c == 't') continue;

            parameters.put(c, new Parameter());
        }
    }

    public double evaluatePolar(double theta) {
        if (!isValid || eqtype != EqType.Polar || mathExpr == null) return Double.NaN;
        try {
            // treat parser x as theta
            double r = mathExpr.eval(theta, 0);
            // Note: for polar domain restrictions we will use EquationData.thetaMin/max (UI-driven)
            return r;
        } catch (Exception e) {
            return Double.NaN;
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

    public double[] evaluateParametric(double t) {
        double[] nan = {Double.NaN, Double.NaN};
        if (!isValid) return nan;

        try {
            double px = paramXExpr.eval(t, 0);
            double py = paramYExpr.eval(t, 0);
            double r[] = {px, py};

            return r;

        } catch (Exception e) {
            return nan;
        }
    }

    public EqType getEqType() {
        return eqtype;
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


    public enum EqType {Implicit, Explicit, Polar, Parametric}

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
                    // Implicit multiplication support
                    // If the next char is a digit, letter, or '(', it's a factor
                    // We do NOT include '+' or '-' here to avoid conflict with addition/subtraction
                    if (ch == '(' || (ch >= '0' && ch <= '9') || ch == '.' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
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
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) nextChar();
                String rawName = str.substring(startPos, this.pos).toLowerCase();

                // 1. Clean up MathLive UI artifacts (\left and \right)
                String name = rawName.replace("left", "").replace("right", "");

                if (eat('(')) {
                    Node a = parseExpression();
                    eat(')');
                    // 2. Direct function match (e.g., sin(...))
                    xNode = buildFunctionNode(name, a);
                } else {
                    // 3. Handle implicit strings like "sinx", "xsiny", "pi", or standalone "sin"
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

// --- HELPER METHODS ---

        private Node buildFunctionNode(String name, Node a) {
            return switch (name) {
                case "sin" -> (X, Y) -> Math.sin(a.eval(X, Y));
                case "cos" -> (X, Y) -> Math.cos(a.eval(X, Y));
                case "tan" -> (X, Y) -> Math.tan(a.eval(X, Y));
                case "sec" -> (X, Y) -> 1.0 / Math.cos(a.eval(X, Y));
                case "csc" -> (X, Y) -> 1.0 / Math.sin(a.eval(X, Y));
                case "cot" -> (X, Y) -> 1.0 / Math.tan(a.eval(X, Y));
                case "asin", "arcsin" -> (X, Y) -> Math.asin(a.eval(X, Y));
                case "acos", "arccos" -> (X, Y) -> Math.acos(a.eval(X, Y));
                case "atan", "arctan" -> (X, Y) -> Math.atan(a.eval(X, Y));
                case "asec", "arcsec" -> (X, Y) -> Math.acos(1.0 / a.eval(X, Y));
                case "acsc", "arccsc" -> (X, Y) -> Math.asin(1.0 / a.eval(X, Y));
                case "acot", "arccot" -> (X, Y) -> Math.atan(1.0 / a.eval(X, Y));
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

            // Order matters: check longest function names first
            String[] funcs = {"asec", "arcsec", "acsc", "arccsc", "acot", "arccot", "sec", "csc", "cot", "arcsin", "arccos", "arctan", "signum", "asin", "acos", "atan", "sinh", "cosh", "tanh", "sqrt", "cbrt", "floor", "round", "sign", "ceil", "sin", "cos", "tan", "abs", "log", "exp", "ln"};

            while (i < name.length()) {
                Node part = null;
                boolean foundFunc = false;

                // Check if a mathematical function is embedded in the string
                for (String func : funcs) {
                    if (name.startsWith(func, i)) {
                        foundFunc = true;
                        int nextIdx = i + func.length();
                        Node argNode;

                        if (nextIdx < name.length()) {
                            // Case 1: The argument is attached directly (e.g., "x" in "sinx")
                            argNode = parseImplicitLetters(name.substring(nextIdx));
                            i = name.length();
                        } else {
                            // Case 2: Function is at the end of this letter block (e.g., "sin 2")
                            // Instead of parseFactor(), we peek at the next factor in the expression
                            // BUT we can't call parseFactor() here because we are inside parseImplicitLetters
                            // which is called by parseFactor. This would be infinite recursion if not careful.
                            // However, parseImplicitLetters is only called when we have a string of letters.
                            // If "sin" is at the end of "xsin", the argument must be the NEXT factor in the stream.
                            // So we return a special node that consumes the next factor? No, that's complex.

                            // SIMPLIFICATION: If a function is at the end of a letter block, 
                            // we assume the argument follows immediately in the stream.
                            // We return a node that, when evaluated, is just the function wrapper,
                            // but we need to consume the argument NOW.
                            // But we can't consume from the stream here easily because we are processing a substring.

                            // ACTUALLY: The logic in parseFactor calls parseImplicitLetters(name).
                            // If 'name' ends with "sin", we are stuck.
                            // We need to signal to parseFactor that we consumed "sin" but need an argument.

                            // Let's assume for now that implicit functions inside a string must have their argument
                            // inside the string too (e.g. "sinx").
                            // If someone types "sin 2", 'name' will be "sin".
                            // In that case nextIdx == name.length().

                            // FIX: If we are at the end of the string, we can't parse more from the string.
                            // We must return a node that expects an argument from the main stream?
                            // Or we change how parseFactor works.

                            // Let's try to handle "sin" at end of string by consuming from main stream.
                            // But parseImplicitLetters is static-ish context relative to the stream?
                            // No, it's an instance method of ASTCompiler. We can call parseFactor()!
                            argNode = parseFactor();
                            i = name.length();
                        }

                        part = buildFunctionNode(func, argNode);
                        break;
                    }
                }

                // If no function was found, process constants and variables
                if (!foundFunc) {
                    if (name.startsWith("pi", i)) {
                        part = (X, Y) -> Math.PI;
                        i += 2;
                    } else if (name.charAt(i) == 'e') {
                        part = (X, Y) -> Math.E;
                        i++;
                    } else if (name.charAt(i) == 'x') {
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
                            part = (X, Y) -> 1.0; // Fallback for unknown variables
                        }
                        i++;
                    }
                }

                // Chain components together via multiplication
                if (chain == null) {
                    chain = part;
                } else {
                    Node prev = chain;
                    Node curr = part;
                    chain = (X, Y) -> prev.eval(X, Y) * curr.eval(X, Y);
                }
            }

            return chain == null ? ((X, Y) -> 1.0) : chain;
        }
    }
}
