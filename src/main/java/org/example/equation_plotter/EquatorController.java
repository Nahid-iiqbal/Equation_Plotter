package org.example.equation_plotter;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class EquatorController {
    @FXML
    private VBox equation_container;
    @FXML
    private AnchorPane graph_container;
    @FXML
    private Button btn_home, btn_zoom_in, btn_zoom_out;
    @FXML
    private BorderPane mainBorderPane;
    @FXML
    private BorderPane sideBar;
    @FXML
    private Button btn_close_sidebar;
    @FXML
    private Button btn_open_sidebar;
    @FXML
    private NavBar navbarController;

    private GraphPlotter graphPlotter;
    private int addEqCount = 0;
    private final List<Color> defaultColors = Arrays.asList(
            Color.RED, Color.BLUE, Color.GREEN,
            Color.ORANGE, Color.PURPLE, Color.BLACK
    );
    private int colorIndex = 0;

    @FXML
    public void initialize() {
        if (navbarController != null) {
            navbarController.setMainController(this);
        }

        addEquation();
        setBtn_home();
        setBtn_zoom_in();
        setBtn_zoom_out();
        initSidebarButtons();
        graphPlotter = new GraphPlotter(graph_container.getPrefWidth(), graph_container.getPrefHeight());
        graphPlotter.widthProperty().bind(graph_container.widthProperty());
        graphPlotter.heightProperty().bind(graph_container.heightProperty());

        graph_container.getChildren().addFirst(graphPlotter);
        graphPlotter.toBack();
    }

    private void initSidebarButtons() {
        FontIcon closeIcon = new FontIcon("fas-angle-double-left");
        closeIcon.setIconColor(Color.web("#00FFFF"));
        closeIcon.setIconSize(12);
        btn_close_sidebar.setGraphic(closeIcon);
        btn_close_sidebar.setText("");

        FontIcon openIcon = new FontIcon("fas-list-ul");
        openIcon.setIconColor(Color.web("#00FFFF"));
        openIcon.setIconSize(14);
        btn_open_sidebar.setGraphic(openIcon);
        btn_open_sidebar.setText("");

        btn_open_sidebar.setVisible(false);
    }

    @FXML
    void closeSidebarPressed(ActionEvent event) {
        mainBorderPane.setLeft(null);
        btn_open_sidebar.setVisible(true);
        AnchorPane.setRightAnchor(btn_home, 10.0);
        AnchorPane.setRightAnchor(btn_zoom_in, 10.0);
        AnchorPane.setRightAnchor(btn_zoom_out, 10.0);
    }

    @FXML
    void openSidebarPressed(ActionEvent event) {
        mainBorderPane.setLeft(sideBar);
        btn_open_sidebar.setVisible(false);
        AnchorPane.setRightAnchor(btn_home, 10.0);
        AnchorPane.setRightAnchor(btn_zoom_in, 10.0);
        AnchorPane.setRightAnchor(btn_zoom_out, 10.0);
    }

    @FXML
    protected void btnAddPressed() {
        addEquation();
    }

    private void addEquation() {
        String id = "eq-" + System.nanoTime();
        TextField equationInput = new TextField();
        VBox paramBox = new VBox(3);
        equationInput.setId(id);
        equationInput.setPromptText("y=f(x)");
        equationInput.getStyleClass().add("glass-input");


        Button btn_rmv = new Button();
        btn_rmv.getStyleClass().add("icon-button");
        FontIcon btn_rmv_icon = new FontIcon("fas-times");
        btn_rmv_icon.setIconColor(Color.web("#ff4444"));
        btn_rmv_icon.setIconSize(18);
        btn_rmv.setGraphic(btn_rmv_icon);

        Color initCol = defaultColors.get(colorIndex % defaultColors.size());
        colorIndex++;
        ColorPicker cp = new ColorPicker(initCol);
        cp.getStyleClass().add("dot-color-picker");
        cp.setOnAction(e -> {
                    graphPlotter.updateEqColor(id, cp.getValue());
                }
        );

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(equationInput, btn_rmv, cp);

        VBox sliderBox = new VBox(5);

        VBox equationBlock = new VBox(5);
        equationBlock.setPadding(new Insets(5, 0, 5, 0));
        equationBlock.getChildren().addAll(topRow, sliderBox);
//        row.getChildren().add(paramBox);

        equationInput.setOnAction(e -> {
            String text = equationInput.getText().trim();
            graphPlotter.addEquationToHashmap(id, text, cp.getValue());

            EquationData data = graphPlotter.getEquation(id);
            createSliders(data.parser, sliderBox, id);
        });


        btn_rmv.setOnAction(event -> {
            equation_container.getChildren().remove(equationBlock);
            graphPlotter.removeEquation(id);
            addEqCount--;
            if (addEqCount == 0) {
                addEquation();
            }
        });

        equation_container.getChildren().add(equationBlock);
        equationInput.requestFocus();
        addEqCount++;
    }

    private void createSliders(EquationParser parser, VBox box, String id) {
        box.getChildren().clear();

        parser.getParameters().forEach((ch, arg) -> {

            // Label showing the current parameter value
            Label lbl = new Label(ch + " = 1");
            lbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

            // Slider with default range -10 to 10 and default value 1
            Slider s = new Slider(-10, 10, 1);
            s.setPrefWidth(300);
            s.setPrefHeight(40);
            s.setShowTickMarks(true);
            s.setShowTickLabels(true);

            // Tiny editable text fields for min and max
            TextField minField = new TextField("-10");
            TextField maxField = new TextField("10");

            minField.setPrefWidth(45);
            maxField.setPrefWidth(45);
            minField.setMaxWidth(45);
            maxField.setMaxWidth(45);
            minField.setStyle("-fx-font-size: 10px; -fx-alignment: center;");
            maxField.setStyle("-fx-font-size: 10px; -fx-alignment: center;");

            // Ensure slider default matches the fields
            s.setMin(-10);
            s.setMax(10);

            // Update slider min when user presses Enter in min field
            minField.setOnAction(e -> {
                try {
                    double newMin = Double.parseDouble(minField.getText());
                    if (newMin < s.getMax()) {
                        s.setMin(newMin);
                        graphPlotter.refreshEquationData(id);
                        graphPlotter.draw();
                    }
                } catch (NumberFormatException ignored) {}
            });

            // Update slider max when user presses Enter in max field
            maxField.setOnAction(e -> {
                try {
                    double newMax = Double.parseDouble(maxField.getText());
                    if (newMax > s.getMin()) {
                        s.setMax(newMax);
                        graphPlotter.refreshEquationData(id);
                        graphPlotter.draw();
                    }
                } catch (NumberFormatException ignored) {}
            });

            // Update parameter value and label while sliding (per equation)
            s.valueProperty().addListener((obs, oldv, newv) -> {
                arg.setArgumentValue(newv.doubleValue());
                lbl.setText(ch + " = " + String.format("%.2f", newv.doubleValue()));
                graphPlotter.refreshEquationData(id);
                graphPlotter.draw();
            });

            // Refresh all equations only when sliding stops
            s.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging) {
                    graphPlotter.refreshAllData();
                    graphPlotter.draw();
                }
            });

            // Layout: min field, slider, max field
            HBox sliderRow = new HBox(5);
            sliderRow.setAlignment(Pos.CENTER_LEFT);
//            HBox.setHgrow(s, Priority.ALWAYS);
            sliderRow.getChildren().addAll(minField, s, maxField);

            // Stack the parameter label and slider row vertically
            VBox sliderBlock = new VBox(3);
            sliderBlock.getChildren().addAll(lbl, sliderRow);

            box.getChildren().add(sliderBlock);
        });
    }

    private void setBtn_home() {
        FontIcon btn_home_icon = new FontIcon("fas-home");
        btn_home_icon.setIconColor(Color.web("#00FFFF"));
        btn_home.setGraphic(btn_home_icon);
        btn_home.setText("");
    }

    private void setBtn_zoom_in() {
        FontIcon btn_zoom_in_icon = new FontIcon("fas-plus");
        btn_zoom_in_icon.setIconColor(Color.web("#00FFFF"));
        btn_zoom_in.setGraphic(btn_zoom_in_icon);
        btn_zoom_in.setText("");
    }

    private void setBtn_zoom_out() {
        FontIcon btn_zoom_out_icon = new FontIcon("fas-minus");
        btn_zoom_out_icon.setIconColor(Color.web("#00FFFF"));
        btn_zoom_out.setGraphic(btn_zoom_out_icon);
        btn_zoom_out.setText("");
    }

    @FXML
    void btnHomePressed(ActionEvent event) {
        graphPlotter.reset();
    }

    @FXML
    void zoomInPressed(ActionEvent event) {
        graphPlotter.zoomIn();
    }

    @FXML
    void zoomOutPressed(ActionEvent event) {
        graphPlotter.zoomOut();
    }

    public void handleNewFile(ActionEvent event) {
        equation_container.getChildren().clear();
        graphPlotter.clearAllEquations();
        colorIndex = 0;
        addEqCount = 0; // Reset count
        addEquation();
    }

    public void handleSaveFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Equations");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(mainBorderPane.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                equation_container.getChildren().forEach(node -> {
                    if (node instanceof HBox row) {
                        row.getChildren().stream()
                                .filter(n -> n instanceof TextField)
                                .map(n -> (TextField) n)
                                .forEach(tf -> writer.println(tf.getText()));
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleOpenFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Equations");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showOpenDialog(mainBorderPane.getScene().getWindow());

        if (file != null) {
            // Clear existing session but don't add default empty equation yet
            equation_container.getChildren().clear();
            graphPlotter.clearAllEquations();
            colorIndex = 0;
            addEqCount = 0;

            boolean loadedAny = false;
            try (Scanner scanner = new Scanner(file)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (!line.isBlank()) {
                        addEquation();
                        HBox lastRow = (HBox) equation_container.getChildren().get(equation_container.getChildren().size() - 1);
                        TextField tf = (TextField) lastRow.getChildren().get(0);
                        tf.setText(line);
                        tf.fireEvent(new ActionEvent());
                        loadedAny = true;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // Ensure at least one equation exists
            if (!loadedAny) {
                addEquation();
            }
        }
    }
}
