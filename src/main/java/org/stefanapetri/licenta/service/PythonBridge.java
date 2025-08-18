package org.stefanapetri.licenta.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets; // --- NEW: Import for UTF-8 constant ---
import java.util.concurrent.CompletableFuture;

public class PythonBridge {

    private final String pythonExecutable = "python";
    private final String scriptPath = new File("scripts/transcribe.py").getAbsolutePath();

    public CompletableFuture<String> transcribeAudio(String audioFilePath) {
        return CompletableFuture.supplyAsync(() -> {
            // Combine stdout and stderr so we can read error messages from one place
            ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, scriptPath, audioFilePath);
            processBuilder.redirectErrorStream(true);

            try {
                Process process = processBuilder.start();
                StringBuilder output = new StringBuilder();

                // --- MODIFIED: Explicitly read the stream as UTF-8 ---
                // This ensures that characters are interpreted correctly, regardless of the OS default.
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append(System.lineSeparator()); // Append new lines for readability
                    }
                }

                int exitCode = process.waitFor();
                // The output might now be multi-line, so trim it.
                String resultText = output.toString().trim();

                if (exitCode == 0) {
                    return resultText;
                } else {
                    System.err.println("Python script exited with error code: " + exitCode);
                    System.err.println("Full output: " + resultText);
                    // Return the captured error message from the script
                    return resultText;
                }

            } catch (Exception e) {
                e.printStackTrace();
                // Return a clear error message from the Java side
                return "Error: Java failed to execute the Python script. " + e.getMessage();
            }
        });
    }
}