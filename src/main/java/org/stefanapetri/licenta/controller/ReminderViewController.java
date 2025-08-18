package org.stefanapetri.licenta.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.stefanapetri.licenta.model.Memo;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class ReminderViewController {

    @FXML private Text dateText;
    @FXML private TextArea reminderTextArea;
    @FXML private Button okButton;

    public void setMemo(Memo memo) {
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
        dateText.setText(memo.createdAt().toLocalDateTime().format(formatter));
        reminderTextArea.setText(memo.transcriptionText());
    }

    @FXML
    private void handleOk() {
        // Get the stage the button is on and close it
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }
}