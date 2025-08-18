package org.stefanapetri.licenta.service;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.function.Consumer;

public class AudioRecorder {

    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, true);
    private TargetDataLine microphone;
    private volatile boolean isRecording = false;

    /**
     * Starts recording audio from the microphone.
     * @param filePath The path to save the final .wav file.
     * @param dataListener A consumer that will receive live chunks of audio data (byte arrays) for visualization.
     * @throws LineUnavailableException if the microphone cannot be accessed.
     */
    public void startRecording(String filePath, Consumer<byte[]> dataListener) throws LineUnavailableException, IOException {
        File audioFile = new File(filePath);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);

        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Audio line not supported");
        }

        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(FORMAT);
        microphone.start();
        isRecording = true;

        // --- NEW: A separate thread to handle audio capture and streaming ---
        Thread captureThread = new Thread(() -> {
            // Use a piped stream to write the audio file in a separate thread
            // while simultaneously processing the live data.
            try (PipedOutputStream out = new PipedOutputStream();
                 PipedInputStream in = new PipedInputStream(out)) {

                // Thread to write the piped stream to a file
                Thread fileWriterThread = new Thread(() -> {
                    try {
                        AudioSystem.write(new AudioInputStream(in, FORMAT, AudioSystem.NOT_SPECIFIED), AudioFileFormat.Type.WAVE, audioFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                fileWriterThread.start();

                byte[] buffer = new byte[1024]; // Process audio in 1KB chunks
                while (isRecording) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        // Send a copy of the data to the UI for visualization
                        if (dataListener != null) {
                            dataListener.accept(buffer.clone());
                        }
                        // Write the data to the file-writing stream
                        out.write(buffer, 0, bytesRead);
                    }
                }
                out.close(); // This will terminate the fileWriterThread
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        captureThread.setDaemon(true);
        captureThread.start();
    }

    public void stopRecording() {
        if (microphone != null) {
            isRecording = false; // Signal the capture thread to stop
            microphone.stop();
            microphone.close();
        }
    }
}