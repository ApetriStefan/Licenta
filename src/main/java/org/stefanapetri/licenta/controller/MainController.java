package org.stefanapetri.licenta.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.stefanapetri.licenta.model.DatabaseManager;
import org.stefanapetri.licenta.model.Memo;
import org.stefanapetri.licenta.model.TrackedApplication;
import org.stefanapetri.licenta.service.AudioRecorder;
import org.stefanapetri.licenta.service.PythonBridge;
import org.stefanapetri.licenta.service.SystemMonitor;
import org.stefanapetri.licenta.service.SystemMonitorListener;
import org.stefanapetri.licenta.view.DialogHelper;

import javax.sound.sampled.LineUnavailableException;
import java.io.File;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController implements Initializable, SystemMonitorListener {

    // --- FXML Fields ---
    @FXML private TableView<TrackedApplication> appTableView;
    @FXML private TableColumn<TrackedApplication, String> appNameColumn;
    @FXML private TableColumn<TrackedApplication, String> appPathColumn;
    @FXML private Button launchAppButton;
    @FXML private Button updateAppButton;
    @FXML private Button removeAppButton;
    @FXML private TextArea reminderTextArea;

    // --- Dependencies ---
    private final DatabaseManager dbManager;
    private final SystemMonitor systemMonitor;
    private final PythonBridge pythonBridge;
    private final AudioRecorder audioRecorder;

    // --- State ---
    private final ObservableList<TrackedApplication> trackedAppsList = FXCollections.observableArrayList();
    private boolean isRecording = false;
    private Memo currentMemo = null;

    public MainController(DatabaseManager dbManager, SystemMonitor systemMonitor, PythonBridge pythonBridge) {
        this.dbManager = dbManager;
        this.systemMonitor = systemMonitor;
        this.pythonBridge = pythonBridge;
        this.audioRecorder = new AudioRecorder();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        systemMonitor.setListener(this);
        appNameColumn.setCellValueFactory(new PropertyValueFactory<>("appName"));
        appPathColumn.setCellValueFactory(new PropertyValueFactory<>("executablePath"));
        appTableView.setItems(trackedAppsList);

        appTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        loadMemoForApp(newSelection);
                        updateButtonStates(true);
                        reminderTextArea.setEditable(true);
                    } else {
                        updateButtonStates(false);
                        currentMemo = null;
                        reminderTextArea.clear();
                        reminderTextArea.setEditable(false);
                    }
                }
        );

        loadApplicationsFromDB();
        updateButtonStates(false);
        reminderTextArea.setEditable(false);
    }

    private void loadApplicationsFromDB() {
        trackedAppsList.setAll(dbManager.getAllTrackedApplications());
        systemMonitor.setTrackedApplications(trackedAppsList);
        appTableView.getSelectionModel().clearSelection();
    }

    private void loadMemoForApp(TrackedApplication app) {
        Optional<Memo> latestMemo = dbManager.getLatestMemoForApp(app.getAppId());
        this.currentMemo = latestMemo.orElse(null);
        reminderTextArea.setText(latestMemo.map(Memo::transcriptionText)
                .orElse("No reminder found for this application."));
    }

    private void updateButtonStates(boolean itemSelected) {
        launchAppButton.setDisable(!itemSelected);
        updateAppButton.setDisable(!itemSelected);
        removeAppButton.setDisable(!itemSelected);
    }

    @FXML
    private void handleAddApp() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Application Executable");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executables", "*.exe"));
        File selectedFile = fileChooser.showOpenDialog(appTableView.getScene().getWindow());

        if (selectedFile != null) {
            String path = selectedFile.getAbsolutePath();
            String name = selectedFile.getName().replace(".exe", "");
            TextInputDialog dialog = new TextInputDialog(name);
            dialog.setTitle("Add Application");
            dialog.setHeaderText("Enter a display name for the application.");
            dialog.setContentText("Name:");
            Optional<String> result = dialog.showAndWait();
            result.ifPresent(appName -> dbManager.addTrackedApplication(appName, path)
                    .ifPresent(newApp -> loadApplicationsFromDB()));
        }
    }

    @FXML
    private void handleRemoveApp() {
        TrackedApplication selectedApp = appTableView.getSelectionModel().getSelectedItem();
        if (selectedApp != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Deletion");
            alert.setHeaderText("Remove '" + selectedApp.getAppName() + "'?");
            alert.setContentText("Are you sure you want to remove this application from the tracker? All associated reminders will be deleted.");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                dbManager.removeTrackedApplication(selectedApp.getAppId());
                loadApplicationsFromDB();
            }
        }
    }

    @FXML
    private void handleLaunchApp() {
        TrackedApplication selectedApp = appTableView.getSelectionModel().getSelectedItem();
        if (selectedApp != null) {
            try {
                new ProcessBuilder(selectedApp.getExecutablePath()).start();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Failed to launch application: " + e.getMessage()).show();
            }
        }
    }

    @FXML
    private void handleUpdateAppPath() {
        new Alert(Alert.AlertType.INFORMATION, "Update App Path feature not implemented yet.").show();
    }

    @FXML
    private void handleUpdateReminder() {
        TrackedApplication selectedApp = appTableView.getSelectionModel().getSelectedItem();
        if (selectedApp == null || currentMemo == null) {
            new Alert(Alert.AlertType.WARNING, "Please select an application that has a reminder to update.").show();
            return;
        }
        String updatedText = reminderTextArea.getText();
        int memoId = currentMemo.memoId();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Update");
        confirmation.setHeaderText("Update the reminder for '" + selectedApp.getAppName() + "'?");
        confirmation.setContentText("This will permanently overwrite the existing memo with your changes.");
        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            dbManager.updateMemoText(memoId, updatedText);
            new Alert(Alert.AlertType.INFORMATION, "Reminder updated successfully.").show();
        }
    }

    @Override
    public void onMonitoredAppClosed(TrackedApplication app) {
        if (isRecording) return;
        Platform.runLater(() -> {
            Alert prompt = new Alert(Alert.AlertType.CONFIRMATION);
            prompt.setTitle("Record a Memo");
            prompt.setHeaderText("You just closed " + app.getAppName());
            prompt.setContentText("Would you like to record a voice memo about what you were doing?");
            prompt.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    startRecordingProcess(app);
                }
            });
        });
    }

    private void startRecordingProcess(TrackedApplication app) {
        isRecording = true;
        String audioFilePath = "temp_memo.wav";
        try {
            audioRecorder.startRecording(audioFilePath);
            Stage recordingStage = DialogHelper.showRecordingDialog(app);
            recordingStage.setOnHidden(e -> {
                audioRecorder.stopRecording();
                isRecording = false;
                transcribeAndSave(app, audioFilePath); // This call is now correct
            });
        } catch (LineUnavailableException e) {
            isRecording = false;
            new Alert(Alert.AlertType.ERROR, "Microphone not available or not supported.").show();
        }
    }

    // --- MODIFIED: Corrected the method name to match the call (camelCase) ---
    private void transcribeAndSave(TrackedApplication app, String audioFilePath) {
        System.out.println("Starting transcription for " + audioFilePath);
        pythonBridge.transcribeAudio(audioFilePath).thenAccept(transcription -> {
            System.out.println("Transcription received: " + transcription);
            if (transcription != null && !transcription.startsWith("Error:")) {
                dbManager.saveMemo(app.getAppId(), transcription, audioFilePath);
                Platform.runLater(() -> {
                    if (app.equals(appTableView.getSelectionModel().getSelectedItem())) {
                        loadMemoForApp(app);
                    }
                });
            } else {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Transcription failed:\n" + transcription).show());
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    @Override
    public void onMonitoredAppOpened(TrackedApplication app) {
        Platform.runLater(() -> {
            dbManager.getLatestMemoForApp(app.getAppId()).ifPresent(memo -> {
                Alert prompt = new Alert(Alert.AlertType.CONFIRMATION);
                prompt.setTitle("View Reminder");
                prompt.setHeaderText("You have a reminder for " + app.getAppName());
                prompt.setContentText("Would you like to view it?");
                prompt.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        DialogHelper.showReminderDialog(memo);
                    }
                });
            });
        });
    }
}