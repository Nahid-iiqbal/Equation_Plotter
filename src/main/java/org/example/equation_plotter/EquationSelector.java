package org.example.equation_plotter;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Map;
import java.util.Objects;

public class EquationSelector {
    private final Map<String, EquationData> equations;
    private final double minX, maxX;

    public EquationSelector(Map<String, EquationData> equations, double minX, double maxX) {
        this.equations = equations;
        this.minX = minX;
        this.maxX = maxX;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Select Equation");

        Label label = new Label("SELECT FUNCTION");
        label.getStyleClass().add("selector-label");

        ComboBox<EquationData> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(equations.values());
        comboBox.getStyleClass().add("selector-combo");

        // Ensure the list items also use the font
        comboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(EquationData item) {
                return (item == null) ? "" : item.raw;
            }

            @Override
            public EquationData fromString(String string) {
                // Not needed for non-editable ComboBoxes
                return null;
            }
        });
        comboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(EquationData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.raw);
                    getStyleClass().add("selector-combo");
                }
            }
        });

        Button calcButton = new Button("CALCULATE DERIVATIVE");
        calcButton.getStyleClass().add("selector-button");
        calcButton.setOnAction(e -> {
            EquationData selectedData = comboBox.getValue();
            if (selectedData != null) {
                // Pass the current theme state to the new window
                boolean isLight = GraphPlotter.getMainInstance().isLightMode;


                derivativeCalc calc = new derivativeCalc(selectedData, minX, maxX, isLight);
                calc.showInNewWindow();
                stage.close();
            }
        });

        VBox layout = new VBox(20, label, comboBox, calcButton);
        layout.setAlignment(Pos.CENTER);
        layout.getStyleClass().add("selector-pane");

        Scene scene = new Scene(layout, 400, 250);
        // Link the existing CSS file
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/org/example/equation_plotter/style.css")).toExternalForm());

        stage.setScene(scene);
        stage.show();
    }
}