package org.example.equation_plotter;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        System.setProperty("log4j2.loggerContextFactory",
                "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
        System.setProperty("prism.order", "d3d,es2,sw");
        System.setProperty("prism.lcdtext", "false");

        Thread.currentThread().setContextClassLoader(Launcher.class.getClassLoader());

        try {
            Class.forName("org.matheclipse.core.expression.F");
            org.matheclipse.core.expression.F.initSymbols();
            Class.forName("org.matheclipse.core.eval.ExprEvaluator");
        } catch (Throwable t) {
            System.err.println("Symja pre-init failed: " + t.getMessage());
        }

        Application.launch(Equator.class, args);
    }
}