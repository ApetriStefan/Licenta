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

import java.awt.*;
import java.io.IOException;
import java.net.URL;
import javafx.scene.image.Image;

public class MainApplication extends Application {

    private SystemMonitor systemMonitor;
    private Stage primaryStage;
    private TrayIcon trayIcon;

    // --- NEW: Static field to hold the application icon ---
    public static Image applicationIcon;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        // IMPORTANT: Prevent implicit exit when the last JavaFX window is closed.
        Platform.setImplicitExit(false);

        // --- NEW: Load the application icon once and store it ---
        URL iconUrl = MainApplication.class.getResource("app_icon.png");
        if (iconUrl != null) {
            applicationIcon = new Image(iconUrl.toExternalForm());
            // Set for the primary stage
            primaryStage.getIcons().add(applicationIcon);
        } else {
            System.err.println("Warning: Application icon 'app_icon.png' not found in resources. Cannot set window icons.");
            applicationIcon = null; // Ensure it's null if not found
        }
        // --- END NEW ---

        DatabaseManager dbManager = new DatabaseManager();
        systemMonitor = new SystemMonitor();
        PythonBridge pythonBridge = new PythonBridge();

        MainController mainController = new MainController(dbManager, systemMonitor, pythonBridge);

        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("MainWindow.fxml"));
        fxmlLoader.setControllerFactory(param -> mainController);

        Scene scene = new Scene(fxmlLoader.load(), 974, 591);
        primaryStage.setTitle("Application Activity Tracker");
        primaryStage.setScene(scene);

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

        // --- MODIFIED: Use the loaded applicationIcon directly ---
        java.awt.Image image = null;
        if (applicationIcon != null) {
            // Convert JavaFX Image to AWT Image for TrayIcon
            image = javafx.embed.swing.SwingFXUtils.fromFXImage(applicationIcon, null);
        } else {
            // Fallback if icon not found (should be handled by the warning above)
            URL fallbackUrl = MainApplication.class.getResource("app_icon.png");
            if (fallbackUrl != null) {
                image = new javax.swing.ImageIcon(fallbackUrl).getImage();
            } else {
                System.err.println("Error: Fallback icon not found for TrayIcon.");
                return; // Cannot create TrayIcon without an image
            }
        }
        // --- END MODIFIED ---


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