package org.stefanapetri.licenta.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.web.WebView; // NEW IMPORT
import javafx.stage.Stage;
import org.stefanapetri.licenta.view.MarkdownConverter; // NEW IMPORT

import java.io.File;
import java.net.MalformedURLException;

public class TranscriptionResultController {

    @FXML private WebView transcriptionWebView; // MODIFIED: Changed from TextArea to WebView
    @FXML private Button playRecordingButton;
    @FXML private Button okButton;

    private String audioFilePath;
    private MediaPlayer mediaPlayer;

    public void setContent(String transcription, String audioFilePath) {
        // MODIFIED: Load HTML content into WebView
        transcriptionWebView.getEngine().loadContent(MarkdownConverter.toHtml(transcription));
        this.audioFilePath = audioFilePath;

        if (audioFilePath != null && new File(audioFilePath).exists()) {
            playRecordingButton.setDisable(false);
        } else {
            playRecordingButton.setDisable(true);
        }
    }

    @FXML
    private void handlePlayRecording() {
        if (audioFilePath == null || !new File(audioFilePath).exists()) {
            System.err.println("No audio file available for playback or file does not exist.");
            return;
        }

        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }

            Media media = new Media(new File(audioFilePath).toURI().toURL().toString());
            mediaPlayer = new MediaPlayer(media);

            mediaPlayer.setOnEndOfMedia(() -> {
                System.out.println("Audio playback finished.");
            });

            mediaPlayer.play();
            System.out.println("Playing audio from: " + audioFilePath);

        } catch (MalformedURLException e) {
            System.err.println("Invalid audio file path URL: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error during audio playback: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleOk() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }
}