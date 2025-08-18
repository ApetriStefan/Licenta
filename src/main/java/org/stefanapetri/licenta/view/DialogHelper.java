package org.stefanapetri.licenta.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.stefanapetri.licenta.MainApplication;
import org.stefanapetri.licenta.controller.RecordingController;
import org.stefanapetri.licenta.controller.ReminderViewController;
import org.stefanapetri.licenta.controller.TranscribingController;
import org.stefanapetri.licenta.controller.TranscriptionResultController;
import org.stefanapetri.licenta.model.Memo;
import org.stefanapetri.licenta.model.TrackedApplication;
import org.stefanapetri.licenta.service.AudioRecorder;

import javax.sound.sampled.LineUnavailableException; // NEW IMPORT
import java.io.IOException;
import java.util.Optional;



public class DialogHelper {

    public static void showReminderDialog(Memo memo) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("Reminder.fxml"));
            Parent root = loader.load();

            ReminderViewController controller = loader.getController();
            controller.setMemo(memo);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Last Session Reminder");
            stage.setScene(new Scene(root));

            stage.setAlwaysOnTop(true);
            stage.show();
            stage.toFront();
            stage.requestFocus();

        } catch (IOException e) {
            e.printStackTrace();
            createTopMostAlert(
                    Alert.AlertType.ERROR,
                    "UI Error",
                    "Could not load the Reminder window.",
                    "Please check the FXML file and controller for errors. Details: " + e.getMessage()
            );
        }
    }

    // --- MODIFIED: showRecordingDialog now correctly calls startRecording ---
    public static StageAndController<RecordingController> showRecordingDialog(TrackedApplication app, AudioRecorder recorder, String audioFilePath) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("RecordingView.fxml"));
            Parent root = loader.load();

            RecordingController controller = loader.getController();
            controller.setAppName(app.getAppName());

            // --- NEW: Start recording HERE and pass the visualizer consumer ---
            // Catch LineUnavailableException here, as it directly relates to starting the mic.
            try {
                recorder.startRecording(audioFilePath, controller.getAudioDataConsumer());
            } catch (LineUnavailableException e) {
                createTopMostAlert(
                        Alert.AlertType.ERROR, "Recording Error",
                        "Microphone not available or not supported.", e.getMessage()
                );
                return null; // Cannot proceed without mic access
            }
            // --- END NEW ---

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setTitle("Recording...");
            stage.setScene(new Scene(root));

            stage.setAlwaysOnTop(true);
            stage.show();
            stage.toFront();
            stage.requestFocus();

            return new StageAndController<>(stage, controller);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Stage showTranscribingDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("TranscribingView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setTitle("Transcribing...");
            stage.setScene(new Scene(root));

            stage.setAlwaysOnTop(true);
            stage.show();
            stage.toFront();
            stage.requestFocus();

            return stage;
        } catch (IOException e) {
            e.printStackTrace();
            createTopMostAlert(
                    Alert.AlertType.ERROR,
                    "UI Error",
                    "Could not load the Transcribing window.",
                    "Details: " + e.getMessage()
            );
            return null;
        }
    }

    public static void showTranscriptionResultDialog(String transcription, String audioFilePath) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("TranscriptionResultView.fxml"));
            Parent root = loader.load();

            TranscriptionResultController controller = loader.getController();
            controller.setContent(transcription, audioFilePath);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Transcription Result");
            stage.setScene(new Scene(root));

            stage.setAlwaysOnTop(true);
            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            createTopMostAlert(
                    Alert.AlertType.ERROR,
                    "UI Error",
                    "Could not load the Transcription Result window.",
                    "Details: " + e.getMessage()
            );
        }
    }

    public static Optional<ButtonType> createTopMostAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        return alert.showAndWait();
    }
}