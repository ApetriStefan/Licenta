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
import org.stefanapetri.licenta.model.Memo;
import org.stefanapetri.licenta.model.TrackedApplication;

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
            // --- THIS IS THE CORRECTED LINE ---
            // We simply call the helper method. We don't need to call .show() on its result.
            createTopMostAlert(
                    Alert.AlertType.ERROR,
                    "UI Error",
                    "Could not load the Reminder window.",
                    "Please check the FXML file and controller for errors. Details: " + e.getMessage()
            );
        }
    }

    public static Stage showRecordingDialog(TrackedApplication app) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("RecordingView.fxml"));
            Parent root = loader.load();

            RecordingController controller = loader.getController();
            controller.setAppName(app.getAppName());

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setTitle("Recording...");
            stage.setScene(new Scene(root));

            stage.setAlwaysOnTop(true);
            stage.show();
            stage.toFront();
            stage.requestFocus();

            return stage;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Optional<ButtonType> createTopMostAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setAlwaysOnTop(true);

        // The alert is shown here, and we wait for the user's response.
        return alert.showAndWait();
    }
}