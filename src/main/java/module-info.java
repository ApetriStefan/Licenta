// src\main\java\module-info.java
module org.stefanapetri.licenta {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.graphics;
    requires javafx.media;
    requires javafx.web;
    requires java.management;

    // Corrected Jackson module names
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation; // <--- CORRECTED: Changed from 'annotations' to 'annotation'
    requires com.fasterxml.jackson.databind;

    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires java.sql;
    requires java.prefs;
    requires java.desktop;
    requires org.commonmark;

    opens org.stefanapetri.licenta to javafx.fxml;
    opens org.stefanapetri.licenta.controller to javafx.fxml;
    opens org.stefanapetri.licenta.model to javafx.base;

    exports org.stefanapetri.licenta;
}