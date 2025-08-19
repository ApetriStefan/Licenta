package org.stefanapetri.licenta.service;

import org.stefanapetri.licenta.MainApplication;
import java.util.prefs.Preferences;
import java.util.Arrays; // NEW IMPORT

public class SettingsManager {

    private final Preferences prefs;
    // Define keys to avoid typos
    private static final String LAUNCH_ON_STARTUP = "launchOnStartup";
    private static final String DISABLE_REMINDERS = "disableReminders";
    private static final String REMINDER_INTERVAL_HOURS = "reminderIntervalHours";
    // --- Gemini API Settings Keys ---
    private static final String ENABLE_GEMINI_PROCESSING = "enableGeminiProcessing";
    private static final String GEMINI_API_KEY = "geminiApiKey";

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

    // --- MODIFIED: Gemini API Settings to handle sensitive data ---
    public boolean isGeminiProcessingEnabled() {
        return prefs.getBoolean(ENABLE_GEMINI_PROCESSING, false); // Default to false
    }

    public void setEnableGeminiProcessing(boolean value) {
        prefs.putBoolean(ENABLE_GEMINI_PROCESSING, value);
    }

    /**
     * Retrieves the Gemini API key.
     * @return The API key as a String, or an empty string if not set.
     */
    public String getGeminiApiKey() {
        // While Preferences stores as String, this method is called to retrieve for use.
        // For truly high-security apps, one would encrypt/decrypt here.
        return prefs.get(GEMINI_API_KEY, "");
    }

    /**
     * Sets the Gemini API key.
     * @param key The API key as a String.
     */
    public void setGeminiApiKey(String key) {
        prefs.put(GEMINI_API_KEY, key);
        // It's good practice to flush preferences to ensure they're written to persistent storage
        try {
            prefs.flush();
        } catch (java.util.prefs.BackingStoreException e) {
            System.err.println("Error saving preferences: " + e.getMessage());
        }
    }
}