module org.stefanapetri.licenta {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.graphics;
    requires javafx.media;
    requires javafx.web; // <--- NEW: Crucial for WebView

    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires java.sql;
    requires java.prefs;
    requires java.desktop;
    requires org.commonmark; // <--- NEW: For the Markdown parser

    opens org.stefanapetri.licenta to javafx.fxml;
    opens org.stefanapetri.licenta.controller to javafx.fxml;
    opens org.stefanapetri.licenta.model to javafx.base;

    exports org.stefanapetri.licenta;
}