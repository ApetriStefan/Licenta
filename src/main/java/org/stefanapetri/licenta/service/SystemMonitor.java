package org.stefanapetri.licenta.service;

import com.sun.jna.Memory; // <-- IMPORTANT: Add this new import
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import javafx.application.Platform;
import org.stefanapetri.licenta.model.TrackedApplication;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SystemMonitor implements Runnable {

    // (All other fields and methods remain the same as the previous version)
    private final Map<String, TrackedApplication> trackedAppMap = new ConcurrentHashMap<>();
    private String lastFocusedAppPath = "";
    private int lastFocusedProcessId = 0;

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
    }

    @Override
    public void run() {
        while (isRunning.get()) {
            try {
                HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
                if (foregroundWindow == null) {
                    Thread.sleep(2000);
                    continue;
                }

                IntByReference processIdRef = new IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(foregroundWindow, processIdRef);
                int currentProcessId = processIdRef.getValue();

                if (currentProcessId != lastFocusedProcessId) {
                    String currentFocusedAppPath = getProcessPath(currentProcessId);

                    if (currentFocusedAppPath != null && !currentFocusedAppPath.isEmpty()) {
                        currentFocusedAppPath = currentFocusedAppPath.toLowerCase();

                        if (trackedAppMap.containsKey(currentFocusedAppPath)) {
                            TrackedApplication openedApp = trackedAppMap.get(currentFocusedAppPath);
                            if (listener != null) {
                                Platform.runLater(() -> listener.onMonitoredAppOpened(openedApp));
                            }
                        }

                        if (lastFocusedAppPath != null && trackedAppMap.containsKey(lastFocusedAppPath)) {
                            if (!isProcessRunning(lastFocusedProcessId)) {
                                TrackedApplication closedApp = trackedAppMap.get(lastFocusedAppPath);
                                if (listener != null) {
                                    Platform.runLater(() -> listener.onMonitoredAppClosed(closedApp));
                                }
                            }
                        }
                        lastFocusedAppPath = currentFocusedAppPath;
                    }
                }
                lastFocusedProcessId = currentProcessId;

                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isRunning.set(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // --- THIS IS THE CORRECTED METHOD ---
    private String getProcessPath(int processId) {
        // Allocate a block of native memory for the path buffer using Memory.
        Memory buffer = new Memory(2048); // 2048 bytes should be sufficient for any path.

        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(
                Kernel32.PROCESS_QUERY_INFORMATION | Kernel32.PROCESS_VM_READ,
                false,
                processId
        );

        if (processHandle != null) {
            try {
                // Pass the Memory object (which is a Pointer) to the native function.
                // The last argument is the size of the buffer in TCHARs (wide characters), not bytes.
                Psapi.INSTANCE.GetModuleFileNameEx(processHandle, null, buffer, 1024);

                // Read the Unicode (wide) string from the native memory block.
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
        if (processHandle == null) {
            return false;
        }

        try {
            IntByReference exitCode = new IntByReference();
            boolean result = Kernel32.INSTANCE.GetExitCodeProcess(processHandle, exitCode);
            return result && exitCode.getValue() == Kernel32.STILL_ACTIVE;
        } finally {
            Kernel32.INSTANCE.CloseHandle(processHandle);
        }
    }
}