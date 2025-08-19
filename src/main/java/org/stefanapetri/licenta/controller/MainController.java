package org.stefanapetri.licenta.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.stefanapetri.licenta.model.DatabaseManager;
import org.stefanapetri.licenta.model.Memo;
import org.stefanapetri.licenta.model.TrackedApplication;
import org.stefanapetri.licenta.service.*;
import org.stefanapetri.licenta.view.DialogHelper;
import org.stefanapetri.licenta.view.MarkdownConverter;
import org.stefanapetri.licenta.view.StageAndController;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.ResourceBundle;

public class MainController implements Initializable, SystemMonitorListener {

    // --- FXML Fields for Settings Tab ---
    @FXML private CheckBox startupCheckBox;
    @FXML private CheckBox disableRemindersCheckBox;
    @FXML private ChoiceBox<ReminderInterval> reminderIntervalChoiceBox;

    // --- FXML Fields for Main Tab ---
    @FXML private TableView<TrackedApplication> appTableView;
    @FXML private TableColumn<TrackedApplication, String> appNameColumn;
    @FXML private TableColumn<TrackedApplication, String> appPathColumn;
    @FXML private Button launchAppButton;
    @FXML private Button updateAppButton;
    @FXML private Button removeAppButton;
    @FXML private TextArea reminderTextArea;
    @FXML private WebView reminderWebView;
    @FXML private Button editOrSaveButton;
    @FXML private Button cancelEditButton;

    // --- Dependencies ---
    private final DatabaseManager dbManager;
    private final SystemMonitor systemMonitor;
    private final PythonBridge pythonBridge;
    private final AudioRecorder audioRecorder;
    private final SettingsManager settingsManager;
    private final StartupManager startupManager;

    // --- State ---
    private boolean isInEditMode = false;
    private final ObservableList<TrackedApplication> trackedAppsList = FXCollections.observableArrayList();
    private boolean isRecording = false;
    private Memo currentMemo = null;

