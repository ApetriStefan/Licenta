// src\main\java\org\stefanapetri\licenta\controller\MainController.java
package org.stefanapetri.licenta.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.stefanapetri.licenta.MainApplication;
import org.stefanapetri.licenta.model.DatabaseManager;
import org.stefanapetri.licenta.model.MemoViewItem;
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
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
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
    @FXML private TextArea consoleTextArea;

    // FXML fields for historical reminders
    @FXML private TableView<MemoViewItem> historicalMemosTableView;
    @FXML private TableColumn<MemoViewItem, String> historyDateColumn;
    @FXML private TableColumn<MemoViewItem, String> historyPreviewColumn;
    @FXML private Button viewHistoricalMemoButton;
    @FXML private Button deleteHistoricalMemoButton;

    // --- FXML fields for Search Tab ---
    @FXML private TextField searchQueryTextField;
    @FXML private Button searchButton;
    @FXML private TableView<MemoViewItem> searchResultsTableView;
    @FXML private TableColumn<MemoViewItem, String> searchAppColumn;
    @FXML private TableColumn<MemoViewItem, String> searchDateColumn;
    @FXML private TableColumn<MemoViewItem, String> searchPreviewColumn;
    @FXML private Button viewSearchMemoButton;
    @FXML private Button deleteSearchMemoButton;


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
    private final ObservableList<MemoViewItem> historicalMemosList = FXCollections.observableArrayList();
    private final ObservableList<MemoViewItem> searchResultsList = FXCollections.observableArrayList();
    private boolean isRecording = false;
    private MemoViewItem currentMemo = null;

    // --- NEW: Constant for placeholder message ---
    private static final String NO_APP_SELECTED_MESSAGE = "### No Application Selected\n\nSelect an application from the list to view its reminders.";


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
        ConsoleManager.redirectSystemStreams(consoleTextArea);

        // --- NEW: Set initial dark mode content for WebView ---
        reminderWebView.getEngine().loadContent(MarkdownConverter.toHtml(NO_APP_SELECTED_MESSAGE));

        systemMonitor.setListener(this);
        appNameColumn.setCellValueFactory(new PropertyValueFactory<>("appName"));
        appPathColumn.setCellValueFactory(new PropertyValueFactory<>("executablePath"));
        appTableView.setItems(trackedAppsList);

        historyDateColumn.setCellValueFactory(cellData -> {
            Timestamp timestamp = cellData.getValue().createdAt();
            DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
            return new ReadOnlyStringWrapper(timestamp.toLocalDateTime().format(formatter));
        });
        historyPreviewColumn.setCellValueFactory(cellData -> {
            String fullText = cellData.getValue().transcriptionText();
            String preview = fullText.length() > 50 ? fullText.substring(0, 50) + "..." : fullText;
            return new ReadOnlyStringWrapper(preview.replaceAll("\n", " "));
        });
        historicalMemosTableView.setItems(historicalMemosList);

        appTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    toggleEditMode(false);
                    if (newSelection != null) {
                        loadMemoForApp(newSelection);
                        loadHistoricalMemosForApp(newSelection);
                        updateButtonStates(true);
                    } else {
                        updateButtonStates(false);
                        currentMemo = null;
                        reminderTextArea.clear();
                        // --- MODIFIED: Use consistent placeholder when selection is cleared ---
                        reminderWebView.getEngine().loadContent(MarkdownConverter.toHtml(NO_APP_SELECTED_MESSAGE));
                        historicalMemosList.clear();
                    }
                }
        );

        searchAppColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().appName()));
        searchDateColumn.setCellValueFactory(cellData -> {
            Timestamp timestamp = cellData.getValue().createdAt();
            DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
            return new ReadOnlyStringWrapper(timestamp.toLocalDateTime().format(formatter));
        });
        searchPreviewColumn.setCellValueFactory(cellData -> {
            String fullText = cellData.getValue().transcriptionText();
            String preview = fullText.length() > 100 ? fullText.substring(0, 100) + "..." : fullText;
            return new ReadOnlyStringWrapper(preview.replaceAll("\n", " "));
        });
        searchResultsTableView.setItems(searchResultsList);

        searchResultsTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    updateSearchButtonStates(newSelection != null);
                }
        );
        updateSearchButtonStates(false);

        loadApplicationsFromDB();
        updateButtonStates(false);
        setupSettingsTab();
    }

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
        Optional<MemoViewItem> latestMemo = dbManager.getLatestMemoForApp(app.getAppId());
        this.currentMemo = latestMemo.orElse(null);
        String markdownText = latestMemo.map(MemoViewItem::transcriptionText).orElse("### No Reminder Found\n\nNo reminder has been recorded for this application yet.");

        reminderTextArea.setText(markdownText);
        reminderWebView.getEngine().loadContent(MarkdownConverter.toHtml(markdownText));
    }

    private void loadHistoricalMemosForApp(TrackedApplication app) {
        historicalMemosList.setAll(dbManager.getAllMemosForApp(app.getAppId()));
        updateHistoricalButtonStates(false);
        historicalMemosTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    updateHistoricalButtonStates(newSelection != null);
                }
        );
    }

    private void updateButtonStates(boolean itemSelected) {
        launchAppButton.setDisable(!itemSelected);
        updateAppButton.setDisable(!itemSelected);
        removeAppButton.setDisable(!itemSelected);
        editOrSaveButton.setDisable(!itemSelected || currentMemo == null);

        updateHistoricalButtonStates(false);
    }

    private void updateHistoricalButtonStates(boolean historicalMemoSelected) {
        viewHistoricalMemoButton.setDisable(!historicalMemoSelected);
        deleteHistoricalMemoButton.setDisable(!historicalMemoSelected);
    }

    private void updateSearchButtonStates(boolean searchResultSelected) {
        viewSearchMemoButton.setDisable(!searchResultSelected);
        deleteSearchMemoButton.setDisable(!searchResultSelected);
    }

    private void toggleEditMode(boolean isEditing) {
        isInEditMode = isEditing;
        reminderTextArea.setVisible(isEditing);
        reminderWebView.setVisible(!isEditing);
        cancelEditButton.setVisible(isEditing);

        editOrSaveButton.getStyleClass().removeAll("warning-button", "success-button");
        if (isEditing) {
            editOrSaveButton.setText("Save Changes");
            editOrSaveButton.getStyleClass().add("success-button");
        } else {
            editOrSaveButton.setText("Edit Reminder");
            editOrSaveButton.getStyleClass().add("warning-button");
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
                        loadHistoricalMemosForApp(app);
                    }
                    DialogHelper.showTranscriptionResultDialog(transcription, audioFilePath, true);
                });
            } else {
                Platform.runLater(() -> DialogHelper.createTopMostAlert(
                        Alert.AlertType.ERROR, "Transcription Failed",
                        "The transcription process failed.", transcription
                ));
            }
        }).exceptionally(ex -> {
            System.err.println("Exception in transcription future: " + ex.getMessage());
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

            // --- Apply dark theme to the dialog ---
            DialogPane dialogPane = dialog.getDialogPane();
            String css = MainApplication.class.getResource("style.css").toExternalForm();
            dialogPane.getStylesheets().add(css);
            dialogPane.getStyleClass().add("root");
            // --- END ---

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
                    "Are you sure? This will delete the application and all of its reminders."
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
                System.out.println("Launching application: " + selectedApp.getExecutablePath());
                new ProcessBuilder(selectedApp.getExecutablePath()).start();
            } catch (Exception e) {
                System.err.println("Failed to launch application: " + e.getMessage());
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
            loadHistoricalMemosForApp(selectedApp);

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

    @FXML
    private void handleViewHistoricalMemo() {
        MemoViewItem selectedMemo = historicalMemosTableView.getSelectionModel().getSelectedItem();
        if (selectedMemo != null) {
            DialogHelper.showTranscriptionResultDialog(selectedMemo.transcriptionText(), selectedMemo.audioFilePath(), false);
        } else {
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.WARNING, "No Memo Selected",
                    "Please select a historical memo to view.", null
            );
        }
    }

    @FXML
    private void handleDeleteHistoricalMemo() {
        MemoViewItem selectedMemo = historicalMemosTableView.getSelectionModel().getSelectedItem();
        if (selectedMemo != null) {
            Optional<ButtonType> result = DialogHelper.createTopMostAlert(
                    Alert.AlertType.CONFIRMATION, "Confirm Deletion",
                    "Delete selected historical memo?",
                    "Are you sure you want to delete this memo? This action cannot be undone."
            );
            if (result.isPresent() && result.get() == ButtonType.OK) {
                dbManager.deleteMemo(selectedMemo.memoId());
                TrackedApplication currentApp = appTableView.getSelectionModel().getSelectedItem();
                if (currentApp != null) {
                    loadMemoForApp(currentApp);
                    loadHistoricalMemosForApp(currentApp);
                } else {
                    historicalMemosList.clear();
                }
                DialogHelper.createTopMostAlert(
                        Alert.AlertType.INFORMATION, "Deleted",
                        "Memo deleted successfully.", null
                );
            }
        } else {
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.WARNING, "No Memo Selected",
                    "Please select a historical memo to delete.", null
            );
        }
    }

    @FXML
    private void handleSearch() {
        String query = searchQueryTextField.getText();
        if (query == null || query.trim().isEmpty()) {
            searchResultsList.clear();
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.INFORMATION, "Empty Search",
                    "Please enter a search query.", null
            );
            return;
        }
        List<MemoViewItem> results = dbManager.searchMemos(query.trim());
        searchResultsList.setAll(results);
        updateSearchButtonStates(false);

        if (results.isEmpty()) {
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.INFORMATION, "No Results",
                    "No memos found matching your search query.", null
            );
        }
    }

    @FXML
    private void handleViewSearchMemo() {
        MemoViewItem selectedMemo = searchResultsTableView.getSelectionModel().getSelectedItem();
        if (selectedMemo != null) {
            DialogHelper.showTranscriptionResultDialog(selectedMemo.transcriptionText(), selectedMemo.audioFilePath(), false);
        } else {
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.WARNING, "No Memo Selected",
                    "Please select a memo from the search results to view.", null
            );
        }
    }

    @FXML
    private void handleDeleteSearchMemo() {
        MemoViewItem selectedMemo = searchResultsTableView.getSelectionModel().getSelectedItem();
        if (selectedMemo != null) {
            Optional<ButtonType> result = DialogHelper.createTopMostAlert(
                    Alert.AlertType.CONFIRMATION, "Confirm Deletion",
                    "Delete selected search result memo?",
                    "Are you sure you want to delete this memo? This action cannot be undone."
            );
            if (result.isPresent() && result.get() == ButtonType.OK) {
                dbManager.deleteMemo(selectedMemo.memoId());
                handleSearch();

                TrackedApplication currentApp = appTableView.getSelectionModel().getSelectedItem();
                if (currentApp != null && currentApp.getAppId() == selectedMemo.appId()) {
                    loadMemoForApp(currentApp);
                    loadHistoricalMemosForApp(currentApp);
                }
                DialogHelper.createTopMostAlert(
                        Alert.AlertType.INFORMATION, "Deleted",
                        "Memo deleted successfully.", null
                );
            }
        } else {
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.WARNING, "No Memo Selected",
                    "Please select a memo from the search results to delete.", null
            );
        }
    }


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
            Optional<MemoViewItem> memoOpt = dbManager.getLatestMemoForApp(app.getAppId());
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