package org.stefanapetri.licenta;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.stefanapetri.licenta.controller.MainController;
import org.stefanapetri.licenta.model.DatabaseManager;
import org.stefanapetri.licenta.service.PythonBridge;
import org.stefanapetri.licenta.service.SystemMonitor;

import java.awt.*; // AWT classes for SystemTray
import java.io.IOException;
import java.net.URL; // For loading image resource
import javafx.scene.image.Image; // NEW IMPORT: For JavaFX Image

public class MainApplication extends Application {

    private SystemMonitor systemMonitor;
    private Stage primaryStage;
    private TrayIcon trayIcon;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        Platform.setImplicitExit(false);

        DatabaseManager dbManager = new DatabaseManager();
        systemMonitor = new SystemMonitor();
        PythonBridge pythonBridge = new PythonBridge();

        MainController mainController = new MainController(dbManager, systemMonitor, pythonBridge);

        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("MainWindow.fxml"));
        fxmlLoader.setControllerFactory(param -> mainController);

        Scene scene = new Scene(fxmlLoader.load(), 974, 591);
        primaryStage.setTitle("Application Activity Tracker");
        primaryStage.setScene(scene);

        // --- NEW CODE: Set the application icon ---
        URL iconUrl = MainApplication.class.getResource("app_icon.png"); // Path to your icon file
        if (iconUrl != null) {
            Image applicationIcon = new Image(iconUrl.toExternalForm());
            primaryStage.getIcons().add(applicationIcon);
        } else {
            System.err.println("Warning: Application icon 'app_icon.png' not found in resources. Cannot set window icon.");
        }
        // --- END NEW CODE ---

        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            hideStage();
        });

        createTrayIcon();

        primaryStage.show();

        systemMonitor.start();
    }

    private void createTrayIcon() {
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported on this platform.");
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        URL imageUrl = MainApplication.class.getResource("app_icon.png");
        if (imageUrl == null) {
            System.err.println("Error: Tray icon file 'app_icon.png' not found in resources.");
            return;
        }
        java.awt.Image image = new javax.swing.ImageIcon(imageUrl).getImage();

        PopupMenu popup = new PopupMenu();
        MenuItem showItem = new MenuItem("Show Window");
        showItem.addActionListener(e -> Platform.runLater(this::showStage));
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            if (systemMonitor != null) {
                systemMonitor.stop();
            }
            if (trayIcon != null) {
                tray.remove(trayIcon);
            }
            Platform.exit();
            System.exit(0);
        });

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        trayIcon = new TrayIcon(image, "Application Activity Tracker", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> Platform.runLater(this::showStage));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added: " + e.getMessage());
        }
    }

    private void showStage() {
        if (primaryStage != null) {
            primaryStage.show();
            primaryStage.toFront();
            primaryStage.requestFocus();
        }
    }

    private void hideStage() {
        if (primaryStage != null) {
            primaryStage.hide();
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}