package org.stefanapetri.licenta.service;

import org.stefanapetri.licenta.MainApplication;
import java.util.prefs.Preferences;

public class SettingsManager {

    private final Preferences prefs;
    // Define keys to avoid typos
    private static final String LAUNCH_ON_STARTUP = "launchOnStartup";
    private static final String DISABLE_REMINDERS = "disableReminders";
    private static final String REMINDER_INTERVAL_HOURS = "reminderIntervalHours";

    public SettingsManager() {
        // Creates a unique preference node for this application
        this.prefs = Preferences.userNodeForPackage(MainApplication.class);
    }

    // --- Launch on Startup ---
    public boolean isLaunchOnStartup() {
        return prefs.getBoolean(LAUNCH_ON_STARTUP, false); // Default to false
    }

    public void setLaunchOnStartup(boolean value) {
        prefs.putBoolean(LAUNCH_ON_STARTUP, value);
    }

    // --- Disable Reminders ---
    public boolean areRemindersDisabled() {
        return prefs.getBoolean(DISABLE_REMINDERS, false); // Default to false
    }

    public void setDisableReminders(boolean value) {
        prefs.putBoolean(DISABLE_REMINDERS, value);
    }

    // --- Reminder Interval ---
    // We store the interval in hours. -1 can represent "Always".
    public int getReminderIntervalHours() {
        return prefs.getInt(REMINDER_INTERVAL_HOURS, -1); // Default to -1 ("Always")
    }

    public void setReminderIntervalHours(int hours) {
        prefs.putInt(REMINDER_INTERVAL_HOURS, hours);
    }
}