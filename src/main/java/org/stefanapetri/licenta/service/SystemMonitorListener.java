package org.stefanapetri.licenta.service;

import org.stefanapetri.licenta.model.TrackedApplication;

public interface SystemMonitorListener {
    /**
     * Called when a monitored application is detected to have been closed.
     * @param app The application that was closed.
     */
    void onMonitoredAppClosed(TrackedApplication app);

    /**
     * Called when a monitored application is detected to have gained focus.
     * @param app The application that was opened or focused.
     */
    void onMonitoredAppOpened(TrackedApplication app);
}