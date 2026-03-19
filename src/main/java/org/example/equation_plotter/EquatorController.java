package org.example.equation_plotter;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EquatorController {

    private static final Logger LOGGER = Logger.getLogger(EquatorController.class.getName());

    @FXML
    private VBox equation_container;
    @FXML
    private AnchorPane graph_container;
    @FXML
    private Button btn_home, btn_zoom_in, btn_zoom_out, btn_toggle_grid;
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
    private final java.util.Map<String, WebView> webViewMap = new java.util.HashMap<>();
    // Vibrant neon palette — all visible on dark backgrounds
    private final List<Color> defaultColors = Arrays.asList(
            Color.web("#FF4D6D"), // neon rose
            Color.web("#00CFFF"), // electric cyan
            Color.web("#39FF14"), // neon green
            Color.web("#FF9F1C"), // vivid amber
            Color.web("#BF5AF2"), // neon violet
            Color.web("#FF6B35")  // neon orange
    );
    private GraphPlotter graphPlotter;
    private int addEqCount = 0;
    private VBox controlPill;
    private int colorIndex = 0;

    // ── Initialization ────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        if (navbarController != null) {
            navbarController.setMainController(this);
        }

        graphPlotter = new GraphPlotter(
                graph_container.getPrefWidth(),
                graph_container.getPrefHeight());
        GraphPlotter.setMainInstance(graphPlotter);
        graphPlotter.prefWidthProperty().bind(graph_container.widthProperty());
        graphPlotter.prefHeightProperty().bind(graph_container.heightProperty());
        graph_container.getChildren().addFirst(graphPlotter);
        graph_container.setStyle("-fx-background-color: transparent;");
        graphPlotter.toBack();

        // Clip canvas so it never overflows graph_container visually
        Rectangle clip = new Rectangle();
        graph_container.setClip(clip);
        graph_container.layoutBoundsProperty().addListener((obs, oldB, newB) -> {
            clip.setWidth(newB.getWidth());
            clip.setHeight(newB.getHeight());
            if (newB.getWidth() > 0 && newB.getHeight() > 0) {
                graphPlotter.refreshAllData();
                graphPlotter.draw();
            }
        });

        addEquation();
        setBtn_home();
        setBtn_zoom_in();
        setBtn_zoom_out();
        setBtn_toggle_grid();
        initSidebarButtons();

        // Floating control pill
        controlPill = new VBox(3, btn_home, btn_zoom_in, btn_zoom_out, btn_toggle_grid);
        controlPill.setStyle(
                "-fx-background-color: rgba(13,13,26,0.82);" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-radius: 5;" +
                        "-fx-border-color: rgba(0,255,255,0.22);" +
                        "-fx-border-width: 1;" +
                        "-fx-padding: 6 4 6 4;"
        );
        controlPill.setAlignment(Pos.CENTER);
        graph_container.getChildren().removeAll(
                btn_home, btn_zoom_in, btn_zoom_out, btn_toggle_grid);
        graph_container.getChildren().add(controlPill);
        AnchorPane.setTopAnchor(controlPill, 16.0);
        AnchorPane.setRightAnchor(controlPill, 16.0);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

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
    void closeSidebarPressed() {
        AnchorPane.setRightAnchor(controlPill, 16.0);
        TranslateTransition slide = new TranslateTransition(Duration.millis(100), sideBar);
        slide.setFromX(0);
        slide.setToX(-sideBar.getWidth());
        slide.setOnFinished(e -> {
            mainBorderPane.setLeft(null);
            btn_open_sidebar.setVisible(true);
            // Two runLater calls — second fires after layout pass completes
            Platform.runLater(() -> Platform.runLater(() -> {
                graphPlotter.refreshAllData();
                graphPlotter.draw();
            }));
        });
        slide.play();
    }

    @FXML
    void openSidebarPressed() {
        btn_open_sidebar.setVisible(false);
        AnchorPane.setRightAnchor(controlPill, 416.0);
        mainBorderPane.setLeft(sideBar);
        sideBar.setTranslateX(-sideBar.getWidth());
        TranslateTransition slide = new TranslateTransition(Duration.millis(150), sideBar);
        slide.setFromX(-sideBar.getWidth());
        slide.setToX(0);
        slide.setOnFinished(e -> Platform.runLater(() -> Platform.runLater(() -> {
            graphPlotter.refreshAllData();
            graphPlotter.draw();
        })));
        slide.play();
    }

    // ── Equation management ───────────────────────────────────────────────────

    @FXML
    protected void btnAddPressed() {
        addEquation();
    }

    private void addEquation() {
        String id = "eq-" + System.nanoTime();

        // 1. Math input WebView
        WebView webView = new WebView();
        webViewMap.put(id, webView);
        webView.setPrefSize(340, 60);
        webView.setContextMenuEnabled(false);

        // 2. Visibility toggle circle
        ToggleButton visibilityToggle = new ToggleButton();
        visibilityToggle.getStyleClass().add("desmos-toggle");
        visibilityToggle.setSelected(true);
        visibilityToggle.setMouseTransparent(true);

        // 3. Invisible color picker stacked behind the toggle
        Color initCol = defaultColors.get(colorIndex % defaultColors.size());
        colorIndex++;
        ColorPicker cp = new ColorPicker(initCol);
        cp.setOpacity(0.0);
        cp.setPrefSize(25, 25);
        cp.setMouseTransparent(true);

        // 4. Sync toggle circle color with picker value
        Runnable syncStyle = () -> {
            String hex = toHexString(cp.getValue());
            if (visibilityToggle.isSelected()) {
                visibilityToggle.setStyle(
                        "-fx-background-color: " + hex + "; -fx-border-color: transparent;");
            } else {
                visibilityToggle.setStyle(
                        "-fx-background-color: transparent; -fx-border-color: " + hex + ";");
            }
        };
        syncStyle.run();

        // 5. Toggle stack — left click = toggle, right click = color picker
        StackPane toggleStack = new StackPane(visibilityToggle, cp);
        toggleStack.setAlignment(Pos.CENTER);
        toggleStack.setCursor(Cursor.HAND);

        toggleStack.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                visibilityToggle.setSelected(!visibilityToggle.isSelected());
                EquationData eq = graphPlotter.getEquation(id);
                if (eq != null) {
                    eq.isVisible = visibilityToggle.isSelected();
                    syncStyle.run();
                    graphPlotter.draw();
                }
            } else if (event.getButton() == MouseButton.SECONDARY) {
                cp.show();
            }
        });

        cp.setOnAction(event -> {
            graphPlotter.updateEqColor(id, cp.getValue());
            syncStyle.run();
            graphPlotter.draw();
        });

        // 6. Remove button
        Button btn_rmv = new Button();
        btn_rmv.getStyleClass().add("icon-button");
        FontIcon rmvIcon = new FontIcon("fas-times");
        rmvIcon.setIconColor(Color.web("#ff4444"));
        rmvIcon.setIconSize(18);
        btn_rmv.setGraphic(rmvIcon);

        // 7. Layout
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(toggleStack, webView, btn_rmv);

        VBox sliderBox = new VBox(5);
        VBox equationBlock = new VBox(5);
        equationBlock.setPadding(new Insets(5, 8, 5, 8));
        equationBlock.getChildren().addAll(topRow, sliderBox);
        equationBlock.getStyleClass().add("equation-card");

        // 8. Hover glow
        String baseStyle =
                "-fx-background-color: rgba(30,30,50,0.9);" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-color: rgba(0,255,255,0.0);" +
                        "-fx-border-width: 1;";
        String hoverStyle =
                "-fx-background-color: rgba(30,30,50,0.95);" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-color: rgba(0,255,255,0.42);" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,255,255,0.18), 12, 0.4, 0, 0);";

        equationBlock.setStyle(baseStyle);
        equationBlock.setOnMouseEntered(e -> equationBlock.setStyle(hoverStyle));
        equationBlock.setOnMouseExited(e -> equationBlock.setStyle(baseStyle));

        // 9. Entrance animation
        equationBlock.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), equationBlock);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(220), equationBlock);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        scaleIn.setFromX(0.97);
        scaleIn.setToX(1.0);
        scaleIn.setFromY(0.97);
        scaleIn.setToY(1.0);

        // 10. MathBridge + WebView
        MathBridge bridge = new MathBridge(id, graphPlotter, cp, sliderBox, this);

        webView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            String keystroke = switch (event.getCode()) {
                case BACK_SPACE -> "Backspace";
                case LEFT -> "Left";
                case RIGHT -> "Right";
                case UP -> "Up";
                case DOWN -> "Down";
                case DELETE -> "Del";
                default -> null;
            };
            if (keystroke != null) {
                webView.getEngine().executeScript(
                        "window.mathField.keystroke('" + keystroke + "');");
                event.consume();
            }
        });

        java.net.URL htmlUrl = getClass().getResource(
                "/org/example/equation_plotter/math_input.html");
        if (htmlUrl != null) webView.getEngine().load(htmlUrl.toExternalForm());

        webView.getEngine().getLoadWorker().stateProperty().addListener(
                (obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        JSObject window = (JSObject) webView.getEngine().executeScript("window");
                        window.setMember("javaConnector", bridge);
                        String currentMath = (String) webView.getEngine().executeScript(
                                "document.getElementById('math-field').innerText");
                        if (currentMath != null && !currentMath.trim().isEmpty()) {
                            bridge.updateMath(currentMath);
                        }
                    }
                });

        // 11. Remove with fade-out
        btn_rmv.setOnAction(event -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(160), equationBlock);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                equation_container.getChildren().remove(equationBlock);
                graphPlotter.removeEquation(id);
                webViewMap.remove(id);
                addEqCount--;
                if (addEqCount == 0) addEquation();
            });
            fadeOut.play();
        });

        equation_container.getChildren().add(equationBlock);
        addEqCount++;
        fadeIn.play();
        scaleIn.play();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    public void createPolarRangeControls(VBox container, String eqId) {
        EquationData ed = graphPlotter.getEquation(eqId);
        if (ed == null) return;


        HBox rangeBox = new HBox(8);
        rangeBox.setAlignment(Pos.CENTER_LEFT);


        String captionLabel = (ed.eqType == EquationParser.EqType.Parametric)
                ? "t range:" : "θ range:";
        Label caption = new Label(captionLabel);
        caption.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

        double maxLimit = (ed.eqType == EquationParser.EqType.Polar) ? Math.PI * 2 : 1.0;
        TextField minField = new TextField("0");
        TextField maxField = new TextField(String.valueOf(maxLimit));
        minField.setPrefWidth(80);
        maxField.setPrefWidth(80);
        minField.setStyle("-fx-font-size:11px;");
        maxField.setStyle("-fx-font-size:11px;");

        javafx.animation.PauseTransition debounce =
                new javafx.animation.PauseTransition(Duration.millis(250));

        Runnable applyRange = () -> {
            try {
                double min = parseAngle(minField.getText());
                double max = parseAngle(maxField.getText());


                if (ed != null) {
                    if (ed.eqType == EquationParser.EqType.Polar) {
                        ed.thetaMin = min;
                        ed.thetaMax = max;
                    } else if (ed.eqType == EquationParser.EqType.Parametric) {
                        ed.tMin = min;
                        ed.tMax = max;
                    }


                    graphPlotter.refreshEquationData(eqId);
                    graphPlotter.draw();
                    Platform.runLater(() -> {
                        graphPlotter.refreshEquationData(eqId);
                        graphPlotter.draw();
                    });
                }
            } catch (Exception ignored) {
            }
        };

        // key released -> debounce
        minField.textProperty().addListener((obs, oldVal, newVal) -> {
            debounce.setOnFinished(e -> applyRange.run());
            debounce.playFromStart();
        });

        maxField.textProperty().addListener((obs, oldVal, newVal) -> {
            debounce.setOnFinished(e -> applyRange.run());
            debounce.playFromStart();
        });

        // enter pressed ->update
        minField.setOnAction(e -> applyRange.run());
        maxField.setOnAction(e -> applyRange.run());

        // apply also on focus lost (safer)
        minField.focusedProperty().addListener((obs, oldv, newv) -> {
            if (!newv) applyRange.run();
        });
        maxField.focusedProperty().addListener((obs, o, n) -> {
            if (!n) applyRange.run();
        });

        String middle = (ed.eqType == EquationParser.EqType.Parametric) ? "≤ t ≤" : "≤ θ ≤";
        rangeBox.getChildren().addAll(caption, minField, new Label(middle), maxField);

        // remove any existing polar row for this equation (optional simple approach)
        // In your code 'sliderBox' is per-equation; caller should ensure only one polar control is appended.
        container.getChildren().add(rangeBox);
    }

    private double parseAngle(String s) {
        String t = s.trim().toLowerCase().replace("π", "pi");
        if (t.contains("pi")) t = t.replace("pi", String.valueOf(Math.PI));
        if (t.contains("/")) {
            String[] p = t.split("/");
            return parseAngle(p[0]) / parseAngle(p[1]);
        }
        if (t.contains("*")) {
            String[] p = t.split("\\*");
            return parseAngle(p[0]) * parseAngle(p[1]);
        }
        return Double.parseDouble(t);
    }

    public void updateWebViewDisplay(String eqId, String displayAscii) {
        WebView wv = webViewMap.get(eqId);
        if (wv == null) return;
        String safe = displayAscii
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ");
        try {
            wv.getEngine().executeScript(
                    "window.mathField.latex('" + safe + "');");
        } catch (Exception ignored) {
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
            s.getStyleClass().add("neon-slider");

            TextField minField = new TextField("-10");
            TextField maxField = new TextField("10");
            minField.setPrefWidth(45);
            minField.setMaxWidth(45);
            maxField.setPrefWidth(45);
            maxField.setMaxWidth(45);
            minField.setStyle("-fx-font-size: 10px; -fx-alignment: center;");
            maxField.setStyle("-fx-font-size: 10px; -fx-alignment: center;");

            minField.setOnAction(e -> {
                try {
                    double v = Double.parseDouble(minField.getText());
                    if (v < s.getMax()) {
                        s.setMin(v);
                        graphPlotter.refreshEquationData(id);
                        graphPlotter.draw();
                    }
                } catch (NumberFormatException ignored) {
                }
            });

            maxField.setOnAction(e -> {
                try {
                    double v = Double.parseDouble(maxField.getText());
                    if (v > s.getMin()) {
                        s.setMax(v);
                        graphPlotter.refreshEquationData(id);
                        graphPlotter.draw();
                    }
                } catch (NumberFormatException ignored) {
                }
            });

            javafx.animation.PauseTransition throttle =
                    new javafx.animation.PauseTransition(Duration.millis(50));

            s.valueProperty().addListener((obs, oldv, newv) -> {
                arg.setArgumentValue(newv.doubleValue());
                lbl.setText(ch + " = " + String.format("%.2f", newv.doubleValue()));
                throttle.setOnFinished(ev -> {
                    graphPlotter.refreshEquationData(id);
                    graphPlotter.drawGraphLayer();
                });
                throttle.playFromStart();
            });

            s.valueChangingProperty().addListener((obs, was, is) -> {
                if (!is) {
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

    // ── Button icon setup ─────────────────────────────────────────────────────

    private void setBtn_home() {
        FontIcon i = new FontIcon("fas-home");
        i.setIconColor(Color.web("#00FFFF"));
        btn_home.setGraphic(i);
        btn_home.setText("");
    }

    private void setBtn_zoom_in() {
        FontIcon i = new FontIcon("fas-plus");
        i.setIconColor(Color.web("#00FFFF"));
        btn_zoom_in.setGraphic(i);
        btn_zoom_in.setText("");
    }

    private void setBtn_zoom_out() {
        FontIcon i = new FontIcon("fas-minus");
        i.setIconColor(Color.web("#00FFFF"));
        btn_zoom_out.setGraphic(i);
        btn_zoom_out.setText("");
    }

    private void setBtn_toggle_grid() {
        FontIcon i = new FontIcon("fas-border-all");
        i.setIconColor(Color.web("#00FFFF"));
        btn_toggle_grid.setGraphic(i);
        btn_toggle_grid.setText("");
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    void btnHomePressed() {
        graphPlotter.reset();
    }

    @FXML
    void zoomInPressed() {
        graphPlotter.zoomIn();
    }

    @FXML
    void zoomOutPressed() {
        graphPlotter.zoomOut();
    }

    @FXML
    void toggleGridPressed() {
        graphPlotter.toggleGrid();
    }

    // ── File handling ─────────────────────────────────────────────────────────

    public void handleNewFile() {
        equation_container.getChildren().clear();
        graphPlotter.clearAllEquations();
        colorIndex = 0;
        addEqCount = 0;
        addEquation();
    }

    public void handleOpenFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Equations");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showOpenDialog(mainBorderPane.getScene().getWindow());

        if (file == null) return;

        List<String> lines = new ArrayList<>();
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) lines.add(line);
            }
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Failed to open equations file", e);
            return;
        }

        if (lines.isEmpty()) return;

        equation_container.getChildren().clear();
        graphPlotter.clearAllEquations();
        colorIndex = 0;
        addEqCount = 0;

        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final int index = i;

            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(Duration.millis(400L * (index + 1)));

            delay.setOnFinished(e -> {
                addEquation();

                VBox equationBlock = (VBox) equation_container.getChildren().getLast();
                HBox topRow = (HBox) equationBlock.getChildren().get(0);
                WebView wv = (WebView) topRow.getChildren().get(1);
                StackPane toggleStack = (StackPane) topRow.getChildren().get(0);

                String eqId = webViewMap.entrySet().stream()
                        .filter(entry -> entry.getValue() == wv)
                        .map(java.util.Map.Entry::getKey)
                        .findFirst().orElse(null);
                if (eqId == null) return;

                ColorPicker cp = toggleStack.getChildren().stream()
                        .filter(n -> n instanceof ColorPicker)
                        .map(n -> (ColorPicker) n)
                        .findFirst().orElse(null);
                Color color = cp != null ? cp.getValue() : Color.web("#00CFFF");

                injectEquation(wv, eqId, line, color);
            });

            delay.play();
        }
    }

    @SuppressWarnings("unchecked")
    private void injectEquation(WebView wv, String eqId, String line, Color color) {
        Worker.State state = wv.getEngine().getLoadWorker().getState();

        if (state == Worker.State.SUCCEEDED) {
            doInject(wv, eqId, line, color);
        } else {
            ChangeListener<Worker.State>[] holder = new ChangeListener[1];
            holder[0] = (obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    wv.getEngine().getLoadWorker().stateProperty().removeListener(holder[0]);
                    doInject(wv, eqId, line, color);
                }
            };
            wv.getEngine().getLoadWorker().stateProperty().addListener(holder[0]);
        }
    }

    private void doInject(WebView wv, String eqId, String line, Color color) {
        // Plot immediately
        graphPlotter.addEquationToHashmap(eqId, line, color);
        graphPlotter.refreshAllData();
        graphPlotter.draw();

        // Poll until MathQuill's mathField is ready, then inject
        String safe = line.replace("\\", "\\\\").replace("'", "\\'");

        javafx.animation.Timeline poller = new javafx.animation.Timeline();
        poller.setCycleCount(20);
        poller.getKeyFrames().add(new javafx.animation.KeyFrame(
                Duration.millis(150),
                e -> {
                    try {
                        Object ready = wv.getEngine().executeScript(
                                "typeof window.mathField !== 'undefined' " +
                                        "&& typeof window.mathField.latex === 'function' " +
                                        "&& typeof window.javaConnector !== 'undefined'");
                        if (Boolean.TRUE.equals(ready)) {
                            wv.getEngine().executeScript(
                                    "window.mathField.latex('" + safe + "');"
                            );
                            poller.stop();
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "MathQuill not ready yet", ex);
                    }
                }
        ));
        poller.play();
    }

    public void handleSaveFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Equations");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fc.showSaveDialog(mainBorderPane.getScene().getWindow());

        if (file == null) return;

        // Save LaTeX from each WebView so it displays correctly on reload
        try (PrintWriter writer = new PrintWriter(file)) {
            webViewMap.forEach((id, wv) -> {
                try {
                    Object latex = wv.getEngine().executeScript(
                            "window.mathField ? window.mathField.latex() : ''");
                    if (latex != null && !latex.toString().isBlank()) {
                        writer.println(latex);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to read LaTeX from WebView " + id, e);
                }
            });
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save equations file", e);
        }
    }


    public Scene getScene() {
        return mainBorderPane.getScene();
    }

    public GraphPlotter getGraphPlotter() {
        return graphPlotter;
    }
}
