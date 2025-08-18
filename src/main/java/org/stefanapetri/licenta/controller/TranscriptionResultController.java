package org.stefanapetri.licenta.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;

import java.io.File;
import java.net.MalformedURLException;

public class TranscriptionResultController {

    @FXML private TextArea transcriptionTextArea;
    @FXML private Button playRecordingButton;
    @FXML private Button okButton;

    private String audioFilePath;
    private MediaPlayer mediaPlayer; // Keep a reference to the media player

    /**
     * Sets the transcription text and the path to the recorded audio file.
     * @param transcription The text received from Whisper.
     * @param audioFilePath The path to the WAV file.
     */
    public void setContent(String transcription, String audioFilePath) {
        transcriptionTextArea.setText(transcription);
        this.audioFilePath = audioFilePath;

        // Enable/disable play button based on whether a valid audio file path exists
        if (audioFilePath != null && new File(audioFilePath).exists()) {
            playRecordingButton.setDisable(false);
        } else {
            playRecordingButton.setDisable(true);
        }
    }

    @FXML
    private void handlePlayRecording() {
        if (audioFilePath == null || !new File(audioFilePath).exists()) {
            // This case should be handled by disabling the button, but as a fallback:
            System.err.println("No audio file available for playback or file does not exist.");
            return;
        }

        try {
            // Stop any currently playing audio before starting a new one
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }

            // Create Media and MediaPlayer objects
            Media media = new Media(new File(audioFilePath).toURI().toURL().toString());
            mediaPlayer = new MediaPlayer(media);

            // Add a listener to reset button state or perform cleanup after playback
            mediaPlayer.setOnEndOfMedia(() -> {
                // You could add logic here, e.g., reset a "playing" indicator
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
        // Stop playback before closing the dialog
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose(); // Release resources
            mediaPlayer = null;
        }
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }
}