package org.example.equation_plotter;

import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import netscape.javascript.JSObject;
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

    private final java.util.Map<String, javafx.scene.web.WebView> webViewMap = new java.util.HashMap<>();
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
        graphPlotter = new GraphPlotter(graph_container.getPrefWidth(), graph_container.getPrefHeight());
        graphPlotter.prefWidthProperty().bind(graph_container.widthProperty());
        graphPlotter.prefHeightProperty().bind(graph_container.heightProperty());

        graph_container.getChildren().addFirst(graphPlotter);
        graph_container.setStyle("-fx-background-color: transparent;");
        graphPlotter.toBack();

        addEquation();
        setBtn_home();
        setBtn_zoom_in();
        setBtn_zoom_out();
        initSidebarButtons();
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
        AnchorPane.setRightAnchor(btn_home, 410.0);
        AnchorPane.setRightAnchor(btn_zoom_in, 410.0);
        AnchorPane.setRightAnchor(btn_zoom_out, 410.0);
    }

    @FXML
    protected void btnAddPressed() {
        addEquation();
    }

    private void addEquation() {
        String id = "eq-" + System.nanoTime();

        WebView webView = new WebView();
        webViewMap.put(id, webView);
        webView.setPrefSize(350, 60); // Updated size for better visibility
        webView.setContextMenuEnabled(false);

        // Standard event filter for keys
        webView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            String keystroke = null;
            switch (event.getCode()) {
                case BACK_SPACE:
                    keystroke = "Backspace";
                    break;
                case LEFT:
                    keystroke = "Left";
                    break;
                case RIGHT:
                    keystroke = "Right";
                    break;
                case UP:
                    keystroke = "Up";
                    break;
                case DOWN:
                    keystroke = "Down";
                    break;
                case DELETE:
                    keystroke = "Del";
                    break;
                default:
                    break;
            }

            if (keystroke != null) {
                // Use the JS variable 'mathField' directly and call 'keystroke'
                webView.getEngine().executeScript("window.mathField.keystroke('" + keystroke + "');");
                event.consume();
            }
        });

        // Load the HTML file
        java.net.URL htmlUrl = getClass().getResource("/org/example/equation_plotter/math_input.html");
        if (htmlUrl != null) {
            webView.getEngine().load(htmlUrl.toExternalForm());
            //webView.getEngine().load("https://www.google.com");

        }

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
        cp.setOnAction(event -> {
            graphPlotter.updateEqColor(id, cp.getValue());
        });
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(webView, btn_rmv, cp);

        VBox sliderBox = new VBox(5);
        VBox equationBlock = new VBox(5);
        equationBlock.setPadding(new Insets(5, 0, 5, 0));
        equationBlock.getChildren().addAll(topRow, sliderBox);

        // --- THE FIX: MOVE LOGIC INSIDE THE LOAD WORKER ---
        MathBridge bridge = new MathBridge(id, graphPlotter, cp, sliderBox, this);

        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webView.getEngine().executeScript("window");
                window.setMember("javaConnector", bridge);

                // If there's already text in the field (from a file load), plot it now
                String currentMath = (String) webView.getEngine().executeScript("document.getElementById('mf').getValue('ascii-math')");
                if (currentMath != null && !currentMath.trim().isEmpty()) {
                    bridge.updateMath(currentMath);
                }
            }
        });

        btn_rmv.setOnAction(event -> {
            equation_container.getChildren().remove(equationBlock);
            graphPlotter.removeEquation(id);
            addEqCount--;
            if (addEqCount == 0) addEquation();
        });

        equation_container.getChildren().add(equationBlock);
        addEqCount++;
    }

    // Call: controller.createPolarRangeControls(sliderBox, id)
    // New method: live-updating range controls
    public void createPolarRangeControls(VBox container, String eqId) {
        HBox rangeBox = new HBox(8);
        rangeBox.setAlignment(Pos.CENTER_LEFT);

        Label caption = new Label("θ range:");
        caption.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

        TextField minField = new TextField("0");
        TextField maxField = new TextField(String.valueOf(Math.PI * 2));
        minField.setPrefWidth(80);
        maxField.setPrefWidth(80);
        minField.setStyle("-fx-font-size:11px;");
        maxField.setStyle("-fx-font-size:11px;");

        // debounce so live typing doesn't redraw every keystroke
        javafx.animation.PauseTransition debounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
        Runnable applyRange = () -> {
            try {
                double min = parseAngle(minField.getText());
                double max = parseAngle(maxField.getText());
                EquationData ed = graphPlotter.getEquation(eqId);
                if (ed != null && ed.isPolar) {
                    ed.thetaMin = min;
                    ed.thetaMax = max;
                    graphPlotter.refreshEquationData(eqId);
                    graphPlotter.draw();
                }
            } catch (Exception ex) {
                // invalid input — ignore (or show tooltip in future)
            }
        };

        // key released -> debounce
        minField.setOnKeyReleased(ev -> {
            debounce.playFromStart();
            debounce.setOnFinished(e -> applyRange.run());
        });
        maxField.setOnKeyReleased(ev -> {
            debounce.playFromStart();
            debounce.setOnFinished(e -> applyRange.run());
        });

        // apply also on focus lost (safer)
        minField.focusedProperty().addListener((obs, oldv, newv) -> { if (!newv) applyRange.run(); });
        maxField.focusedProperty().addListener((obs, oldv, newv) -> { if (!newv) applyRange.run(); });

        rangeBox.getChildren().addAll(caption, minField, new Label("≤ θ ≤"), maxField);

        // remove any existing polar row for this equation (optional simple approach)
        // In your code 'sliderBox' is per-equation; caller should ensure only one polar control is appended.
        container.getChildren().add(rangeBox);
    }

    private double parseAngle(String s) {
        String t = s.trim().toLowerCase().replace("π", "pi");
        // simple replacements and recursive parse of * and /
        if (t.contains("pi")) {
            t = t.replace("pi", String.valueOf(Math.PI));
        }
        if (t.contains("/")) {
            String[] parts = t.split("/");
            return parseAngle(parts[0]) / parseAngle(parts[1]);
        }
        if (t.contains("*")) {
            String[] parts = t.split("\\*");
            return parseAngle(parts[0]) * parseAngle(parts[1]);
        }
        return Double.parseDouble(t);
    }

    // helper function
    public void updateWebViewDisplay(String eqId, String displayAscii) {
        javafx.scene.web.WebView wv = webViewMap.get(eqId);
        if (wv == null) return;
        // replace single quotes and backslashes for safe JS string literal
        String safe = displayAscii.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
        // The page's MathField element id in your HTML appears to be 'mf' (used earlier).
        // We set the ascii-math value of the field to the modified version (with θ).
        try {
            wv.getEngine().executeScript("document.getElementById('mf').setValue('" + safe + "', 'ascii-math');");
        } catch (Exception e) {
            // Ignore errors (page might not expose setValue exactly like this)
        }
    }

    public void createSlidersBridge(EquationParser parser, VBox box, String id) {
        box.getChildren().clear();

        parser.getParameters().forEach((ch, arg) -> {

            Label lbl = new Label(ch + " = 1");
            lbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

            Slider s = new Slider(-10, 10, 1);
            s.setPrefWidth(300);
            s.setPrefHeight(40);
            s.setShowTickMarks(true);
            s.setShowTickLabels(true);

            TextField minField = new TextField("-10");
            TextField maxField = new TextField("10");

            minField.setPrefWidth(45);
            maxField.setPrefWidth(45);
            minField.setMaxWidth(45);
            maxField.setMaxWidth(45);
            minField.setStyle("-fx-font-size: 10px; -fx-alignment: center;");
            maxField.setStyle("-fx-font-size: 10px; -fx-alignment: center;");

            s.setMin(-10);
            s.setMax(10);

            minField.setOnAction(e -> {
                try {
                    double newMin = Double.parseDouble(minField.getText());
                    if (newMin < s.getMax()) {
                        s.setMin(newMin);
                        graphPlotter.refreshEquationData(id);
                        graphPlotter.draw();
                    }
                } catch (NumberFormatException ignored) {
                }
            });

            maxField.setOnAction(e -> {
                try {
                    double newMax = Double.parseDouble(maxField.getText());
                    if (newMax > s.getMin()) {
                        s.setMax(newMax);
                        graphPlotter.refreshEquationData(id);
                        graphPlotter.draw();
                    }
                } catch (NumberFormatException ignored) {
                }
            });

            javafx.animation.PauseTransition sliderThrottle = new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));

            s.valueProperty().addListener((obs, oldv, newv) -> {
                arg.setArgumentValue(newv.doubleValue());
                lbl.setText(ch + " = " + String.format("%.2f", newv.doubleValue()));

                sliderThrottle.setOnFinished(event -> {
                    graphPlotter.refreshEquationData(id);
                    graphPlotter.drawGraphLayer();
                });

                sliderThrottle.playFromStart();
            });

            s.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging) {
                    graphPlotter.refreshAllData();
                    graphPlotter.draw();
                }
            });

            HBox sliderRow = new HBox(5);
            sliderRow.setAlignment(Pos.CENTER_LEFT);
            sliderRow.getChildren().addAll(minField, s, maxField);

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
        addEqCount = 0;
        addEquation();
    }

    public void handleOpenFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Equations");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showOpenDialog(mainBorderPane.getScene().getWindow());

        if (file != null) {
            equation_container.getChildren().clear();
            graphPlotter.clearAllEquations();
            colorIndex = 0;
            addEqCount = 0;

            boolean loadedAny = false;
            try (Scanner scanner = new Scanner(file)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.isEmpty()) {
                        addEquation();

                        // Navigate: VBox (equationBlock) -> HBox (topRow) -> TextField
                        VBox equationBlock = (VBox) equation_container.getChildren().getLast();
                        HBox topRow = (HBox) equationBlock.getChildren().get(0);
                        TextField tf = (TextField) topRow.getChildren().get(0);
                        ColorPicker cp = (ColorPicker) topRow.getChildren().get(2);

                        tf.setText(line);

                        // Manually trigger the plot since setText() doesn't fire listeners
                        graphPlotter.addEquationToHashmap(tf.getId(), line, cp.getValue());
                        loadedAny = true;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            if (!loadedAny) {
                addEquation();
            }

            // Final refresh to ensure all points and lines calculate
            graphPlotter.refreshAllData();
            graphPlotter.draw();
        }
    }

    public void handleSaveFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Equations");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(mainBorderPane.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                equation_container.getChildren().forEach(node -> {
                    if (node instanceof VBox equationBlock && !equationBlock.getChildren().isEmpty()) {
                        HBox topRow = (HBox) equationBlock.getChildren().get(0);
                        TextField tf = (TextField) topRow.getChildren().get(0);
                        if (!tf.getText().isBlank()) {
                            writer.println(tf.getText());
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public GraphPlotter getGraphPlotter() {
        return graphPlotter;
    }

    public Scene getScene() {
        return mainBorderPane.getScene();
    }
}
