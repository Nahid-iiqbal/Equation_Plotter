package org.example.equation_plotter;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuItem;

import java.util.Map;

public class NavBar {
    @FXML
    private MenuItem derivativeCalculator;
    @FXML
    private MenuItem menuNew;
    @FXML
    private MenuItem menuOpen;
    @FXML
    private MenuItem menuSave;
    @FXML
    private MenuItem menuClose;
    @FXML
    private MenuItem menuDelete;
    @FXML
    private MenuItem menuAbout;
    private EquatorController mainController;

    public void setMainController(EquatorController mainController) {
        this.mainController = mainController;
    }

    @FXML
    void onNew(ActionEvent event) {
        if (mainController != null) mainController.handleNewFile(event);
    }

    @FXML
    void onOpen(ActionEvent event) {
        if (mainController != null) mainController.handleOpenFile(event);
    }

    @FXML
    void onSave(ActionEvent event) {
        if (mainController != null) mainController.handleSaveFile(event);
    }

    @FXML
    void onClose(ActionEvent event) {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    void onDelete(ActionEvent event) {
        // Implementation for clearing specific data if needed
    }

    @FXML
    void onAbout(ActionEvent event) {
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
        dialogPane.getStylesheets().add(getClass().getResource("/org/example/equation_plotter/style.css").toExternalForm());
        dialogPane.getStyleClass().add("about-dialog");
        alert.showAndWait();
    }

    @FXML
    private void onDerCalc(ActionEvent event) {
        // Get the map of current equations from the plotter
        Map<String, EquationData> activeEqns = GraphPlotter.getCurrentEquations();

        if (activeEqns.isEmpty()) {
            // You can add an alert here if no equations are present
            return;
        }

        // Open the new selection window
        EquationSelector selector = new EquationSelector(
                activeEqns,
                0, 1000
        );
        selector.show();
    }
}
