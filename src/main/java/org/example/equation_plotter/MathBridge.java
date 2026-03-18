package org.example.equation_plotter;

import javafx.scene.control.ColorPicker;
import javafx.scene.layout.VBox;

public class MathBridge {
    private final String equationId;
    private final GraphPlotter plotter;
    private final ColorPicker cp;
    private final javafx.animation.PauseTransition debounceTimer;
    private final VBox sliderBox;
    private final EquatorController controller;

    public MathBridge(String equationId, GraphPlotter plotter, ColorPicker cp, VBox sliderBox, EquatorController controller) {
        this.equationId = equationId;
        this.plotter = plotter;
        this.cp = cp;
        this.sliderBox = sliderBox;
        this.controller = controller;
        this.debounceTimer = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
    }

    // 2. MUST BE PUBLIC
    // Inside MathBridge.java -> updateMath method
    public void updateMath(String rawMath) {
        debounceTimer.playFromStart();
        debounceTimer.setOnFinished(e -> {
            String text = rawMath.trim();
            if (text.isEmpty()) {
                plotter.removeEquation(equationId);
                sliderBox.getChildren().clear();
            } else {
                try {
                    // IMPROVED CLEANING LOGIC
                    String javaMath = text.toLowerCase()
                            .replaceAll("\\\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}", "($1)/($2)")
                            .replace("\\sin", "sin")
                            .replace("\\cos", "cos")
                            .replace("\\tan", "tan")
                            .replace("\\sec", "sec")
                            .replace("\\csc", "csc")
                            .replace("\\cot", "cot")
                            .replace("\\operatorname", "")
                            .replace("\\left", "(")
                            .replace("\\right", ")")
                            .replace("\\cdot", "*")
                            .replace("\\frac", "")
                            .replace("{", "(")
                            .replace("}", ")")
                            .replace("\\", "") // Remove remaining backslashes
                            .replace(" ", "");

                    EquationParser parser = new EquationParser(javaMath);
                    plotter.addEquationToHashmap(equationId, javaMath, cp.getValue(), parser.getEqType());


                    plotter.refreshEquationData(equationId);
                    plotter.draw();

                    EquationData data = plotter.getEquation(equationId);
                    if (data != null && data.parser != null) {
                        controller.createSlidersBridge(data.parser, sliderBox, equationId);
                        if (data.eqType == EquationParser.EqType.Polar || data.eqType == EquationParser.EqType.Parametric) {
                            controller.createPolarRangeControls(sliderBox, equationId); // new method in controller
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
}
