package org.stefanapetri.licenta.service;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class AudioRecorder {

    // Define audio format. 16kHz is good for voice.
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, true);
    private TargetDataLine microphone;
    private File audioFile;

    public void startRecording(String filePath) throws LineUnavailableException {
        audioFile = new File(filePath);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);

        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Audio line not supported");
        }

        microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(FORMAT);
        microphone.start();

        // Start a new thread to write the audio data to a file
        Thread recordingThread = new Thread(() -> {
            try {
                AudioSystem.write(new AudioInputStream(microphone), AudioFileFormat.Type.WAVE, audioFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        recordingThread.setDaemon(true);
        recordingThread.start();
    }

    public void stopRecording() {
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
    }
}