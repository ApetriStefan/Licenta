package org.stefanapetri.licenta.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.stefanapetri.licenta.MainApplication;
import org.stefanapetri.licenta.controller.RecordingController;
import org.stefanapetri.licenta.controller.ReminderViewController;
import org.stefanapetri.licenta.controller.TranscriptionResultController;
import org.stefanapetri.licenta.model.MemoViewItem;
import org.stefanapetri.licenta.model.TrackedApplication;
import org.stefanapetri.licenta.service.AudioRecorder;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.util.Optional;

public class DialogHelper {

    private static void applyThemeToScene(Scene scene) {
        String css = MainApplication.class.getResource("style.css").toExternalForm();
        scene.getStylesheets().add(css);
    }

    private static void applyThemeToAlert(Alert alert) {
        DialogPane dialogPane = alert.getDialogPane();
        String css = MainApplication.class.getResource("style.css").toExternalForm();
        dialogPane.getStylesheets().add(css);
        dialogPane.getStyleClass().add("root"); // Use the root style class from CSS
    }

    private static void applyDefaultStageSettings(Stage stage, Scene scene) {
        applyThemeToScene(scene);
        stage.setScene(scene);
        if (MainApplication.applicationIcon != null) {
            stage.getIcons().add(MainApplication.applicationIcon);
        }
        stage.setAlwaysOnTop(true);
    }

    private static void showStage(Stage stage) {
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    public static void showReminderDialog(MemoViewItem memo) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("Reminder.fxml"));
            Parent root = loader.load();

            ReminderViewController controller = loader.getController();
            controller.setMemo(memo);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Last Session Reminder");

            Scene scene = new Scene(root);
            applyDefaultStageSettings(stage, scene);
            showStage(stage);

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

    public static StageAndController<RecordingController> showRecordingDialog(TrackedApplication app, AudioRecorder recorder, String audioFilePath) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("RecordingView.fxml"));
            Parent root = loader.load();

            RecordingController controller = loader.getController();
            controller.setAppName(app.getAppName());

            try {
                recorder.startRecording(audioFilePath, controller.getAudioDataConsumer());
            } catch (LineUnavailableException e) {
                createTopMostAlert(
                        Alert.AlertType.ERROR, "Recording Error",
                        "Microphone not available or not supported.", e.getMessage()
                );
                return null;
            }

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setTitle("Recording...");

            Scene scene = new Scene(root);
            applyDefaultStageSettings(stage, scene);
            showStage(stage);

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

            Scene scene = new Scene(root);
            applyDefaultStageSettings(stage, scene);
            showStage(stage);

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

    public static void showTranscriptionResultDialog(String transcription, String audioFilePath, boolean enablePlayback) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("TranscriptionResultView.fxml"));
            Parent root = loader.load();

            TranscriptionResultController controller = loader.getController();
            controller.setContent(transcription, audioFilePath, enablePlayback);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Transcription Result");

            Scene scene = new Scene(root);
            applyDefaultStageSettings(stage, scene);
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

        applyThemeToAlert(alert);

        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        if (MainApplication.applicationIcon != null) {
            stage.getIcons().add(MainApplication.applicationIcon);
        }
        stage.setAlwaysOnTop(true);

        return alert.showAndWait();
    }
}