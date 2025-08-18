package org.stefanapetri.licenta.service;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.io.File;

public class StartupManager {

    private static final String APP_NAME = "AppActivityTracker";
    private static final String REGISTRY_KEY_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    // This method is specific to Windows
    public void enableLaunchOnStartup() {
        try {
            String jarPath = new File(StartupManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
            // We need to launch the JAR using 'javaw.exe -jar ...' for it to run correctly
            String command = String.format("javaw.exe -jar \"%s\"", jarPath);
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY_PATH, APP_NAME, command);
            System.out.println("Enabled launch on startup.");
        } catch (Exception e) {
            System.err.println("Could not enable launch on startup: " + e.getMessage());
        }
    }

    // This method is specific to Windows
    public void disableLaunchOnStartup() {
        try {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, REGISTRY_KEY_PATH, APP_NAME);
            System.out.println("Disabled launch on startup.");
        } catch (Exception e) {
            System.err.println("Could not disable launch on startup (this is normal if it wasn't enabled): " + e.getMessage());
        }
    }
}