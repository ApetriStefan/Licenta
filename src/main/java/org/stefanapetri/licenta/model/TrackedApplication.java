package org.stefanapetri.licenta.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

// Using JavaFX properties allows the TableView to automatically update when data changes.
public class TrackedApplication {
    private final SimpleIntegerProperty appId;
    private final SimpleStringProperty appName;
    private final SimpleStringProperty executablePath;
    // You can add other properties like lastOpened if needed for the table.
    // For now, we'll keep it simple.

    public TrackedApplication(int appId, String appName, String executablePath) {
        this.appId = new SimpleIntegerProperty(appId);
        this.appName = new SimpleStringProperty(appName);
        this.executablePath = new SimpleStringProperty(executablePath);
    }

    // Getters for the properties
    public int getAppId() { return appId.get(); }
    public SimpleIntegerProperty appIdProperty() { return appId; }
    public String getAppName() { return appName.get(); }
    public SimpleStringProperty appNameProperty() { return appName; }
    public String getExecutablePath() { return executablePath.get(); }
    public SimpleStringProperty executablePathProperty() { return executablePath; }

    @Override
    public String toString() {
        // Useful for debugging
        return getAppName();
    }
}