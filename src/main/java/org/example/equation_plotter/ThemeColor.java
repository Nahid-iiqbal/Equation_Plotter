package org.example.equation_plotter;

import javafx.scene.paint.Color;

public enum ThemeColor {
    // Semantic Name   (Dark Mode Hex, Light Mode Hex)
    BACKGROUND("#1e1e1e", "#f5f5f5"),
    GRID_MINOR("#2a2a3a", "#d0d0d0"),
    GRID_MAJOR("#2e2e42", "#b0b0b0"),
    AXIS_MAIN("#c8c8d8", "#333333"),
    TEXT_PRIMARY("#FFFFFF", "#000000"),
    TEXT_SECONDARY("#999999", "#555555"),

    // UI Elements
    PILL_BACKGROUND("rgba(13,13,26,0.82)", "rgba(230,230,230,0.85)"),
    PILL_BORDER("rgba(0,255,255,0.22)", "rgba(0,0,0,0.22)"),

    // Accent (Kept same for both, or tweak the light mode cyan if it's too bright on white)
    ACCENT_PRIMARY("#00FFFF", "#00AADD");

    private final String darkHex;
    private final String lightHex;

    ThemeColor(String darkHex, String lightHex) {
        this.darkHex = darkHex;
        this.lightHex = lightHex;
    }

    /**
     * Returns the parsed JavaFX Color for canvas rendering
     */
    public Color getColor(boolean isLightMode) {
        return Color.web(isLightMode ? lightHex : darkHex);
    }

    /**
     * Returns the parsed JavaFX Color with a specific opacity applied
     */
    public Color getColor(boolean isLightMode, double opacity) {
        return Color.web(isLightMode ? lightHex : darkHex, opacity);
    }

    /**
     * Returns the raw CSS string for styling nodes
     */
    public String getCss(boolean isLightMode) {
        return isLightMode ? lightHex : darkHex;
    }
}