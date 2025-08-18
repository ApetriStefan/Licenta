module org.stefanapetri.licenta {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.graphics;
    requires javafx.media; // <--- NEW: Required for MediaPlayer and Media classes

    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires java.sql;
    requires java.prefs;
    requires java.desktop;

    opens org.stefanapetri.licenta to javafx.fxml;
    opens org.stefanapetri.licenta.controller to javafx.fxml;
    opens org.stefanapetri.licenta.model to javafx.base;

    exports org.stefanapetri.licenta;
}