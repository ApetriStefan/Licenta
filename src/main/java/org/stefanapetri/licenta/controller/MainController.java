package org.stefanapetri.licenta.controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

import java.io.BufferedReader; // NEW IMPORT
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader; // NEW IMPORT
import java.net.URL;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.stream.Collectors; // NEW IMPORT

public class MainController implements Initializable, SystemMonitorListener {

    // --- FXML Fields for Settings Tab ---
    @FXML private CheckBox startupCheckBox;
    @FXML private CheckBox disableRemindersCheckBox;
    @FXML private ChoiceBox<ReminderInterval> reminderIntervalChoiceBox;
    // --- Gemini API Settings FXML Fields ---
    @FXML private CheckBox enableGeminiProcessingCheckBox;
    @FXML private PasswordField geminiApiKeyPasswordField;
    @FXML private Button saveGeminiApiKeyButton;
    // --- END NEW ---

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

    // --- FXML Fields for Developer Tab ---
    @FXML private TextField devAudioFilePathTextField;
    @FXML private Button devSelectAudioFileButton;
    @FXML private ChoiceBox<WhisperModel> devWhisperModelChoiceBox;
    @FXML private CheckBox devEnableGeminiProcessingCheckBox;
    @FXML private ChoiceBox<GeminiModel> devGeminiModelChoiceBox;
    @FXML private Button devGenerateTranscriptButton;
    @FXML private TextArea devPerformanceMetricsTextArea;
    @FXML private Button devAnalyzeMetricsButton; // NEW FXML FIELD
    // --- END DEVELOPER TAB FXML FIELDS ---


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

    // --- Constant for placeholder message ---
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

