package org.example.equation_plotter;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        System.setProperty("prism.order", "d3d,es2,sw");
        System.setProperty("prism.lcdtext", "false");
        Application.launch(Equator.class, args);
    }
}