    public MainController(DatabaseManager dbManager, SystemMonitor systemMonitor, PythonBridge pythonBridge) {
        this.dbManager = dbManager;
        this.systemMonitor = systemMonitor;
        this.pythonBridge = pythonBridge;
        this.audioRecorder = new AudioRecorder();
        this.settingsManager = new SettingsManager();
        this.startupManager = new StartupManager();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Main Tab Setup
        systemMonitor.setListener(this);
        appNameColumn.setCellValueFactory(new PropertyValueFactory<>("appName"));
        appPathColumn.setCellValueFactory(new PropertyValueFactory<>("executablePath"));
        appTableView.setItems(trackedAppsList);

        appTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    // When selection changes, always revert to view mode
                    toggleEditMode(false);
                    if (newSelection != null) {
                        loadMemoForApp(newSelection);
                        updateButtonStates(true);
                    } else {
                        updateButtonStates(false);
                        currentMemo = null;
                        reminderTextArea.clear();
                        reminderWebView.getEngine().loadContent(MarkdownConverter.toHtml(""));
                    }
                }
        );

        loadApplicationsFromDB();
        updateButtonStates(false);
        // Settings Tab Setup
        setupSettingsTab();
    }

    // --- HELPER METHODS ---

    private void setupSettingsTab() {
        reminderIntervalChoiceBox.setItems(FXCollections.observableArrayList(ReminderInterval.values()));
        startupCheckBox.setSelected(settingsManager.isLaunchOnStartup());
        disableRemindersCheckBox.setSelected(settingsManager.areRemindersDisabled());
        int savedIntervalHours = settingsManager.getReminderIntervalHours();
        ReminderInterval.fromHours(savedIntervalHours).ifPresent(reminderIntervalChoiceBox::setValue);

        startupCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            settingsManager.setLaunchOnStartup(newVal);
            if (newVal) startupManager.enableLaunchOnStartup(); else startupManager.disableLaunchOnStartup();
        });

        disableRemindersCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> settingsManager.setDisableReminders(newVal));

        reminderIntervalChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) settingsManager.setReminderIntervalHours(newVal.getHours());
        });
    }

    private void loadApplicationsFromDB() {
        trackedAppsList.setAll(dbManager.getAllTrackedApplications());
        systemMonitor.setTrackedApplications(trackedAppsList);
        appTableView.getSelectionModel().clearSelection();
    }

    private void loadMemoForApp(TrackedApplication app) {
        Optional<Memo> latestMemo = dbManager.getLatestMemoForApp(app.getAppId());
        this.currentMemo = latestMemo.orElse(null);
        String markdownText = latestMemo.map(Memo::transcriptionText).orElse("No reminder found for this application.");

        // Load content into both components
        reminderTextArea.setText(markdownText);
        reminderWebView.getEngine().loadContent(MarkdownConverter.toHtml(markdownText));
    }

    private void updateButtonStates(boolean itemSelected) {
        launchAppButton.setDisable(!itemSelected);
        updateAppButton.setDisable(!itemSelected);
        removeAppButton.setDisable(!itemSelected);
        // Also manage the edit button state
        editOrSaveButton.setDisable(!itemSelected || currentMemo == null);
    }

    private void toggleEditMode(boolean isEditing) {
        isInEditMode = isEditing;
        reminderTextArea.setVisible(isEditing);
        reminderWebView.setVisible(!isEditing);
        cancelEditButton.setVisible(isEditing);

        if (isEditing) {
            editOrSaveButton.setText("Save Changes");
            editOrSaveButton.setStyle("-fx-background-color: #28A745;"); // Green for save
        } else {
            editOrSaveButton.setText("Edit Reminder");
            editOrSaveButton.setStyle("-fx-background-color: #FFC107;"); // Yellow for edit
            editOrSaveButton.setDisable(appTableView.getSelectionModel().getSelectedItem() == null || currentMemo == null);
        }
    }

    private void startRecordingProcess(TrackedApplication app) {
        isRecording = true;
        String userTempDir = System.getProperty("java.io.tmpdir");
        String audioFilePath = new File(userTempDir, "temp_memo.wav").getAbsolutePath();
        StageAndController<RecordingController> sac = DialogHelper.showRecordingDialog(app, audioRecorder, audioFilePath);
        if (sac != null) {
            sac.stage.setOnHidden(e -> {
                audioRecorder.stopRecording();
                isRecording = false;
                transcribeAndSave(app, audioFilePath);
            });
        } else {
            isRecording = false;
        }
    }

    private void transcribeAndSave(TrackedApplication app, String audioFilePath) {
        Stage transcribingDialog = DialogHelper.showTranscribingDialog();

        pythonBridge.transcribeAudio(audioFilePath).thenAccept(transcription -> {
            Platform.runLater(() -> {
                if (transcribingDialog != null) transcribingDialog.close();
            });

            if (transcription != null && !transcription.startsWith("Error:")) {
                dbManager.saveMemo(app.getAppId(), transcription, audioFilePath);
                Platform.runLater(() -> {
                    if (app.equals(appTableView.getSelectionModel().getSelectedItem())) {
                        loadMemoForApp(app);
                    }
                    DialogHelper.showTranscriptionResultDialog(transcription, audioFilePath);
                });
            } else {
                Platform.runLater(() -> DialogHelper.createTopMostAlert(
                        Alert.AlertType.ERROR, "Transcription Failed",
                        "The transcription process failed.", transcription
                ));
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            Platform.runLater(() -> {
                if (transcribingDialog != null) transcribingDialog.close();
                DialogHelper.createTopMostAlert(
                        Alert.AlertType.ERROR, "Transcription Error",
                        "An unexpected error occurred during transcription.", ex.getMessage()
                );
            });
            return null;
        });
    }

    // --- FXML HANDLER METHODS ---

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
            Optional<ButtonType> result = DialogHelper.createTopMostAlert(
                    Alert.AlertType.CONFIRMATION, "Confirm Deletion",
                    "Remove '" + selectedApp.getAppName() + "'?",
                    "Are you sure? This will delete all associated reminders."
            );
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
                DialogHelper.createTopMostAlert(
                        Alert.AlertType.ERROR, "Launch Error",
                        "Failed to launch application.", e.getMessage()
                );
            }
        }
    }

    @FXML
    private void handleUpdateAppPath() {
        TrackedApplication selectedApp = appTableView.getSelectionModel().getSelectedItem();
        if (selectedApp == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select New Path for " + selectedApp.getAppName());
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executables", "*.exe"));
        File newFile = fileChooser.showOpenDialog(appTableView.getScene().getWindow());

        if (newFile != null) {
            String newPath = newFile.getAbsolutePath();
            Optional<ButtonType> result = DialogHelper.createTopMostAlert(
                    Alert.AlertType.CONFIRMATION, "Confirm Path Update",
                    "Change path for '" + selectedApp.getAppName() + "'?",
                    "Old Path: " + selectedApp.getExecutablePath() + "\nNew Path: " + newPath
            );
            if (result.isPresent() && result.get() == ButtonType.OK) {
                dbManager.updateApplicationPath(selectedApp.getAppId(), newPath);
                loadApplicationsFromDB();
                DialogHelper.createTopMostAlert(
                        Alert.AlertType.INFORMATION, "Success",
                        "Application path updated successfully.", null
                );
            }
        }
    }

    @FXML
    private void handleEditOrSaveReminder() {
        if (!isInEditMode) {
            toggleEditMode(true);
        } else {
            TrackedApplication selectedApp = appTableView.getSelectionModel().getSelectedItem();
            if (selectedApp == null || currentMemo == null) return;

            String updatedText = reminderTextArea.getText();
            int memoId = currentMemo.memoId();

            dbManager.updateMemoText(memoId, updatedText);
            loadMemoForApp(selectedApp);
            toggleEditMode(false);

            DialogHelper.createTopMostAlert(
                    Alert.AlertType.INFORMATION, "Success",
                    "Reminder updated successfully.", null
            );
        }
    }

    @FXML
    private void handleCancelEdit() {
        TrackedApplication selectedApp = appTableView.getSelectionModel().getSelectedItem();
        if (selectedApp != null) {
            loadMemoForApp(selectedApp);
        }
        toggleEditMode(false);
    }

    // --- SYSTEM MONITOR LISTENER METHODS ---

    @Override
    public void onMonitoredAppClosed(TrackedApplication app) {
        dbManager.updateLastClosedTimestamp(app.getAppId());
        if (isRecording) return;
        Platform.runLater(() -> {
            Optional<ButtonType> response = DialogHelper.createTopMostAlert(
                    Alert.AlertType.CONFIRMATION, "Record a Memo",
                    "You just closed " + app.getAppName(),
                    "Would you like to record a voice memo about what you were doing?"
            );
            if (response.isPresent() && response.get() == ButtonType.OK) {
                startRecordingProcess(app);
            }
        });
    }

    @Override
    public void onMonitoredAppOpened(TrackedApplication app) {
        if (settingsManager.areRemindersDisabled()) return;
        Platform.runLater(() -> {
            Optional<Memo> memoOpt = dbManager.getLatestMemoForApp(app.getAppId());
            Optional<Timestamp> lastClosedOpt = dbManager.getLastClosedTimestamp(app.getAppId());

            memoOpt.ifPresent(memo -> {
                int intervalHours = settingsManager.getReminderIntervalHours();
                boolean shouldShowPopup = (intervalHours == -1) || lastClosedOpt.map(ts ->
                        Duration.between(ts.toInstant(), Instant.now()).toHours() >= intervalHours
                ).orElse(true);

                if (shouldShowPopup) {
                    Optional<ButtonType> response = DialogHelper.createTopMostAlert(
                            Alert.AlertType.CONFIRMATION, "View Reminder",
                            "You have a reminder for " + app.getAppName(),
                            "Would you like to view it?"
                    );
                    if (response.isPresent() && response.get() == ButtonType.OK) {
                        DialogHelper.showReminderDialog(memo);
                    }
                }
            });
        });
    }
}

// Helper Enum for the ChoiceBox
enum ReminderInterval {
    ALWAYS("Always", -1),
    ONE_HOUR("After 1 Hour", 1),
    SIX_HOURS("After 6 Hours", 6),
    ONE_DAY("After 1 Day", 24),
    ONE_WEEK("After 1 Week", 168);

    private final String displayName;
    private final int hours;

    ReminderInterval(String displayName, int hours) {
        this.displayName = displayName;
        this.hours = hours;
    }

    public int getHours() {
        return hours;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static Optional<ReminderInterval> fromHours(int hours) {
        for (ReminderInterval interval : values()) {
            if (interval.hours == hours) {
                return Optional.of(interval);
            }
        }
        return Optional.empty();
    }
}