package org.stefanapetri.licenta.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.stefanapetri.licenta.MainApplication;
import org.stefanapetri.licenta.controller.RecordingController;
import org.stefanapetri.licenta.controller.ReminderViewController;
import org.stefanapetri.licenta.model.Memo;
import org.stefanapetri.licenta.model.TrackedApplication;

import java.io.IOException;

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
            stage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
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
            stage.show(); // Use show() instead of showAndWait() so it's not blocking
            return stage;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}