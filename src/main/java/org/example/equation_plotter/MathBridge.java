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
    public void updateMath(String asciiMath) {
        // Debugging: Watch the console to see the exact text JavaScript is sending to Java!
        System.out.println("Received from Desmos UI: " + asciiMath);

        debounceTimer.playFromStart();

        debounceTimer.setOnFinished(e -> {
            String text = asciiMath.trim();
            if (text.isEmpty()) {
                plotter.removeEquation(equationId);
                sliderBox.getChildren().clear();
            } else {
                try {
                    // Convert ASCII math slightly to match your AST compiler
                    String javaMath = text.replace("⋅", "*").replace("pi", "π");
                    plotter.addEquationToHashmap(equationId, javaMath, cp.getValue());

                    EquationData data = plotter.getEquation(equationId);
                    if (data != null && data.parser != null) {
                        controller.createSlidersBridge(data.parser, sliderBox, equationId);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace(); // Print errors if the compiler fails to parse it
                }
            }
        });
    }
}
