package org.stefanapetri.licenta.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.stefanapetri.licenta.service.AudioRecorder;

import java.util.function.Consumer; // NEW IMPORT

public class RecordingController {

    @FXML private Label titleLabel;
    @FXML private Button stopButton;
    @FXML private Canvas waveformCanvas;

    private GraphicsContext gc;

    @FXML
    public void initialize() {
        gc = waveformCanvas.getGraphicsContext2D();
        // Initial clear to ensure a blank canvas
        gc.clearRect(0, 0, waveformCanvas.getWidth(), waveformCanvas.getHeight());
    }

    public void setAppName(String name) {
        titleLabel.setText("Recording Memo for " + name);
    }

    // --- NEW METHOD: Provides the Consumer for audio data ---
    public Consumer<byte[]> getAudioDataConsumer() {
        return this::drawWaveform;
    }

    private void drawWaveform(byte[] data) {
        Platform.runLater(() -> {
            double canvasWidth = waveformCanvas.getWidth();
            double canvasHeight = waveformCanvas.getHeight();
            double centerY = canvasHeight / 2;

            gc.clearRect(0, 0, canvasWidth, canvasHeight);
            gc.setStroke(Color.web("#007BFF"));
            gc.setLineWidth(1.5);
            gc.beginPath();
            gc.moveTo(0, centerY);

            // Assuming 16-bit mono PCM data
            for (int i = 0; i < data.length - 1; i += 2) { // Ensure we don't go out of bounds
                int low = data[i] & 0xFF; // Unsigned byte
                int high = data[i + 1] << 8;
                int sample = high | low; // 16-bit signed short range: -32768 to 32767

                double x = (double) i / data.length * canvasWidth;
                double y = centerY + ((double) sample / 32768.0) * centerY;

                gc.lineTo(x, y);
            }

            gc.stroke();
        });
    }

    @FXML
    private void handleStop() {
        Stage stage = (Stage) stopButton.getScene().getWindow();
        stage.close();
    }
}