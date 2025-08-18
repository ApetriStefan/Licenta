module org.stefanapetri.licenta {
    // === JavaFX Modules ===
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.graphics; // Good practice to explicitly require graphics as well

    // === External Dependencies ===
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires java.sql;
    requires java.prefs;

    // === Opening Packages for Reflection ===
    opens org.stefanapetri.licenta to javafx.fxml;
    opens org.stefanapetri.licenta.controller to javafx.fxml;
    opens org.stefanapetri.licenta.model to javafx.base;

    // === Exporting Packages for Direct Access ===
    // This is the new line that fixes the error.
    // It allows javafx.graphics to construct your MainApplication class.
    exports org.stefanapetri.licenta;
}