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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;

public class RecordingController {

    @FXML private Label titleLabel;
    @FXML private Button stopButton;
    @FXML private Canvas waveformCanvas; // The new Canvas from FXML

    private GraphicsContext gc;
    // A deque (double-ended queue) to store the most recent audio samples for drawing
    private final ArrayDeque<Short> audioBuffer = new ArrayDeque<>();
    private int maxBufferSize;

    @FXML
    public void initialize() {
        // Prepare the canvas for drawing
        gc = waveformCanvas.getGraphicsContext2D();
        maxBufferSize = (int) waveformCanvas.getWidth();
        drawSilence(); // Draw a flat line initially
    }

    public void setAppName(String name) {
        titleLabel.setText("Recording Memo for " + name);
    }

    /**
     * This method is called by the DialogHelper to start the recording process.
     * @param recorder The shared AudioRecorder instance.
     */
    public void start(AudioRecorder recorder, String audioFilePath) {
        try {
            // Start recording and provide a listener (a lambda function) that will receive audio data.
            // This listener is called from the AudioRecorder's background thread.
            recorder.startRecording(audioFilePath,
                    bytes -> Platform.runLater(() -> drawWaveform(bytes))
            );
        } catch (Exception e) {
            e.printStackTrace();
            // Optionally show an error dialog here
        }
    }

    private void drawSilence() {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, waveformCanvas.getWidth(), waveformCanvas.getHeight());
        gc.setStroke(Color.LIMEGREEN);
        gc.strokeLine(0, waveformCanvas.getHeight() / 2, waveformCanvas.getWidth(), waveformCanvas.getHeight() / 2);
    }

    /**
     * This method is called on the JavaFX Application Thread to draw the waveform.
     * @param chunk The raw byte array of audio data from the microphone.
     */
    private void drawWaveform(byte[] chunk) {
        // Convert the byte array to an array of shorts (16-bit audio samples)
        ShortBuffer shortBuffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] samples = new short[shortBuffer.remaining()];
        shortBuffer.get(samples);

        // Add the new samples to our buffer
        for (short sample : samples) {
            audioBuffer.add(sample);
        }

        // Trim the buffer to only keep the most recent samples that fit on the canvas
        while (audioBuffer.size() > maxBufferSize) {
            audioBuffer.removeFirst();
        }

        // Clear the canvas with a black background
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, waveformCanvas.getWidth(), waveformCanvas.getHeight());

        // Set up the drawing properties
        gc.setStroke(Color.LIMEGREEN);
        gc.setLineWidth(1.5);
        double centerY = waveformCanvas.getHeight() / 2;

        // Draw the waveform
        gc.beginPath();
        int i = 0;
        for (Short sample : audioBuffer) {
            // Map the sample value (-32768 to 32767) to a Y coordinate on the canvas
            double y = centerY + (sample / 32768.0) * centerY;
            if (i == 0) {
                gc.moveTo(i, y);
            } else {
                gc.lineTo(i, y);
            }
            i++;
        }
        gc.stroke();
    }

    @FXML
    private void handleStop() {
        Stage stage = (Stage) stopButton.getScene().getWindow();
        stage.close();
    }
}