        searchQueryTextField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleSearch();
            }
        });

        loadApplicationsFromDB();
        updateButtonStates(false);
        setupSettingsTab();
        setupDeveloperTab(); // NEW: Setup the new tab
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

        // --- Initialize and bind Gemini API settings controls ---
        enableGeminiProcessingCheckBox.setSelected(settingsManager.isGeminiProcessingEnabled());
        // Load the key only when the checkbox is enabled, or to show previous state if any
        if (settingsManager.isGeminiProcessingEnabled()) {
            geminiApiKeyPasswordField.setText(settingsManager.getGeminiApiKey());
        } else {
            geminiApiKeyPasswordField.setText(""); // Clear if not enabled
        }
        geminiApiKeyPasswordField.disableProperty().bind(enableGeminiProcessingCheckBox.selectedProperty().not());
        saveGeminiApiKeyButton.disableProperty().bind(geminiApiKeyPasswordField.textProperty().isEmpty().or(enableGeminiProcessingCheckBox.selectedProperty().not()));


        enableGeminiProcessingCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            settingsManager.setEnableGeminiProcessing(newVal);
            // Clear API key in UI and settings if feature is disabled
            if (!newVal) {
                geminiApiKeyPasswordField.clear();
                settingsManager.setGeminiApiKey("");
            } else {
                // If re-enabled, load previously saved key if available
                geminiApiKeyPasswordField.setText(settingsManager.getGeminiApiKey());
            }
        });
    }

    private void setupDeveloperTab() {
        devWhisperModelChoiceBox.setItems(FXCollections.observableArrayList(WhisperModel.values()));
        devWhisperModelChoiceBox.setValue(settingsManager.getDeveloperWhisperModel()); // Load default

        devGeminiModelChoiceBox.setItems(FXCollections.observableArrayList(GeminiModel.values()));
        devGeminiModelChoiceBox.setValue(settingsManager.getDeveloperGeminiModel()); // Load default

        devEnableGeminiProcessingCheckBox.setSelected(settingsManager.isDeveloperGeminiProcessingEnabled());

        // Bind Gemini model choice box disable property
        devGeminiModelChoiceBox.disableProperty().bind(devEnableGeminiProcessingCheckBox.selectedProperty().not());

        // Save selected values to settings
        devWhisperModelChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) settingsManager.setDeveloperWhisperModel(newVal);
        });
        devGeminiModelChoiceBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) settingsManager.setDeveloperGeminiModel(newVal);
        });
        devEnableGeminiProcessingCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            settingsManager.setDeveloperEnableGeminiProcessing(newVal);
        });
    }

    // --- Handle Save Gemini API Key Button Action ---
    @FXML
    private void handleSaveGeminiApiKey() {
        String apiKey = geminiApiKeyPasswordField.getText();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            settingsManager.setGeminiApiKey(apiKey.trim());
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.INFORMATION, "API Key Saved",
                    "Gemini API Key saved successfully.", null
            );
        } else {
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.WARNING, "Empty API Key",
                    "Please enter a Gemini API Key before saving.", null
            );
        }
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
                // For regular recording, we use default Whisper/Gemini models
                transcribeAndSave(app, audioFilePath, WhisperModel.SMALL.getModelName(), settingsManager.isGeminiProcessingEnabled(), GeminiModel.GEMINI_2_5_FLASH.getModelName(), settingsManager.getGeminiApiKey());
            });
        } else {
            isRecording = false;
        }
    }

    private void transcribeAndSave(TrackedApplication app, String audioFilePath, String whisperModel, boolean enableGemini, String geminiModel, String geminiApiKey) {
        Stage transcribingDialog = DialogHelper.showTranscribingDialog();

        pythonBridge.transcribeAudio(audioFilePath, whisperModel, enableGemini, geminiModel, geminiApiKey).thenAccept(result -> {
            Platform.runLater(() -> {
                if (transcribingDialog != null) transcribingDialog.close();
            });

            if (result.transcription() != null && !result.transcription().startsWith("Error:")) {
                dbManager.saveMemo(app.getAppId(), result.transcription(), audioFilePath);
                Platform.runLater(() -> {
                    if (app.equals(appTableView.getSelectionModel().getSelectedItem())) {
                        loadMemoForApp(app);
                        loadHistoricalMemosForApp(app);
                    }
                    DialogHelper.showTranscriptionResultDialog(result.transcription(), audioFilePath, true);
                });
            } else {
                Platform.runLater(() -> DialogHelper.createTopMostAlert(
                        Alert.AlertType.ERROR, "Transcription Failed",
                        "The transcription process failed.", result.transcription()
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

    // --- Developer Tab Handlers ---
    @FXML
    private void handleDevSelectAudioFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Audio File for Transcription");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.flac"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selectedFile = fileChooser.showOpenDialog(devSelectAudioFileButton.getScene().getWindow());
        if (selectedFile != null) {
            devAudioFilePathTextField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleDevGenerateTranscript() {
        String audioFilePath = devAudioFilePathTextField.getText();
        if (audioFilePath == null || audioFilePath.trim().isEmpty() || !new File(audioFilePath).exists()) {
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.WARNING, "Missing Audio File",
                    "Please select an audio file before generating a transcript.", null
            );
            return;
        }

        WhisperModel selectedWhisperModel = devWhisperModelChoiceBox.getValue();
        boolean enableGemini = devEnableGeminiProcessingCheckBox.isSelected();
        GeminiModel selectedGeminiModel = devGeminiModelChoiceBox.getValue();
        String geminiApiKey = settingsManager.getGeminiApiKey(); // Use the same API key as settings

        if (enableGemini && (geminiApiKey == null || geminiApiKey.trim().isEmpty())) {
            DialogHelper.createTopMostAlert(
                    Alert.AlertType.WARNING, "Gemini API Key Missing",
                    "Gemini processing is enabled, but no API key is set in the Settings tab.", null
            );
            return;
        }

        // Clear previous results
        devPerformanceMetricsTextArea.clear();
        devPerformanceMetricsTextArea.appendText("Starting transcription...\n");
        devPerformanceMetricsTextArea.appendText("Using Whisper Model: " + selectedWhisperModel.getModelName() + "\n");
        devPerformanceMetricsTextArea.appendText("Gemini Processing: " + (enableGemini ? "Enabled with " + selectedGeminiModel.getModelName() : "Disabled") + "\n");
        devPerformanceMetricsTextArea.appendText("Audio File: " + audioFilePath + "\n");
        devPerformanceMetricsTextArea.appendText("----------------------------------\n");


        // UI feedback while processing
        devGenerateTranscriptButton.setDisable(true);
        devAnalyzeMetricsButton.setDisable(true);
        devSelectAudioFileButton.setDisable(true);
        devWhisperModelChoiceBox.setDisable(true);
        devEnableGeminiProcessingCheckBox.setDisable(true);

        // Get initial system CPU load
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double initialSystemLoadAverage = osBean.getSystemLoadAverage();
        String systemLoadAvgInitialReport = (initialSystemLoadAverage >= 0) ? String.format("%.2f (1 min avg)", initialSystemLoadAverage) : "N/A (not available on this system)";
        devPerformanceMetricsTextArea.appendText("Initial System Load Average: " + systemLoadAvgInitialReport + "\n");


        pythonBridge.transcribeAudio(
                audioFilePath,
                selectedWhisperModel.getModelName(),
                enableGemini,
                selectedGeminiModel.getModelName(),
                geminiApiKey
        ).thenAccept(result -> {
            Platform.runLater(() -> {
                double finalSystemLoadAverage = osBean.getSystemLoadAverage();
                String systemLoadAvgFinalReport = (finalSystemLoadAverage >= 0) ? String.format("%.2f (1 min avg)", finalSystemLoadAverage) : "N/A (not available on this system)";

                devPerformanceMetricsTextArea.appendText("\n--- Transcription Results (from Python) ---\n");
                devPerformanceMetricsTextArea.appendText("Transcription: " + result.transcription() + "\n");

                devPerformanceMetricsTextArea.appendText("\n--- Performance Metrics (from Python) ---\n");
                Map<String, Object> metrics = result.metrics();
                if (!metrics.isEmpty()) {
                    metrics.forEach((key, value) -> devPerformanceMetricsTextArea.appendText(String.format("%s: %s%n", key.replace("_", " "), value)));
                } else {
                    devPerformanceMetricsTextArea.appendText("No detailed Python-side metrics received.\n");
                }

                devPerformanceMetricsTextArea.appendText("\n--- System Metrics (from Java) ---\n");
                devPerformanceMetricsTextArea.appendText("Final System Load Average: " + systemLoadAvgFinalReport + "\n");


                // Save metrics to file
                String fileName = String.format("whisper-%s_gemini-%s.txt",
                        selectedWhisperModel.getModelName(),
                        enableGemini ? selectedGeminiModel.getModelName() : "disabled");
                File metricsDir = new File("metrics");
                if (!metricsDir.exists()) metricsDir.mkdirs();
                File outputFile = new File(metricsDir, fileName);

                try (FileWriter writer = new FileWriter(outputFile, true)) {
                    writer.write("--- Performance Test Run ---\n");
                    writer.write(String.format("Timestamp: %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                    writer.write(String.format("Audio File: %s%n", audioFilePath));
                    writer.write(String.format("Whisper Model: %s%n", selectedWhisperModel.getModelName()));
                    writer.write(String.format("Gemini Enabled: %b%n", enableGemini));
                    writer.write(String.format("Gemini Model: %s%n", enableGemini ? selectedGeminiModel.getModelName() : "N/A"));
                    writer.write("\n--- Python-side Metrics ---\n");
                    if (!metrics.isEmpty()) {
                        metrics.forEach((key, value) -> {
                            try {
                                writer.write(String.format("%s: %s%n", key.replace("_", " "), value));
                            } catch (IOException e) {
                                System.err.println("Error writing metric to file: " + e.getMessage());
                            }
                        });
                    } else {
                        writer.write("No detailed Python-side metrics received.\n");
                    }
                    writer.write("\n--- Java-side System Metrics ---\n");
                    writer.write("Initial System Load Average: " + systemLoadAvgInitialReport + "\n");
                    writer.write("Final System Load Average: " + systemLoadAvgFinalReport + "\n");
                    writer.write("Transcription Output:\n" + result.transcription() + "\n");
                    writer.write("----------------------------\n\n");
                    System.out.println("Performance metrics saved to " + outputFile.getAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Error writing performance metrics to file: " + e.getMessage());
                }


                // Re-enable UI elements
                devGenerateTranscriptButton.setDisable(false);
                devAnalyzeMetricsButton.setDisable(false);
                devSelectAudioFileButton.setDisable(false);
                devWhisperModelChoiceBox.setDisable(false);
                devEnableGeminiProcessingCheckBox.setDisable(false);
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                devPerformanceMetricsTextArea.appendText("\nError during transcription process: " + ex.getMessage() + "\n");
                ex.printStackTrace();
                // Re-enable UI elements on error
                devGenerateTranscriptButton.setDisable(false);
                devAnalyzeMetricsButton.setDisable(false);
                devSelectAudioFileButton.setDisable(false);
                devWhisperModelChoiceBox.setDisable(false);
                devEnableGeminiProcessingCheckBox.setDisable(false);
            });
            return null;
        });
    }

    @FXML
    private void handleDevAnalyzeMetrics() {
        devAnalyzeMetricsButton.setDisable(true);
        devAnalyzeMetricsButton.setText("Analyzing...");

        // Run the script in a background thread to avoid freezing the UI
        new Thread(() -> {
            try {
                String pythonExecutable = "python";
                String scriptPath = "metrics/metrics_analyzer.py";

                // Ensure the script exists before trying to run it
                File scriptFile = new File(scriptPath);
                if (!scriptFile.exists()) {
                    Platform.runLater(() -> {
                        DialogHelper.createTopMostAlert(
                                Alert.AlertType.ERROR,
                                "Script Not Found",
                                "The analysis script was not found.",
                                "Please ensure 'metrics_analyzer.py' is inside the 'metrics' directory, which is in the same folder as your application JAR."
                        );
                        devAnalyzeMetricsButton.setText("Analyze Metrics & Generate Graphs");
                        devAnalyzeMetricsButton.setDisable(false);
                    });
                    return;
                }

                ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptFile.getName());
                // Set the working directory for the script to the 'metrics' folder
                pb.directory(scriptFile.getParentFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();

                // Capture the script's output
                String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                        .lines().collect(Collectors.joining("\n"));

                int exitCode = process.waitFor();

                Platform.runLater(() -> {
                    if (exitCode == 0) {
                        DialogHelper.createTopMostAlert(
                                Alert.AlertType.INFORMATION,
                                "Analysis Complete",
                                "The performance metrics have been analyzed successfully.",
                                "Graphs have been saved to the 'performance_plots' directory."
                        );
                    } else {
                        DialogHelper.createTopMostAlert(
                                Alert.AlertType.ERROR,
                                "Analysis Failed",
                                "The analysis script failed to execute. See console for details.",
                                "Python script output:\n" + output
                        );
                        System.err.println("Python script failed with exit code " + exitCode + ". Output:\n" + output);
                    }
                    devAnalyzeMetricsButton.setText("Analyze Metrics & Generate Graphs");
                    devAnalyzeMetricsButton.setDisable(false);
                });

            } catch (IOException | InterruptedException e) {
                Platform.runLater(() -> {
                    DialogHelper.createTopMostAlert(
                            Alert.AlertType.ERROR,
                            "Execution Error",
                            "Could not run the Python analysis script.",
                            "Error: " + e.getMessage()
                    );
                    devAnalyzeMetricsButton.setText("Analyze Metrics & Generate Graphs");
                    devAnalyzeMetricsButton.setDisable(false);
                });
                e.printStackTrace();
            }
        }).start();
    }


    // --- SystemMonitorListener Methods ---
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
                boolean shouldShowPopup = false;

                if (intervalHours == ReminderInterval.ALWAYS.getHours()) { // -1
                    shouldShowPopup = true;
                } else if (intervalHours == ReminderInterval.AUTOMATIC.getHours()) { // -2
                    if (lastClosedOpt.isPresent()) {
                        // Ebbinghaus Forgetting Curve Calculation
                        final double memoryStrength = 34.63;
                        final double targetRetention = 0.25;
                        double requiredHoursForForgetting = -memoryStrength * Math.log(targetRetention);

                        long elapsedHours = Duration.between(lastClosedOpt.get().toInstant(), Instant.now()).toHours();

                        if (elapsedHours >= requiredHoursForForgetting) {
                            shouldShowPopup = true;
                        }
                    } else {
                        shouldShowPopup = true;
                    }
                } else if (intervalHours > 0) {
                    shouldShowPopup = lastClosedOpt.map(ts ->
                            Duration.between(ts.toInstant(), Instant.now()).toHours() >= intervalHours
                    ).orElse(true);
                }

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
    AUTOMATIC("Automatic", -2),
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