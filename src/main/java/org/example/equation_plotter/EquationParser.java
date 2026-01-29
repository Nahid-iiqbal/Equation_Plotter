package org.example.equation_plotter;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

public class EquationParser {
    private final Expression exp;
    private final Argument arg;

    public EquationParser(String eq) {
        String parsed = eq.replaceAll("^(y\\s*=|f\\(x\\)\\s*=)", "").trim();
        this.arg = new Argument("x");
        this.exp = new Expression(parsed, arg);
    }

    public double evaluate(double xValue) {
        arg.setArgumentValue(xValue);
        return exp.calculate();
    }
}
