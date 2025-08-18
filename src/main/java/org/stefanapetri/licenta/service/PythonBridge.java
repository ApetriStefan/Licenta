package org.stefanapetri.licenta.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PythonBridge {

    private final String pythonExecutable = "python";
    private final String scriptPath = new File("scripts/transcribe.py").getAbsolutePath();

    public CompletableFuture<String> transcribeAudio(String audioFilePath) {
        return CompletableFuture.supplyAsync(() -> {
            ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, scriptPath, audioFilePath);

            // --- THIS IS THE KEY CHANGE ---
            // We NO LONGER redirect the error stream. This keeps stdout and stderr separate.
            // processBuilder.redirectErrorStream(true); // <-- REMOVED

            try {
                Process process = processBuilder.start();

                // Read the standard output (this is our clean transcription)
                String output;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    output = reader.lines().collect(Collectors.joining(System.lineSeparator()));
                }

                // Read the standard error (for logging and debugging)
                String errorOutput;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    errorOutput = reader.lines().collect(Collectors.joining(System.lineSeparator()));
                }

                int exitCode = process.waitFor();

                // If there was anything in the error stream, print it to the Java console for debugging.
                if (errorOutput != null && !errorOutput.isEmpty()) {
                    System.err.println("Python Script stderr:\n" + errorOutput);
                }

                if (exitCode == 0) {
                    return output;
                } else {
                    return "Error: Transcription failed. Script exited with code " + exitCode + ".";
                }

            } catch (Exception e) {
                e.printStackTrace();
                return "Error: Could not execute Python script.";
            }
        });
    }
}