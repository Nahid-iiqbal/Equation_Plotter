package org.example.equation_plotter;

import javafx.scene.paint.Color;

import java.util.Arrays;

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
    public boolean isVisible = true;

    EquationParser.EqType eqType;
    // new flags for polar
    public double thetaMin = 0;
    public double tMin = 0.0;
    public double thetaMax = 2 * Math.PI;
    public double tMax = 2 * Math.PI;


    // caches for polar and parametrix
    public double[] CacheX;
    public double[] CacheY;

    public EquationData() {}

    public EquationData(EquationData data) {
        this.raw = data.raw;
        this.parser = data.parser;
        this.color = data.color;
        this.r = data.r;
        this.g = data.g;
        this.b = data.b;
        this.step = data.step;
        this.xStart = data.xStart;
        this.startIndex = data.startIndex;
        this.size = data.size;
        this.isVisible = data.isVisible;
        this.eqType = data.eqType;
        //this.isPolar = data.isPolar;
        this.thetaMin = data.thetaMin;
        this.thetaMax = data.thetaMax;
        this.tMin = data.tMin;
        this.tMax = data.tMax;
        this.yCache = data.yCache != null
                ? Arrays.copyOf(data.yCache, data.yCache.length) : null;
        this.CacheX = data.CacheX != null
                ? Arrays.copyOf(data.CacheX, data.CacheX.length) : null;
        this.CacheY = data.CacheY != null
                ? Arrays.copyOf(data.CacheY, data.CacheY.length) : null;
    }

    public void buildCacheExplicit(double visibleMinX, double visibleMaxX, double width) {
        if (parser.eqtype == EquationParser.EqType.Implicit) return;
        double visibleWidth = visibleMaxX - visibleMinX;
        double bufferWidth = visibleWidth * 3;
        size = (int) (width * 3 * 10);
        step = bufferWidth / size;
        xStart = visibleMinX - visibleWidth;
        yCache = new double[size];
        for (int i = 0; i < size; i++) {
            double x = xStart + i * step;
            yCache[i] = parser.evaluateExplicit(x);
        }
        startIndex = 0;
    }

    public void buildCachePolar(double thetaMin, double thetaMax, double width) {
        if (eqType != EquationParser.EqType.Polar || parser == null) return;

        // number of samples: base on pixel width * factor for smoothness
        int samples = Math.max(200, (int) (width * 1.5));
        CacheX = new double[samples + 1];
        CacheY = new double[samples + 1];
        double step = (thetaMax - thetaMin) / samples;

        for (int i = 0; i <= samples; i++) {
            double t = thetaMin + i * step;
            double r = parser.evaluatePolar(t);
            if (Double.isNaN(r) || Double.isInfinite(r)) {
                CacheX[i] = Double.NaN;
                CacheY[i] = Double.NaN;
            } else {
                CacheX[i] = r * Math.cos(t);
                CacheY[i] = r * Math.sin(t);
            }
        }
    }

    public void buildCacheParametric(double t0, double t1, double width) {
        if (parser.eqtype != EquationParser.EqType.Parametric || parser == null) return;

        double stepSize = 0.01;
        double deltaT = t1 - t0;
        int samples = (int) Math.ceil(Math.abs(deltaT) / stepSize) + 1;

        CacheX = new double[samples];
        CacheY = new double[samples];

        for (int i = 0; i < samples; i++) {
            // Linear interpolation of t to ensure precision at the bounds
            double t = t0 + (i * stepSize);
            if (deltaT > 0 && t > t1) t = t1;
            if (deltaT < 0 && t < t1) t = t1;

            double[] p = parser.evaluateParametric(t);

            // Fail-safe: Store NaN for non-real coordinates to prevent rendering artifacts
            if (p == null || Double.isNaN(p[0]) || Double.isNaN(p[1]) ||
                    Double.isInfinite(p[0]) || Double.isInfinite(p[1])) {
                CacheX[i] = Double.NaN;
                CacheY[i] = Double.NaN;
            } else {
                CacheX[i] = p[0];
                CacheY[i] = p[1];
            }
        }
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

    // Replace the field with a method:
    public boolean isPolar() {
        return eqType == EquationParser.EqType.Polar;
    }
}