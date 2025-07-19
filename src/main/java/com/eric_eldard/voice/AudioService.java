package com.eric_eldard.voice;

import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service for handling audio input (microphone) and output (speakers)
 */
@Slf4j
public class AudioService
{
    // Audio format configuration
    private static final float SAMPLE_RATE = 24000.0f; // OpenAI Realtime API uses 24kHz

    private static final int SAMPLE_SIZE_IN_BITS = 16;

    private static final int CHANNELS = 1; // Mono

    private static final boolean SIGNED = true;

    private static final boolean BIG_ENDIAN = false;

    private final AudioFormat audioFormat;

    private final AtomicBoolean recording = new AtomicBoolean(false);

    private TargetDataLine microphone;

    private SourceDataLine speakers;

    private Thread recordingThread;

    private AudioDataListener audioDataListener;

    private volatile double currentVolume = 0.0;

    // Audio playback state
    private final AtomicBoolean playingAudio = new AtomicBoolean(false);

    private final AtomicBoolean audioMuted = new AtomicBoolean(false);

    private final ByteArrayOutputStream audioPlaybackBuffer = new ByteArrayOutputStream();

    private final Object playbackLock = new Object();


    public AudioService()
    {
        this.audioFormat = new AudioFormat(
            SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN
        );
        log.info("AudioService initialized");
    }

    public void setAudioDataListener(AudioDataListener listener)
    {
        this.audioDataListener = listener;
    }

    public boolean initializeMicrophone()
    {
        try
        {
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(micInfo))
            {
                log.error("Microphone not supported with the specified audio format");
                return false;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(audioFormat);
            log.info("Microphone initialized successfully");
            return true;

        }
        catch (LineUnavailableException e)
        {
            log.error("Failed to initialize microphone", e);
            return false;
        }
    }

    public boolean initializeSpeakers()
    {
        try
        {
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(speakerInfo))
            {
                log.error("Speakers not supported with the specified audio format");
                return false;
            }

            speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speakers.open(audioFormat);
            log.info("Speakers initialized successfully");
            return true;

        }
        catch (LineUnavailableException e)
        {
            log.error("Failed to initialize speakers", e);
            return false;
        }
    }

    public void startRecording()
    {
        if (microphone == null)
        {
            log.error("Microphone not initialized");
            return;
        }

        if (recording.get())
        {
            log.warn("Recording already in progress");
            return;
        }

        recording.set(true);
        microphone.start();

        recordingThread = new Thread(() ->
        {
            byte[] buffer = new byte[1024];

            while (recording.get())
            {
                int bytesRead = microphone.read(buffer, 0, buffer.length);

                if (bytesRead > 0)
                {
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);

                    // Calculate volume level
                    currentVolume = calculateVolume(audioData);

                    if (audioDataListener != null)
                    {
                        audioDataListener.onAudioData(audioData);
                    }
                }
            }
        });

        recordingThread.start();
        log.info("Recording started");
    }

    public void stopRecording()
    {
        if (!recording.get())
        {
            return;
        }

        recording.set(false);

        if (microphone != null)
        {
            microphone.stop();
        }

        if (recordingThread != null)
        {
            try
            {
                recordingThread.join(1000); // Wait up to 1 second
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }

        log.info("Recording stopped");
    }

    public void startAudioPlayback()
    {
        if (speakers == null)
        {
            log.error("Speakers not initialized");
            return;
        }

        synchronized (playbackLock)
        {
            if (playingAudio.get())
            {
                log.warn("Audio playback already in progress");
                return;
            }

            playingAudio.set(true);
            audioPlaybackBuffer.reset();
            speakers.start();
            log.info("Audio playback started");
        }
    }

    public void streamAudioData(byte[] audioData)
    {
        if (speakers == null)
        {
            log.error("Speakers not initialized");
            return;
        }

        if (!playingAudio.get())
        {
            log.warn("Audio playback not started - call startAudioPlayback() first");
            return;
        }

        synchronized (playbackLock)
        {
            try
            {

                // Only write audio data if not muted
                if (!audioMuted.get())
                {
                    speakers.write(audioData, 0, audioData.length);
                    // Reduce debug logging frequency for performance - log every ~50KB
                    if (audioData.length > 0 && audioData.length % 51200 == 0)
                    {
                        log.debug("Streamed {} bytes of audio data", audioData.length);
                    }
                }
                else
                {
                    // Reduce debug logging frequency for performance - log every ~50KB
                    if (audioData.length > 0 && audioData.length % 51200 == 0)
                    {
                        log.debug("Audio muted - skipped {} bytes of audio data", audioData.length);
                    }
                }
            }
            catch (Exception e)
            {
                log.error("Failed to stream audio data", e);
            }
        }
    }

    public void stopAudioPlayback()
    {
        synchronized (playbackLock)
        {
            if (!playingAudio.get())
            {
                return;
            }

            playingAudio.set(false);

            if (speakers != null)
            {
                speakers.drain(); // Wait for all queued audio to play
                speakers.stop();
            }

            audioPlaybackBuffer.reset();
            log.info("Audio playback stopped");
        }
    }

    public boolean isPlayingAudio()
    {
        return playingAudio.get();
    }

    // Legacy method for backward compatibility - now uses streaming approach
    public void playAudio(byte[] audioData)
    {
        startAudioPlayback();
        streamAudioData(audioData);
        stopAudioPlayback();
    }

    public boolean isRecording()
    {
        return recording.get();
    }

    public double getCurrentVolume()
    {
        return currentVolume;
    }

    public void setAudioMuted(boolean muted)
    {
        audioMuted.set(muted);
        log.info("Audio output {}", muted ? "muted" : "unmuted");
    }

    public boolean isAudioMuted()
    {
        return audioMuted.get();
    }

    public void toggleAudioMute()
    {
        boolean newMuteState = !audioMuted.get();
        setAudioMuted(newMuteState);
    }


    private double calculateVolume(byte[] audioData)
    {
        if (audioData.length == 0)
        {
            return 0.0;
        }

        // Calculate RMS (Root Mean Square) volume
        long sum = 0;
        for (int i = 0; i < audioData.length - 1; i += 2)
        {
            // Convert two bytes to a 16-bit sample
            int sample = (audioData[i + 1] << 8) | (audioData[i] & 0xFF);
            sum += sample * sample;
        }

        double rms = Math.sqrt((double) sum / (audioData.length / 2));

        // Normalize to 0-100 range with improved scaling for typical microphone levels
        // Use a smaller divisor to make the volume meter more sensitive to normal speech levels
        double volume = (rms / 8192.0) * 100.0; // Changed from 32768.0 to 8192.0 for 4x sensitivity
        return Math.min(100.0, Math.max(0.0, volume));
    }

    public void shutdown()
    {
        stopRecording();

        if (microphone != null)
        {
            microphone.close();
        }

        if (speakers != null)
        {
            speakers.close();
        }
    }

    public interface AudioDataListener
    {
        void onAudioData(byte[] audioData);
    }
}
