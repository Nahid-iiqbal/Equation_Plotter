package org.example.equation_plotter;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;

import java.util.Map;
import java.util.Objects;

public class NavBar {
    private EquatorController mainController;

    public void setMainController(EquatorController mainController) {
        this.mainController = mainController;
    }

    @FXML
    void onNew() {
        if (mainController != null) mainController.handleNewFile();
    }

    @FXML
    void onOpen() {
        if (mainController != null) mainController.handleOpenFile();
    }

    @FXML
    void onSave() {
        if (mainController != null) mainController.handleSaveFile();
    }

    @FXML
    void onClose() {
        Platform.exit();
        System.exit(0);
    }


    @FXML
    void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Equator");
        alert.setHeaderText("EQUATOR GRAPHING CALCULATOR");

        String description = """
                Equator is a high-performance mathematical visualization engine \
                designed for speed and precision. It leverages an advanced AST compiler \
                to evaluate functions as native Java lambdas, providing a seamless \
                real-time graphing experience.
                
                CORE FEATURES:
                 • AST-Based Evaluation: Compiles math strings to native Java lambdas.
                 • Symbolic Math: Analytical differentiation powered by Symja.
                 • Implicit Plotting: Multithreaded caching for complex relations.
                 • Neon UI: Cyberpunk dark-mode with JetBrains Mono typography.
                 • Interactive LaTeX: MathLive-powered equation input.
                 • High Fidelity: Symmetric difference quotient for curve stability.
                
                Built for speed. Designed for discovery.
                
                Developed by Nahid Iqbal and Rafid Muammar.""";

        alert.setContentText(description);

        // Style the Dialog
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/org/example/equation_plotter/style.css")).toExternalForm());
        dialogPane.getStyleClass().add("about-dialog");
        alert.showAndWait();
    }

    @FXML
    private void onDerCalc() {
        Map<String, EquationData> activeEqns = GraphPlotter.getCurrentEquations();

        if (activeEqns.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Equations");
            alert.setHeaderText(null);
            alert.setContentText("No equations are currently plotted.\nAdd an equation before using the derivative calculator.");
            alert.getDialogPane().setPrefSize(360, 160);
            DialogPane dp = alert.getDialogPane();
            dp.getStylesheets().add(Objects.requireNonNull(getClass().getResource(
                    "/org/example/equation_plotter/style.css")).toExternalForm());
            dp.getStyleClass().add("compact-dialog");
            alert.showAndWait();
            return;
        }

        // Filter out implicit equations — derivative only works on explicit
        Map<String, EquationData> explicitOnly = activeEqns.entrySet().stream()
                .filter(e -> e.getValue().parser != null
                        && !e.getValue().parser.eqtype.equals(EquationParser.EqType.Implicit)
                        && !e.getValue().isPolar())
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue));

        if (explicitOnly.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Compatible Equations");
            alert.setHeaderText(null);
            alert.setContentText("Derivative calculator only works on explicit equations (e.g. x^2).\nImplicit , polar and parametric equations are not supported.");
            alert.getDialogPane().setPrefSize(360, 160);
            DialogPane dp = alert.getDialogPane();
            dp.getStylesheets().add(Objects.requireNonNull(getClass().getResource(
                    "/org/example/equation_plotter/style.css")).toExternalForm());
            dp.getStyleClass().add("compact-dialog");
            alert.showAndWait();
            return;
        }

        EquationSelector selector = new EquationSelector(
                explicitOnly,
                0, 1000
        );
        selector.show();
    }

    @FXML
    void onIntCalc() {
        var eqMap = GraphPlotter.getCurrentEquations();

        // No equations at all
        if (eqMap.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Equations");
            alert.setHeaderText(null);
            alert.setContentText("No equations are currently plotted.\nAdd an equation before using the integral calculator.");
            alert.getDialogPane().setPrefSize(360, 160);
            DialogPane dp = alert.getDialogPane();
            dp.getStylesheets().add(Objects.requireNonNull(getClass().getResource(
                    "/org/example/equation_plotter/style.css")).toExternalForm());
            dp.getStyleClass().add("compact-dialog");
            alert.showAndWait();
            return;
        }

        // Filter out equations with no valid parser
        var validEqMap = eqMap.entrySet().stream()
                .filter(e -> e.getValue().parser != null && e.getValue().isVisible)
                .collect(java.util.stream.Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        java.util.Map.Entry::getValue));

        if (validEqMap.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Valid Equations");
            alert.setHeaderText(null);
            alert.setContentText("No valid equations found.\nMake sure at least one equation is visible and correctly entered.");
            alert.getDialogPane().setPrefSize(360, 160);
            DialogPane dp = alert.getDialogPane();
            dp.getStylesheets().add(Objects.requireNonNull(getClass().getResource(
                    "/org/example/equation_plotter/style.css")).toExternalForm());
            dp.getStyleClass().add("compact-dialog");
            alert.showAndWait();
            return;
        }

        // Pass the first valid equation as default
        integralCalc calc = new integralCalc(validEqMap.values().iterator().next());
        calc.show();
    }

    @FXML
    void onExport() {
        if (mainController != null) {
            mainController.handleExportImage();
        }
    }


    @FXML
    void onToggleTheme() {
        if (mainController != null) {
            mainController.toggleTheme();
        }
    }
}
