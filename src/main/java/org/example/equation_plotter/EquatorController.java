package org.example.equation_plotter;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
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

import javax.imageio.ImageIO;
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

        WebView webView = new WebView();
        webViewMap.put(id, webView);
        webView.setPrefSize(340, 60);
        webView.setContextMenuEnabled(false);

        ToggleButton visibilityToggle = new ToggleButton();
        visibilityToggle.getStyleClass().add("desmos-toggle");
        visibilityToggle.setSelected(true);
        visibilityToggle.setMouseTransparent(true);

        Color initCol = defaultColors.get(colorIndex % defaultColors.size());
        colorIndex++;
        ColorPicker cp = new ColorPicker(initCol);
        cp.setOpacity(0.0);
        cp.setPrefSize(25, 25);
        cp.setMouseTransparent(true);

        Runnable syncStyle = () -> {
            String hex = toHexString(cp.getValue());
            if (visibilityToggle.isSelected()) {
                visibilityToggle.setStyle("-fx-background-color: " + hex + "; -fx-border-color: transparent;");
            } else {
                visibilityToggle.setStyle("-fx-background-color: transparent; -fx-border-color: " + hex + ";");
            }
        };
        syncStyle.run();

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

        Button btn_rmv = new Button();
        btn_rmv.getStyleClass().add("icon-button");
        FontIcon rmvIcon = new FontIcon("fas-times");
        rmvIcon.setIconColor(Color.web("#ff4444"));
        rmvIcon.setIconSize(18);
        btn_rmv.setGraphic(rmvIcon);

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(toggleStack, webView, btn_rmv);

        VBox sliderBox = new VBox(5);
        VBox equationBlock = new VBox(5);
        equationBlock.setPadding(new Insets(5, 8, 5, 8));
        equationBlock.getChildren().addAll(topRow, sliderBox);
        equationBlock.getStyleClass().add("equation-card");

        // ── FIX: helper suppliers that always read the LIVE theme state ──────
        java.util.function.Supplier<String> liveBaseStyle = () -> {
            boolean live = graphPlotter != null && graphPlotter.isLightMode;
            return live
                    ? "-fx-background-color: rgba(255,255,255,0.9);" +
                      "-fx-background-radius: 8; -fx-border-radius: 8;" +
                      "-fx-border-color: rgba(0,0,0,0.1); -fx-border-width: 1;"
                    : "-fx-background-color: rgba(30,30,50,0.9);" +
                      "-fx-background-radius: 8; -fx-border-radius: 8;" +
                      "-fx-border-color: rgba(0,255,255,0.0); -fx-border-width: 1;";
        };

        java.util.function.Supplier<String> liveHoverStyle = () -> {
            boolean live = graphPlotter != null && graphPlotter.isLightMode;
            return live
                    ? "-fx-background-color: rgba(255,255,255,1.0);" +
                      "-fx-background-radius: 8; -fx-border-radius: 8;" +
                      "-fx-border-color: rgba(0,170,221,0.42); -fx-border-width: 1;" +
                      "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 12, 0.4, 0, 0);"
                    : "-fx-background-color: rgba(30,30,50,0.95);" +
                      "-fx-background-radius: 8; -fx-border-radius: 8;" +
                      "-fx-border-color: rgba(0,255,255,0.42); -fx-border-width: 1;" +
                      "-fx-effect: dropshadow(gaussian, rgba(0,255,255,0.18), 12, 0.4, 0, 0);";
        };

        // Apply initial style using the live supplier (correct at creation time)
        equationBlock.setStyle(liveBaseStyle.get());

        // ── FIX: hover listeners call the supplier so they're never stale ────
        equationBlock.setOnMouseEntered(e -> equationBlock.setStyle(liveHoverStyle.get()));
        equationBlock.setOnMouseExited(e -> equationBlock.setStyle(liveBaseStyle.get()));

        equationBlock.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), equationBlock);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(220), equationBlock);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        scaleIn.setFromX(0.97);
        scaleIn.setToX(1.0);
        scaleIn.setFromY(0.97);
        scaleIn.setToY(1.0);

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

                        // ── FIX: inject the correct theme into the WebView on load ──
                        boolean live = graphPlotter != null && graphPlotter.isLightMode;
                        String webBg = live ? "#f5f5f5" : "#1e1e1e";
                        String webColor = live ? "#000000" : "#ffffff";
                        webView.getEngine().executeScript(
                                "document.body.style.backgroundColor = '" + webBg + "';" +
                                        "document.body.style.color = '" + webColor + "';"
                        );

                        String currentMath = (String) webView.getEngine().executeScript(
                                "document.getElementById('math-field').innerText");
                        if (currentMath != null && !currentMath.trim().isEmpty()) {
                            bridge.updateMath(currentMath);
                        }
                    }
                });

        btn_rmv.setOnAction(event -> {
            // Disable the button immediately to prevent double-clicks
            btn_rmv.setDisable(true);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(160), equationBlock);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                equation_container.getChildren().remove(equationBlock);
                graphPlotter.removeEquation(id);
                webViewMap.remove(id);

                // Decrement first
                addEqCount--;

                // Only add a new one if the container is truly empty
                // and no other additions are pending
                if (addEqCount <= 0) {
                    addEqCount = 0; // Reset to be safe
                    addEquation();
                }
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
        // Safety check: Only proceed if it is actually Polar
        if (ed == null || ed.eqType != EquationParser.EqType.Polar) return;

        boolean isLight = graphPlotter != null && graphPlotter.isLightMode;

        HBox rangeBox = new HBox(8);
        rangeBox.setAlignment(Pos.CENTER_LEFT);

        Label caption = new Label("θ range:");
        caption.setStyle(isLight ? "-fx-text-fill: black; -fx-font-size: 12px;" : "-fx-text-fill: white; -fx-font-size: 12px;");

        TextField minField = new TextField("0");
        TextField maxField = new TextField(String.valueOf(Math.PI * 2));
        minField.setPrefWidth(80);
        maxField.setPrefWidth(80);

        String tfStyle = "-fx-font-size: 10px; -fx-alignment: center; " +
                "-fx-border-color: rgba(0,255,255,0.3); -fx-border-radius: 3; " +
                "-fx-background-radius: 3; " +
                (isLight ? "-fx-text-fill: black; -fx-background-color: white;"
                        : "-fx-text-fill: white; -fx-background-color: #2a2a3a;");
        maxField.setStyle(tfStyle);
        minField.setStyle(tfStyle);

        javafx.animation.PauseTransition debounce =
                new javafx.animation.PauseTransition(Duration.millis(250));

        Runnable applyRange = () -> {
            try {
                double min = parseAngle(minField.getText());
                double max = parseAngle(maxField.getText());

                if (ed != null && ed.eqType == EquationParser.EqType.Polar) {
                    ed.thetaMin = min;
                    ed.thetaMax = max;

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

        minField.textProperty().addListener((obs, oldVal, newVal) -> {
            debounce.setOnFinished(e -> applyRange.run());
            debounce.playFromStart();
        });

        maxField.textProperty().addListener((obs, oldVal, newVal) -> {
            debounce.setOnFinished(e -> applyRange.run());
            debounce.playFromStart();
        });

        minField.setOnAction(e -> applyRange.run());
        maxField.setOnAction(e -> applyRange.run());

        minField.focusedProperty().addListener((obs, oldv, newv) -> {
            if (!newv) applyRange.run();
        });
        maxField.focusedProperty().addListener((obs, o, n) -> {
            if (!n) applyRange.run();
        });

        Label midLabel = new Label("≤ θ ≤");
        midLabel.setStyle(isLight ? "-fx-text-fill: black;" : "-fx-text-fill: white;");

        rangeBox.getChildren().addAll(caption, minField, midLabel, maxField);
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


    public void createSlidersBridge(EquationParser parser, VBox box, String id) {
        box.getChildren().clear();
        boolean isLight = graphPlotter != null && graphPlotter.isLightMode;

        parser.getParameters().forEach((ch, arg) -> {

            Label lbl = new Label(ch + " = 1.00");
            lbl.setStyle(isLight ? "-fx-text-fill: black; -fx-font-size: 14px;" : "-fx-text-fill: white; -fx-font-size: 14px;");

            Slider slider = new Slider(-10, 10, 1);
            slider.setPrefWidth(200);
            slider.setPrefHeight(32);
            slider.setShowTickMarks(true);
            slider.setShowTickLabels(true);
            slider.getStyleClass().add("neon-slider");

            String fieldStyle = "-fx-font-size: 10px; -fx-alignment: center; " +
                    "-fx-border-color: rgba(0,255,255,0.3); -fx-border-radius: 3; " +
                    "-fx-background-radius: 3; " +
                    (isLight ? "-fx-text-fill: black; -fx-background-color: white;"
                            : "-fx-text-fill: white; -fx-background-color: #2a2a3a;");

            TextField minField = new TextField("-10");
            minField.setPrefWidth(45);
            minField.setMaxWidth(45);
            minField.setStyle(fieldStyle);

            TextField maxField = new TextField("10");
            maxField.setPrefWidth(45);
            maxField.setMaxWidth(45);
            maxField.setStyle(fieldStyle);

            TextField durationField = new TextField("3.0");
            durationField.setPrefWidth(42);
            durationField.setMaxWidth(42);
            durationField.setStyle(fieldStyle.replace(
                    isLight ? "black" : "white",
                    isLight ? "#0077AA" : "#00FFFF"));
            durationField.setTooltip(new Tooltip("Sweep duration (seconds)"));

            org.kordamp.ikonli.javafx.FontIcon playIcon =
                    new org.kordamp.ikonli.javafx.FontIcon("fas-play");
            playIcon.setIconColor(Color.web("#00FFFF"));
            playIcon.setIconSize(13);

            Button playBtn = new Button();
            playBtn.setStyle(
                    "-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 4 2 4;");
            playBtn.setGraphic(playIcon);

            // --- REALTIME ANIMATION FIX ---
            final boolean[] isAnimating = {false};
            final double[] animDirection = {1.0};

            javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
                private long lastUpdate = 0;

                @Override
                public void handle(long now) {
                    if (lastUpdate == 0) {
                        lastUpdate = now;
                        return;
                    }
                    double elapsedSec = (now - lastUpdate) / 1_000_000_000.0;
                    lastUpdate = now;

                    double dur = 3.0;
                    try {
                        dur = Double.parseDouble(durationField.getText());
                    } catch (NumberFormatException ignored) {
                    }
                    if (dur < 0.1) dur = 0.1;

                    double range = slider.getMax() - slider.getMin();
                    double speed = range / dur; // Units per second

                    double newValue = slider.getValue() + speed * elapsedSec * animDirection[0];

                    if (newValue >= slider.getMax()) {
                        newValue = slider.getMax();
                        animDirection[0] = -1.0;
                    } else if (newValue <= slider.getMin()) {
                        newValue = slider.getMin();
                        animDirection[0] = 1.0;
                    }

                    slider.setValue(newValue);
                }

                @Override
                public void stop() {
                    super.stop();
                    lastUpdate = 0;
                }
            };

            playBtn.setOnAction(e -> {
                if (isAnimating[0]) {
                    timer.stop();
                    isAnimating[0] = false;
                    graphPlotter.isParameterAnimating = false;
                    playIcon.setIconLiteral("fas-play");
                    durationField.setDisable(false);
                    minField.setDisable(false);
                    maxField.setDisable(false);

                    graphPlotter.refreshAllData();
                    graphPlotter.draw();
                } else {
                    isAnimating[0] = true;
                    graphPlotter.isParameterAnimating = true;
                    playIcon.setIconLiteral("fas-pause");
                    durationField.setDisable(true);
                    minField.setDisable(true);
                    maxField.setDisable(true);
                    timer.start();
                }
            });

            java.util.function.Supplier<Double> parseDuration = () -> {
                try {
                    double d = Double.parseDouble(durationField.getText().trim());
                    if (d < 0.1) {
                        durationField.setText("0.1");
                        return 0.1;
                    }
                    if (d > 600) {
                        durationField.setText("600");
                        return 600.0;
                    }
                    return d;
                } catch (NumberFormatException ex) {
                    durationField.setText("3.0");
                    return 3.0;
                }
            };

            Runnable applyMin = () -> {
                try {
                    double v = Double.parseDouble(minField.getText().trim());
                    if (v >= slider.getMax()) {
                        minField.setText(String.format("%.4g", slider.getMin()));
                        return;
                    }
                    slider.setMin(v);
                    if (slider.getValue() < v) {
                        slider.setValue(v);
                        arg.setArgumentValue(v);
                        lbl.setText(ch + " = " + String.format("%.2f", v));
                    }
                    if (!isAnimating[0]) {
                        graphPlotter.refreshAllData();
                        graphPlotter.draw();
                    }
                } catch (NumberFormatException ignored) {
                    minField.setText(String.format("%.4g", slider.getMin()));
                }
            };

            Runnable applyMax = () -> {
                try {
                    double v = Double.parseDouble(maxField.getText().trim());
                    if (v <= slider.getMin()) {
                        maxField.setText(String.format("%.4g", slider.getMax()));
                        return;
                    }
                    slider.setMax(v);
                    if (slider.getValue() > v) {
                        slider.setValue(v);
                        arg.setArgumentValue(v);
                        lbl.setText(ch + " = " + String.format("%.2f", v));
                    }
                    if (!isAnimating[0]) {
                        graphPlotter.refreshAllData();
                        graphPlotter.draw();
                    }
                } catch (NumberFormatException ignored) {
                    maxField.setText(String.format("%.4g", slider.getMax()));
                }
            };

            minField.setOnAction(e -> applyMin.run());
            maxField.setOnAction(e -> applyMax.run());
            minField.focusedProperty().addListener((o, was, focused) -> {
                if (!focused) applyMin.run();
            });
            maxField.focusedProperty().addListener((o, was, focused) -> {
                if (!focused) applyMax.run();
            });
            durationField.setOnAction(e -> parseDuration.get());
            durationField.focusedProperty().addListener((o, was, focused) -> {
                if (!focused) parseDuration.get();
            });

            slider.valueProperty().addListener((obs, oldv, newv) -> {
                double val = newv.doubleValue();
                arg.setArgumentValue(val);
                lbl.setText(ch + " = " + String.format("%.2f", val));
                graphPlotter.onParameterChanged(id);
            });

            slider.valueChangingProperty().addListener((obs, was, changing) -> {
                if (!changing && !isAnimating[0]) {
                    graphPlotter.refreshAllData();
                    graphPlotter.draw();
                }
            });

            HBox sliderRow = new HBox(4);
            sliderRow.setAlignment(Pos.CENTER_LEFT);
            sliderRow.getChildren().addAll(playBtn, durationField, minField, slider, maxField);

            VBox sliderBlock = new VBox(3);
            sliderBlock.getChildren().addAll(lbl, sliderRow);
            box.getChildren().add(sliderBlock);
        });
    }

    // ── Button icon setup ─────────────────────────────────────────────────────

    private void setBtn_home() {
        FontIcon i = new FontIcon("fas-home");
        i.setIconSize(14);
        btn_home.setGraphic(i);
        btn_home.setText("");
        applyIconColor(i, graphPlotter != null && graphPlotter.isLightMode);
    }

    private void setBtn_zoom_in() {
        FontIcon i = new FontIcon("fas-plus");
        i.setIconSize(14);
        btn_zoom_in.setGraphic(i);
        btn_zoom_in.setText("");
        applyIconColor(i, graphPlotter != null && graphPlotter.isLightMode);
    }

    private void setBtn_zoom_out() {
        FontIcon i = new FontIcon("fas-minus");
        i.setIconSize(14);
        btn_zoom_out.setGraphic(i);
        btn_zoom_out.setText("");
        applyIconColor(i, graphPlotter != null && graphPlotter.isLightMode);
    }

    private void setBtn_toggle_grid() {
        FontIcon i = new FontIcon("fas-border-all");
        i.setIconSize(14);
        btn_toggle_grid.setGraphic(i);
        btn_toggle_grid.setText("");
        applyIconColor(i, graphPlotter != null && graphPlotter.isLightMode);
    }

    private void applyIconColor(FontIcon icon, boolean isLight) {
        icon.setIconColor(isLight ? Color.web("#1B1B1B") : Color.web("#00FFFF"));
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
        graphPlotter.addEquationToHashmap(eqId, line, color);
        graphPlotter.refreshAllData();
        graphPlotter.draw();

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

    public void handleExportImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Graph as Image");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        File file = fileChooser.showSaveDialog(mainBorderPane.getScene().getWindow());

        if (file == null) return;

        try {
            WritableImage image = graphPlotter.snapshot(new javafx.scene.SnapshotParameters(), null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to export graph image", e);
        }
    }

    // ── Theming Control ───────────────────────────────────────────────────────
    private void recolorButton(Button btn, boolean isLight) {
        if (btn == null) return;
        javafx.scene.Node graphic = btn.getGraphic();
        if (graphic instanceof FontIcon fi) {
            applyIconColor(fi, isLight);
        }
    }

    private void updateIconColors(boolean isLight) {
        // Control-pill buttons
        recolorButton(btn_home, isLight);
        recolorButton(btn_zoom_in, isLight);
        recolorButton(btn_zoom_out, isLight);
        recolorButton(btn_toggle_grid, isLight);

        // Sidebar open / close buttons
        recolorButton(btn_close_sidebar, isLight);
        recolorButton(btn_open_sidebar, isLight);
    }

    public void toggleTheme() {
        graphPlotter.toggleTheme();
        boolean isLight = graphPlotter.isLightMode;

        // Apply Enum Colors
        mainBorderPane.setStyle("-fx-background-color: " + ThemeColor.BACKGROUND.getCss(isLight) + ";");
        sideBar.setStyle("-fx-background-color: " + ThemeColor.BACKGROUND.getCss(isLight) + ";");
        if (isLight) mainBorderPane.getStyleClass().add("light-theme");
        else mainBorderPane.getStyleClass().remove("light-theme");

        if (controlPill != null) {
            controlPill.setStyle(
                    "-fx-background-color: " + ThemeColor.PILL_BACKGROUND.getCss(isLight) + ";" +
                            "-fx-background-radius: 20;" +
                            "-fx-border-radius: 5;" +
                            "-fx-border-color: " + ThemeColor.PILL_BORDER.getCss(isLight) + ";" +
                            "-fx-border-width: 1;" +
                            "-fx-padding: 6 4 6 4;"
            );
        }

        // ── FIX: recolour all icon buttons to match the new theme ────────────
        updateIconColors(isLight);

        // --- Update all existing Equation Cards dynamically ---
        String cardBaseStyle = isLight ?
                "-fx-background-color: rgba(255,255,255,0.9);" +
                "-fx-background-radius: 8; -fx-border-radius: 8;" +
                "-fx-border-color: rgba(0,0,0,0.1); -fx-border-width: 1;"
                :
                "-fx-background-color: rgba(30,30,50,0.9);" +
                "-fx-background-radius: 8; -fx-border-radius: 8;" +
                "-fx-border-color: rgba(0,255,255,0.0); -fx-border-width: 1;";

        String cardHoverStyle = isLight ?
                "-fx-background-color: rgba(255,255,255,1.0);" +
                "-fx-background-radius: 8; -fx-border-radius: 8;" +
                "-fx-border-color: rgba(0,170,221,0.42); -fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 12, 0.4, 0, 0);"
                :
                "-fx-background-color: rgba(30,30,50,0.95);" +
                "-fx-background-radius: 8; -fx-border-radius: 8;" +
                "-fx-border-color: rgba(0,255,255,0.42); -fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,255,255,0.18), 12, 0.4, 0, 0);";

        for (javafx.scene.Node node : equation_container.getChildren()) {
            if (node instanceof VBox equationBlock) {
                equationBlock.setStyle(cardBaseStyle);
                equationBlock.setOnMouseEntered(e -> equationBlock.setStyle(cardHoverStyle));
                equationBlock.setOnMouseExited(e -> equationBlock.setStyle(cardBaseStyle));
                updateNodeStyles(equationBlock, isLight);
            }
        }
    }

    // Recursive helper to update nested UI nodes during a live theme switch
    private void updateNodeStyles(javafx.scene.Node node, boolean isLight) {
        if (node instanceof TextField tf) {
            if (tf.getTooltip() != null && tf.getTooltip().getText().contains("duration")) {
                tf.setStyle("-fx-font-size: 10px; -fx-alignment: center; " +
                        "-fx-border-color: rgba(0,255,255,0.3); -fx-border-radius: 3; " +
                        "-fx-background-radius: 3; " +
                        (isLight ? "-fx-text-fill: #0077AA; -fx-background-color: white;"
                                : "-fx-text-fill: #00FFFF; -fx-background-color: #2a2a3a;"));
            } else if (tf.getPrefWidth() == 42) { // Duration field
                tf.setStyle("-fx-font-size: 10px; -fx-alignment: center; " +
                        "-fx-border-color: rgba(0,255,255,0.3); -fx-border-radius: 3; " +
                        "-fx-background-radius: 3; " +
                        (isLight ? "-fx-text-fill: #0077AA; -fx-background-color: white;"
                                : "-fx-text-fill: #00FFFF; -fx-background-color: #2a2a3a;"));
            }
        } else if (node instanceof Label lbl) {
            String currentStyle = lbl.getStyle();
            if (currentStyle.contains("12px")) { // Range labels
                lbl.setStyle(isLight ? "-fx-text-fill: black; -fx-font-size: 12px;" : "-fx-text-fill: white; -fx-font-size: 12px;");
            } else if (currentStyle.contains("14px")) { // Slider name labels
                lbl.setStyle(isLight ? "-fx-text-fill: black; -fx-font-size: 14px;" : "-fx-text-fill: white; -fx-font-size: 14px;");
            } else { // Generic inner labels (like "≤ t ≤")
                lbl.setStyle(isLight ? "-fx-text-fill: black;" : "-fx-text-fill: white;");
            }
        } else if (node instanceof Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                updateNodeStyles(child, isLight);
            }
        }
    }
}