module org.example.equation_plotter {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires eu.hansolo.tilesfx;
    requires org.kordamp.ikonli.fontawesome5;
    requires java.desktop;
    requires MathParser.org.mXparser;
    requires atlantafx.base;
    requires javafx.swing;
    requires java.logging;
    requires jdk.jsobject;

    opens org.example.equation_plotter to javafx.fxml;
    exports org.example.equation_plotter;
}