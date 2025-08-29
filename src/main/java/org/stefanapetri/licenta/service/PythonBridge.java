// src\main\main\java\org\stefanapetri\licenta\service\PythonBridge.java
package org.stefanapetri.licenta.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper; // NEW IMPORT
import com.fasterxml.jackson.core.type.TypeReference; // NEW IMPORT
import java.util.Map; // NEW IMPORT

public class PythonBridge {

    private final String pythonExecutable = "python";
    private final String scriptName = "transcribe.py";
    private static final String METRICS_DELIMITER = "---METRICS_JSON_START---"; // Unique delimiter


    public record TranscriptionResult(String transcription, Map<String, Object> metrics) {}

    public CompletableFuture<TranscriptionResult> transcribeAudio(String audioFilePath, String whisperModel, boolean enableGemini, String geminiModel, String geminiApiKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File tempScript = extractScriptFromResources(scriptName);
                String scriptPath = tempScript.getAbsolutePath();

                List<String> command = new ArrayList<>();
                command.add(pythonExecutable);
                command.add(scriptPath);
                command.add(audioFilePath);
                command.add("--whisper-model=" + whisperModel);
                command.add("--enable-gemini=" + enableGemini);
                command.add("--gemini-model=" + geminiModel);
                command.add("--gemini-api-key=" + geminiApiKey);

                ProcessBuilder processBuilder = new ProcessBuilder(command);

                Process process = processBuilder.start();

                StringBuilder outputBuilder = new StringBuilder();
                String line;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    while ((line = reader.readLine()) != null) {
                        outputBuilder.append(line).append(System.lineSeparator());
                    }
                }
                String rawOutput = outputBuilder.toString();

                // Read the standard error (for logging and debugging)
                String errorOutput;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    errorOutput = reader.lines().collect(Collectors.joining(System.lineSeparator()));
                }

                int exitCode = process.waitFor();
                tempScript.delete(); // Clean up the temporary file

                if (errorOutput != null && !errorOutput.isEmpty()) {
                    System.err.println("Python Script stderr:\n" + errorOutput);
                }

                if (exitCode == 0) {
                    // Parse transcription and metrics
                    int delimiterIndex = rawOutput.indexOf(METRICS_DELIMITER);
                    String transcription = rawOutput;
                    Map<String, Object> metrics = Map.of(); // Default empty map

                    if (delimiterIndex != -1) {
                        transcription = rawOutput.substring(0, delimiterIndex).trim();
                        String jsonMetrics = rawOutput.substring(delimiterIndex + METRICS_DELIMITER.length()).trim();
                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            metrics = mapper.readValue(jsonMetrics, new TypeReference<Map<String, Object>>() {});
                        } catch (IOException e) {
                            System.err.println("Error parsing Python metrics JSON: " + e.getMessage());
                        }
                    }

                    return new TranscriptionResult(transcription, metrics);

                } else {
                    return new TranscriptionResult("Error: Transcription failed. Script exited with code " + exitCode + ".\nRaw output:\n" + rawOutput, Map.of("error", "Python script failed"));
                }

            } catch (Exception e) {
                e.printStackTrace();
                return new TranscriptionResult("Error: Could not execute Python script. Details: " + e.getMessage(), Map.of("error", e.getMessage()));
            }
        });
    }

    private File extractScriptFromResources(String scriptName) throws IOException {
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