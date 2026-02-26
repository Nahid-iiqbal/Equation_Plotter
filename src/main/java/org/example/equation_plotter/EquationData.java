package org.example.equation_plotter;

import javafx.scene.paint.Color;

public class EquationData {
    String raw;
    EquationParser parser;
    Color color;
    int r, g, b;

    private double[] yCache;
    private double step;
    private double xStart;
    private int startIndex;
    private int size;

    public void buildCacheExplicit(double visibleMinX, double visibleMaxX, double width) {
        if (parser.isImplicit()) return;
        double visibleWidth = visibleMaxX - visibleMinX;
        double bufferWidth = visibleWidth * 3;
        size = (int) (width * 3 * 2);
        step = bufferWidth / size;
        xStart = visibleMinX - visibleWidth;
        yCache = new double[size];
        for (int i = 0; i < size; i++) {
            double x = xStart + i * step;
            yCache[i] = parser.evaluateExplicit(x);
        }
        startIndex = 0;
    }

    public double getY(double graphX) {
        if (yCache == null) return Double.NaN;
        double fIndex = (graphX - xStart) / step;
        int i0 = (int) Math.floor(fIndex);
        int i1 = i0 + 1;
        if (i0 < 0 || i1 >= size) return Double.NaN;
        double y0 = yCache[(startIndex + i0) % size];
        double y1 = yCache[(startIndex + i1) % size];
        double t = fIndex - i0;
        return y0 + t * (y1 - y0);
    }

    public void setColor(Color color) {
        this.color = color;
        this.r = (int) (color.getRed() * 255);
        this.g = (int) (color.getGreen() * 255);
        this.b = (int) (color.getBlue() * 255);
    }
}
