package org.stefanapetri.licenta.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.text.Text;
import javafx.scene.web.WebView; // NEW IMPORT
import javafx.stage.Stage;
import org.stefanapetri.licenta.model.Memo;
import org.stefanapetri.licenta.view.MarkdownConverter; // NEW IMPORT

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class ReminderViewController {

    @FXML private Text dateText;
    @FXML private WebView reminderWebView; // MODIFIED: Changed from TextArea to WebView
    @FXML private Button okButton;

    public void setMemo(Memo memo) {
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
        dateText.setText(memo.createdAt().toLocalDateTime().format(formatter));
        // MODIFIED: Load HTML content into WebView
        reminderWebView.getEngine().loadContent(MarkdownConverter.toHtml(memo.transcriptionText()));
    }

    @FXML
    private void handleOk() {
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }
}