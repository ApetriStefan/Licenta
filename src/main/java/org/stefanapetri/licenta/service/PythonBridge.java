package org.stefanapetri.licenta.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

public class PythonBridge {

    // Path to the Python executable. This might need to be configured by the user.
    // For now, we assume 'python' is in the system's PATH.
    private final String pythonExecutable = "python";

    // Path to your transcription script.
    // We assume the 'scripts' folder is in the root of the running application.
    private final String scriptPath = new File("scripts/transcribe.py").getAbsolutePath();

    public CompletableFuture<String> transcribeAudio(String audioFilePath) {
        return CompletableFuture.supplyAsync(() -> {
            ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, scriptPath, audioFilePath);
            processBuilder.redirectErrorStream(true); // Combine stdout and stderr

            try {
                Process process = processBuilder.start();
                StringBuilder output = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return output.toString();
                } else {
                    System.err.println("Python script exited with error code: " + exitCode);
                    System.err.println("Output: " + output);
                    // Return a descriptive error message
                    return "Error: Transcription failed. Check logs for details.";
                }

            } catch (Exception e) {
                e.printStackTrace();
                return "Error: Could not execute Python script.";
            }
        });
    }
}