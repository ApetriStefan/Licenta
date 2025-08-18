package org.stefanapetri.licenta;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.stefanapetri.licenta.controller.MainController;
import org.stefanapetri.licenta.model.DatabaseManager;
import org.stefanapetri.licenta.service.PythonBridge;
import org.stefanapetri.licenta.service.SystemMonitor;

import java.io.IOException;

public class MainApplication extends Application {

    private SystemMonitor systemMonitor;

    @Override
    public void start(Stage stage) throws IOException {
        // 1. Initialize core services (the "backend")
        DatabaseManager dbManager = new DatabaseManager();
        systemMonitor = new SystemMonitor();
        PythonBridge pythonBridge = new PythonBridge();

        // 2. Initialize the Controller and inject the services (Dependency Injection)
        MainController mainController = new MainController(dbManager, systemMonitor, pythonBridge);

        // 3. Load the FXML view
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("MainWindow.fxml"));

        // 4. Set the controller factory so the FXML loader uses our instance
        // This is how we pass our services to the controller.
        fxmlLoader.setControllerFactory(param -> mainController);

        // 5. Create the scene and show the stage
        Scene scene = new Scene(fxmlLoader.load(), 974, 591);
        stage.setTitle("Application Activity Tracker");
        stage.setScene(scene);
        stage.show();

        // 6. Start the background monitor AFTER the UI is visible
        systemMonitor.start();
    }

    @Override
    public void stop() throws Exception {
        // Gracefully stop the background thread when the application closes
        if (systemMonitor != null) {
            systemMonitor.stop();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}