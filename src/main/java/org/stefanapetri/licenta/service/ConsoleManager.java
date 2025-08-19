package org.stefanapetri.licenta.service;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConsoleManager {

    private final TextArea output;
    private final StringBuilder lineBuilder = new StringBuilder();
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ConsoleManager(TextArea ta) {
        this.output = ta;
    }

    private void appendText(String str) {
        // This custom stream ensures each new line from System.out gets a timestamp.
        for (char c : str.toCharArray()) {
            if (c == '\n') {
                if (!lineBuilder.isEmpty()) {
                    String timestamp = dtf.format(LocalDateTime.now());
                    output.appendText("[" + timestamp + "] " + lineBuilder + "\n");
                    lineBuilder.setLength(0); // Clear the builder for the next line
                }
            } else {
                lineBuilder.append(c);
            }
        }
    }

    /**
     * Creates a custom OutputStream that redirects its content to a JavaFX TextArea.
     */
    private static class ConsoleOutputStream extends OutputStream {
        private final ConsoleManager consoleManager;

        public ConsoleOutputStream(ConsoleManager consoleManager) {
            this.consoleManager = consoleManager;
        }

        @Override
        public void write(int b) throws IOException {
            // This is inefficient but necessary to redirect byte-by-byte streams.
            // We buffer it in the ConsoleManager.
            Platform.runLater(() -> consoleManager.appendText(String.valueOf((char) b)));
        }
    }


    /**
     * Redirects System.out and System.err to the provided TextArea.
     * @param consoleTextArea The TextArea to which the output streams will be redirected.
     */
    public static void redirectSystemStreams(TextArea consoleTextArea) {
        ConsoleManager manager = new ConsoleManager(consoleTextArea);
        OutputStream out = new ConsoleOutputStream(manager);

        // Redirect standard out and standard error
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));

        System.out.println("ConsoleManager initialized. System streams redirected.");
    }
}