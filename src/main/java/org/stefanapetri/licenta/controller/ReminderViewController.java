package org.stefanapetri.licenta.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.stefanapetri.licenta.model.MemoViewItem; // MODIFIED: Import MemoViewItem
import org.stefanapetri.licenta.view.MarkdownConverter;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class ReminderViewController {

    @FXML private Text dateText;
    @FXML private WebView reminderWebView;
    @FXML private Button okButton;

    // MODIFIED: Accepts MemoViewItem
    public void setMemo(MemoViewItem memo) {
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
        dateText.setText(memo.createdAt().toLocalDateTime().format(formatter));
        // Uses transcriptionText from MemoViewItem
        reminderWebView.getEngine().loadContent(MarkdownConverter.toHtml(memo.transcriptionText()));
    }

    @FXML
    private void handleOk() {
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }
}