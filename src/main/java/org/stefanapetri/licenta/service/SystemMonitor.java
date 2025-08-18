package org.stefanapetri.licenta.service;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import javafx.application.Platform;
import org.stefanapetri.licenta.model.TrackedApplication;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SystemMonitor implements Runnable {

    private final Map<String, TrackedApplication> trackedAppMap = new ConcurrentHashMap<>();
    private final Map<Integer, TrackedApplication> runningTrackedProcesses = new ConcurrentHashMap<>();
    private String lastOpenedAppPath = ""; // Path of the app for which we last showed an "open" dialog

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private SystemMonitorListener listener;

    public void setListener(SystemMonitorListener listener) {
        this.listener = listener;
    }

    public void start() {
        isRunning.set(true);
        Thread monitorThread = new Thread(this, "SystemMonitorThread");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public void stop() {
        isRunning.set(false);
    }

    public void setTrackedApplications(Collection<TrackedApplication> apps) {
        trackedAppMap.clear();
        for (TrackedApplication app : apps) {
            trackedAppMap.put(app.getExecutablePath().toLowerCase(), app);
        }
        runningTrackedProcesses.clear();
    }

    @Override
    public void run() {
        while (isRunning.get()) {
            try {
                // --- Part 1: Check the currently focused window ---
                HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
                if (foregroundWindow != null) {
                    IntByReference processIdRef = new IntByReference();
                    User32.INSTANCE.GetWindowThreadProcessId(foregroundWindow, processIdRef);
                    int currentPid = processIdRef.getValue();
                    String currentPath = getProcessPath(currentPid);

                    if (currentPath != null && !currentPath.isEmpty()) {
                        currentPath = currentPath.toLowerCase();

                        if (trackedAppMap.containsKey(currentPath)) {
                            TrackedApplication currentApp = trackedAppMap.get(currentPath);
                            runningTrackedProcesses.putIfAbsent(currentPid, currentApp);

                            // --- MODIFIED "OPEN" LOGIC ---
                            // Only fire the event if the focused app is different from the one we last remembered.
                            // This prevents our own pop-ups from causing the event to fire repeatedly.
                            if (!currentPath.equals(lastOpenedAppPath)) {
                                if (listener != null) {
                                    Platform.runLater(() -> listener.onMonitoredAppOpened(currentApp));
                                }
                                lastOpenedAppPath = currentPath; // Remember this path
                            }
                        }
                        // We NO LONGER reset lastOpenedAppPath here.
                    }
                }

                // --- Part 2: Reliably check for closed applications ---
                for (Integer pid : new HashSet<>(runningTrackedProcesses.keySet())) {
                    if (!isProcessRunning(pid)) {
                        TrackedApplication closedApp = runningTrackedProcesses.remove(pid);
                        if (closedApp != null) {
                            // --- NEW "CLOSE" LOGIC ---
                            // If the app that just closed is the one we were remembering, we can now forget it.
                            // This allows the "open" pop-up to appear again if the user re-launches it.
                            if (closedApp.getExecutablePath().equalsIgnoreCase(lastOpenedAppPath)) {
                                lastOpenedAppPath = "";
                            }

                            if (listener != null) {
                                Platform.runLater(() -> listener.onMonitoredAppClosed(closedApp));
                            }
                        }
                    }
                }

                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isRunning.set(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // (getProcessPath and isProcessRunning methods are unchanged)
    private String getProcessPath(int processId) {
        Memory buffer = new Memory(2048);
        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(
                Kernel32.PROCESS_QUERY_INFORMATION | Kernel32.PROCESS_VM_READ,
                false, processId);
        if (processHandle != null) {
            try {
                Psapi.INSTANCE.GetModuleFileNameEx(processHandle, null, buffer, 1024);
                return buffer.getWideString(0);
            } finally {
                Kernel32.INSTANCE.CloseHandle(processHandle);
            }
        }
        return null;
    }

    private boolean isProcessRunning(int processId) {
        if (processId == 0) return false;
        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(Kernel32.PROCESS_QUERY_INFORMATION, false, processId);
        if (processHandle == null) return false;
        try {
            IntByReference exitCode = new IntByReference();
            boolean result = Kernel32.INSTANCE.GetExitCodeProcess(processHandle, exitCode);
            return result && exitCode.getValue() == Kernel32.STILL_ACTIVE;
        } finally {
            Kernel32.INSTANCE.CloseHandle(processHandle);
        }
    }
}