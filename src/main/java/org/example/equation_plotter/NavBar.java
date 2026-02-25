package org.example.equation_plotter;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;

public class NavBar {

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
        alert.setTitle("About Equation Plotter");
        alert.setHeaderText("Equation Plotter v1.0");
        alert.setContentText("A high-performance graphing calculator.\n\nBuilt with JavaFX and mXparser.");
        alert.showAndWait();
    }
}
