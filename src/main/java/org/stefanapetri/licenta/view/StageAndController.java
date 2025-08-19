package org.stefanapetri.licenta.view;

import javafx.stage.Stage;

// --- NEW: StageAndController helper class definition ---
// This class bundles a Stage and its associated Controller
public class StageAndController<T> {
    public final Stage stage;
    public final T controller;

    public StageAndController(Stage stage, T controller) {
        this.stage = stage;
        this.controller = controller;
    }
}
