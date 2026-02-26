package org.example.equation_plotter;

import java.util.List;

public class CachedImplicit {
    List<double[]> lines;
    double scale;
    double cx, cy;

    public CachedImplicit(List<double[]> lines, double scale, double cx, double cy) {
        this.lines = lines;
        this.scale = scale;
        this.cx = cx;
        this.cy = cy;
    }
}
