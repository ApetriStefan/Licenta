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
    private PipedOutputStream pipedOutputStream; // Declared at class level

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

        // Initialize piped streams: pipedOutputStream is now a class field
        pipedOutputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);
        AudioInputStream audioInputStream = new AudioInputStream(pipedInputStream, FORMAT, AudioSystem.NOT_SPECIFIED);

        // Thread 1: Captures from mic and writes to pipedOutputStream
        Thread captureThread = new Thread(() -> {
            byte[] buffer = new byte[1024]; // Read in 1KB chunks
            try {
                while (isRecording) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        // Send a copy of the data to the UI for visualization
                        if (dataListener != null) {
                            dataListener.accept(buffer.clone());
                        }
                        // Write the data to the file-writing stream
                        pipedOutputStream.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                // This catches if the pipe is closed by the reader thread prematurely
                System.err.println("AudioRecorder captureThread IOException: " + e.getMessage());
            } finally {
                // Crucial: Close the output stream only after the capture loop finishes
                // This signals the AudioSystem.write (in writerThread) to finish.
                try {
                    if (pipedOutputStream != null) {
                        pipedOutputStream.close();
                    }
                } catch (IOException e) {
                    System.err.println("Error closing pipedOutputStream: " + e.getMessage());
                }
            }
        });

        // Thread 2: Reads from the pipedInputStream and writes to the final WAV file
        Thread writerThread = new Thread(() -> {
            try {
                // AudioSystem.write will block until the AudioInputStream is exhausted (i.e., pipedOutputStream closes)
                AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, audioFile);
            } catch (IOException e) {
                System.err.println("AudioRecorder writerThread IOException: " + e.getMessage());
            } finally {
                // Ensure input stream is closed, which also closes the pipedInputStream
                try {
                    audioInputStream.close();
                } catch (IOException e) {
                    System.err.println("Error closing audioInputStream: " + e.getMessage());
                }
            }
        });

        captureThread.setDaemon(true); // Allow JVM to exit if only daemon threads remain
        writerThread.setDaemon(true); // Allow JVM to exit if only daemon threads remain
        captureThread.start();
        writerThread.start();
    }

    public void stopRecording() {
        if (microphone != null) {
            isRecording = false; // Signal the capture thread to stop its loop
            microphone.stop();
            microphone.close();
        }
        // The pipedOutputStream will be closed by the captureThread's finally block now.
    }
}