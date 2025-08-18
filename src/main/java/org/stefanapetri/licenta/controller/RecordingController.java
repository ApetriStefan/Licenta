package org.stefanapetri.licenta.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class RecordingController {

    @FXML private Label titleLabel;
    @FXML private Button stopButton;

    public void setAppName(String name) {
        titleLabel.setText("Recording Memo for " + name);
    }

    @FXML
    private void handleStop() {
        // Simply close the window. The MainController will handle the logic.
        Stage stage = (Stage) stopButton.getScene().getWindow();
        stage.close();
    }
}