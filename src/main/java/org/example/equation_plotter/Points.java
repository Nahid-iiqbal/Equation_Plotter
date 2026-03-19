package org.example.equation_plotter;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

public record Points(double x, double y, Color color) {
    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Paint getColor() {
        return color;
    }
}
