package org.example.equation_plotter;

import javafx.scene.paint.Color;

public class Points {
    private double x;
    private double y;
    private Color color;

    public Points(double x, double y, Color color) {
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Color getColor() {
        return color;
    }
}
