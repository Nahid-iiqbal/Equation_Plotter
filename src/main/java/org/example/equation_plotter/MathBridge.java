package org.example.equation_plotter;

import javafx.scene.control.ColorPicker;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MathBridge {

    private static final Logger LOGGER = Logger.getLogger(MathBridge.class.getName());

    private final String equationId;
    private final GraphPlotter plotter;
    private final ColorPicker cp;
    private final javafx.animation.PauseTransition debounceTimer;
    private final VBox sliderBox;
    private final EquatorController controller;

    public MathBridge(String equationId, GraphPlotter plotter, ColorPicker cp,
                      VBox sliderBox, EquatorController controller) {
        this.equationId = equationId;
        this.plotter = plotter;
        this.cp = cp;
        this.sliderBox = sliderBox;
        this.controller = controller;
        this.debounceTimer = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(300));
    }

    private static @NotNull String getJavaMath(String text) {
        String javaMath = text.toLowerCase()
                .replaceAll("\\\\pi", String.valueOf(Math.PI))
                .replaceAll("\\\\theta", "θ")
                .replaceAll("\\\\left\\(", "(").replaceAll("\\\\right\\)", ")")
                .replaceAll("\\\\leq?", "<=")
                .replaceAll("\\\\geq?", ">=")
                .replaceAll("\\\\frac\\{([^{}]*)}\\{([^{}]*)}", "($1)/($2)")
                .replaceAll("\\\\left|\\\\right", "")
                .replaceAll("\\\\cdot", "*")
                .replaceAll("\\\\times", "*")
                .replaceAll("\\\\div", "/")
                .replaceAll("\\\\sqrt\\{([^{}]*)}", "sqrt($1)")
                .replaceAll("\\\\([a-z]+)", "$1")  // \sin→sin, \cos→cos etc.
                .replaceAll("[{}]", "")
                .replaceAll("\\s+", "");


        // Pattern: single letter that is NOT part of a known function, followed by a function name
        javaMath = javaMath.replaceAll(
                "([a-z])(?=(sin|cos|tan|arcsin|arccos|arctan|sinh|cosh|tanh|sec|csc|cot|log|ln|sqrt|exp|abs|floor|ceil|round)(?![a-z]))",
                "$1*"
        );

        // Handle coefficient*variable: digit followed by letter
        javaMath = javaMath.replaceAll("(\\d)([a-z])", "$1*$2");

        // Only insert * if the letter is a single char not preceded by another letter (i.e. not a func name)
        javaMath = javaMath.replaceAll(
                "(?<![a-z])([a-z])\\((?!(sin|cos|tan|arcsin|arccos|arctan|sinh|cosh|tanh|sec|csc|cot|log|ln|sqrt|exp|abs))",
                "$1*(");
        return javaMath;
    }

    public void updateMath(String rawMath) {
        debounceTimer.setOnFinished(e -> {
            String text = rawMath.trim();
            if (text.isEmpty()) {
                plotter.removeEquation(equationId);
                sliderBox.getChildren().clear();
            } else {
                try {
                    String javaMath = getJavaMath(text);

                    EquationParser parser = new EquationParser(javaMath);
                    plotter.addEquationToHashmap(equationId, javaMath, cp.getValue(),
                            parser.getEqType());

                    plotter.refreshEquationData(equationId);
                    plotter.draw();

                    EquationData data = plotter.getEquation(equationId);
                    if (data != null && data.parser != null) {
                        controller.createSlidersBridge(data.parser, sliderBox, equationId);

                        if (data.eqType == EquationParser.EqType.Polar || data.eqType == EquationParser.EqType.Parametric) {
                            controller.createRangeControls(sliderBox, equationId);
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Failed to parse/plot equation: " + rawMath, ex);
                }
            }
        });
        debounceTimer.playFromStart();
    }
}
