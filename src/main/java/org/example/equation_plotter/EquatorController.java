package org.example.equation_plotter;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

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

    private GraphPlotter graphPlotter;
    private int addEqCount = 0;

    @FXML
    public void initialize() {
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
        AnchorPane.setRightAnchor(btn_home, 310.0);
        AnchorPane.setRightAnchor(btn_zoom_in, 310.0);
        AnchorPane.setRightAnchor(btn_zoom_out, 310.0);
    }

    @FXML
    protected void btnAddPressed() {
        addEquation();
    }

    private void addEquation() {
        String id = "eq-" + System.nanoTime();
        TextField equationInput = new TextField();
        equationInput.setId(id);  // time based id for each textfield, later used for currentEquations hashmap key
        equationInput.setPromptText("y=f(x)");
        equationInput.getStyleClass().add("glass-input");

        equationInput.setOnAction(e -> graphPlotter.addEquationToHashmap(id, equationInput.getText()));

        Button btn_rmv = new Button();
        btn_rmv.getStyleClass().add("icon-button");
        FontIcon btn_rmv_icon = new FontIcon("fas-times");
        btn_rmv_icon.setIconColor(Color.web("#ff4444"));
        btn_rmv_icon.setIconSize(18);
        btn_rmv.setGraphic(btn_rmv_icon);


        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 0, 5, 0));
        row.getChildren().addAll(equationInput, btn_rmv);

        btn_rmv.setOnAction(event -> {
            if (addEqCount >= 2) {
                equation_container.getChildren().remove(row);
                graphPlotter.removeEquation(id);
                addEqCount--;
            }
        });

        equation_container.getChildren().add(row);
        equationInput.requestFocus();
        addEqCount++;
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

}
