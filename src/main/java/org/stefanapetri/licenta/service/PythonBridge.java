package org.stefanapetri.licenta.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PythonBridge {

    private final String pythonExecutable = "python";
    // scriptPath is now just the name, not a full path.
    private final String scriptName = "transcribe.py";

    public CompletableFuture<String> transcribeAudio(String audioFilePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Extract the script from resources to a temporary file
                File tempScript = extractScriptFromResources(scriptName);
                String scriptPath = tempScript.getAbsolutePath();

                ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, scriptPath, audioFilePath);

                // --- THIS IS THE KEY CHANGE ---
                // We NO LONGER redirect the error stream. This keeps stdout and stderr separate.
                // processBuilder.redirectErrorStream(true); // <-- REMOVED

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
                tempScript.delete(); // Clean up the temporary file

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

    private File extractScriptFromResources(String scriptName) throws IOException {
        // The path within resources should match your source folder structure
        String resourcePath = "/org/stefanapetri/licenta/scripts/" + scriptName;
        try (InputStream in = PythonBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Script not found in resources: " + resourcePath);
            }
            File tempFile = Files.createTempFile("script-", ".py").toFile();
            try (OutputStream out = Files.newOutputStream(tempFile.toPath())) {
                in.transferTo(out);
            }
            return tempFile;
        }
    }
}