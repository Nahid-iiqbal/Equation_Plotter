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
//    public boolean isPolar = false;
    public double thetaMin = 0;
    public double thetaMax = 2 * Math.PI;

    public double tMin = 0.0;
    public double tMax = 1.0;


    // caches for polar and parametrix
    public double[] CacheX;
    public double[] CacheY;

    public EquationData() {
    }

    public EquationData(EquationData data) {
        this.raw = data.raw;
        this.parser = data.parser;
        this.color = data.color;
        this.r = data.r;
        this.g = data.g;
        this.b = data.b;
        this.yCache = data.yCache;
        this.step = data.step;
        this.xStart = data.xStart;
        this.startIndex = data.startIndex;
        this.size = data.size;
        if (data.yCache != null) {
            this.yCache = Arrays.copyOf(data.yCache, data.yCache.length);
        }
    }


    public void buildCacheExplicit(double visibleMinX, double visibleMaxX, double width) {
        if (parser.eqtype == EquationParser.EqType.Implicit) return;
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
        // determine number of samples relative to pixel width (oversample a bit)
        int samples = Math.max(300, (int)(width * 1.5));
        double[] xs = new double[samples+1];
        double[] ys = new double[samples+1];
        for (int i = 0; i <= samples; i++) {
            double t = t0 + (t1 - t0) * i / (double)samples;
            // prefer parser-provided evaluator:
            double p[] = this.parser.evaluateParametric(t);
            double x,y;
            if (p != null && !Double.isNaN(p[0]) && !Double.isNaN(p[1])) {
                x = p[0];
                y = p[1];
                xs[i] = x;
                ys[i] = y;
            } else {
                xs[i] = Double.NaN;
                ys[i] = Double.NaN;
            }
        }
        this.CacheX = xs;
        this.CacheY = ys;
    }

    public void setThetaRange(double min, double max) {
        thetaMin = min;
        thetaMax = max;
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

    public double calculateIntegral(double a, double b, int n) {
        if (n % 2 != 0) n++; // Simpson's rule requires an even number of intervals
        double h = (b - a) / n;
        double sum = parser.evaluateExplicit(a) + parser.evaluateExplicit(b);

        for (int i = 1; i < n; i++) {
            double x = a + i * h;
            double y = parser.evaluateExplicit(x);
            // Odd indices get weight 4, even indices get weight 2
            sum += (i % 2 != 0) ? 4 * y : 2 * y;
        }
        return sum * (h / 3.0);
    }

}
