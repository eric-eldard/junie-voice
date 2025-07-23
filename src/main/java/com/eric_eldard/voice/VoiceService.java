package com.eric_eldard.voice;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Combined voice service that integrates OpenAI Realtime API with audio input/output
 */
@Slf4j
public class VoiceService implements OpenAIRealtimeService.VoiceEventListener, AudioService.AudioDataListener
{
    private final OpenAIRealtimeService openAIService;

    @Getter
    private final AudioService audioService;

    @Setter
    private VoiceServiceListener serviceListener;

    // Audio response state tracking
    private volatile boolean audioResponseActive = false;

    public VoiceService(String openAIApiKey, String model, String voice, String junieConfig)
    {
        this.openAIService = new OpenAIRealtimeService(openAIApiKey, model, voice, junieConfig);
        this.audioService = new AudioService();

        // Set up listeners
        this.openAIService.setEventListener(this);
        this.audioService.setAudioDataListener(this);
    }

    public CompletableFuture<Boolean> initialize()
    {
        log.info("Initializing voice service...");

        // Initialize audio components
        boolean micInitialized = audioService.initializeMicrophone();
        boolean speakersInitialized = audioService.initializeSpeakers();

        if (!micInitialized || !speakersInitialized)
        {
            log.error("Failed to initialize audio components");
            return CompletableFuture.completedFuture(false);
        }

        // Connect to OpenAI
        return openAIService.connect();
    }

    public void startVoiceSession()
    {
        if (!openAIService.isConnected())
        {
            log.error("Cannot start voice session - not connected to OpenAI");
            return;
        }

        audioService.startRecording();

        // If we're starting during an AI response, this is an interruption
        if (audioResponseActive)
        {
            log.info("Voice session started during AI response (interruption)");
        }
        else
        {
            log.info("Voice session started");
        }

        if (serviceListener != null)
        {
            serviceListener.onVoiceSessionStarted();
        }
    }

    public void stopVoiceSession()
    {
        audioService.stopRecording();

        // Check buffer size before committing using improved validation
        double bufferDurationMs = openAIService.getAudioBufferDurationMs();
        boolean hasMinimumData = openAIService.hasMinimumAudioData();

        log.info("Stopping voice session. Audio buffer: {}ms, has minimum data: {}", bufferDurationMs, hasMinimumData);

        // Only commit if we have sufficient audio data (minimum 100ms)
        if (hasMinimumData && bufferDurationMs >= 100.0)
        {
            // Commit the audio buffer to OpenAI for processing
            openAIService.commitAudioBuffer();
            log.info("Audio buffer committed successfully");
        }
        else
        {
            log.debug("Skipping audio buffer commit - insufficient audio data ({}ms, minimum check: {})",
                bufferDurationMs, hasMinimumData);
            // Clear the buffer since we're not using it
            openAIService.clearAudioBuffer();

            // Log the short recording at debug level instead of treating it as an error
            log.debug(
                "Recording too short ({}ms). Please record for at least 100ms and ensure microphone is working.",
                bufferDurationMs);
        }

        log.info("Voice session stopped");

        if (serviceListener != null)
        {
            serviceListener.onVoiceSessionStopped();
        }
    }

    public boolean isRecording()
    {
        return audioService.isRecording();
    }

    public boolean isConnected()
    {
        return openAIService.isConnected();
    }

    public void sendTextMessage(String text)
    {
        if (!openAIService.isConnected())
        {
            log.error("Cannot send text message - not connected to OpenAI");
            return;
        }

        openAIService.sendTextMessage(text);
        log.info("Sent text message: {}", text);
    }

    public void injectAssistantMessage(String text)
    {
        if (!openAIService.isConnected())
        {
            log.error("Cannot inject assistant message - not connected to OpenAI");
            return;
        }

        openAIService.injectAssistantMessage(text);
        log.info("Injected assistant message: {}", text);
    }

    public void shutdown()
    {
        stopVoiceSession();

        // Clean up AI response state if shutdown during response
        if (audioResponseActive)
        {
            audioService.stopAudioPlayback();
            audioResponseActive = false;
            log.info("Cleaned up active AI response during shutdown");
        }

        openAIService.disconnect();
        audioService.shutdown();
        log.info("Voice service shut down");
    }

    // OpenAI service event handlers
    @Override
    public void onConnected()
    {
        log.info("Connected to OpenAI Realtime API");
        if (serviceListener != null)
        {
            serviceListener.onConnected();
        }
    }

    @Override
    public void onDisconnected()
    {
        log.info("Disconnected from OpenAI Realtime API");
        stopVoiceSession(); // Stop recording if we lose connection

        // Clean up AI response state if disconnected during response
        if (audioResponseActive)
        {
            audioService.stopAudioPlayback();
            audioResponseActive = false;
        }

        if (serviceListener != null)
        {
            serviceListener.onDisconnected();
        }
    }

    @Override
    public void onMessage(String message)
    {
        log.debug("Received message from OpenAI: {}", message);
        if (serviceListener != null)
        {
            serviceListener.onResponseReceived(message);
        }
    }

    @Override
    public void onTraceMessage(String message)
    {
        log.trace("Received trace message from OpenAI: {}", message);
        if (serviceListener != null)
        {
            serviceListener.onTraceReceived(message);
        }
    }

    @Override
    public void onError(Throwable error)
    {
        log.error("OpenAI service error", error);
        if (serviceListener != null)
        {
            serviceListener.onError(error);
        }
    }

    // Audio service event handlers
    @Override
    public void onAudioData(byte[] audioData)
    {
        // Always send audio data to OpenAI when available - this enables interruption
        // The AudioService will only call this when microphone is actively recording

        // Send audio data to OpenAI with reduced logging frequency for performance
        if (audioData.length > 0 && audioData.length % 51200 == 0)
        { // Log every ~50KB for better performance
            log.debug("Received audio data from microphone: {} bytes", audioData.length);
        }
        openAIService.sendAudioData(audioData);
    }

    // Additional OpenAI event handlers
    @Override
    public void onSpeechStarted()
    {
        log.info("Speech started detected by OpenAI");
        // Could add UI feedback here
    }

    @Override
    public void onSpeechStopped()
    {
        log.info("Speech stopped detected by OpenAI");
        // Could add UI feedback here
    }

    @Override
    public void onTextResponse(String text)
    {
        log.info("Received text response: {}", text);
        if (serviceListener != null)
        {
            serviceListener.onResponseReceived("Text: " + text);
        }
    }

    @Override
    public void onTextDelta(String delta)
    {
        log.debug("Received text delta: {}", delta);
        if (serviceListener != null)
        {
            serviceListener.onTextDelta(delta);
        }
    }

    @Override
    public void onUserSpeechEnded(String message)
    {
        log.info("User stopped talking; final message: {}", message);
        if (serviceListener != null)
        {
            serviceListener.onUserSpeechEnded();
        }
    }

    @Override
    public void onResponseStarted()
    {
        log.info("New response started");

        // If there's already an active audio response, interrupt it
        if (audioResponseActive)
        {
            log.info("Interrupting current audio response for new response");
            audioService.stopAudioPlayback();

            // Log the interruption but don't change the audioResponseActive flag yet
            // It will be set to true again when the first audio delta arrives
            if (serviceListener != null)
            {
                serviceListener.onResponseReceived("🤖 AI: [Response interrupted by new response]");
            }
        }
    }

    @Override
    public void onAudioResponse(byte[] audioData)
    {
        log.debug("Received audio response: {} bytes", audioData.length);
        boolean wasMuted = !audioService.isRecording();

        // Start audio playback on first audio delta
        if (!audioResponseActive)
        {
            audioResponseActive = true;

            // Stop microphone recording to prevent feedback loop
            if (!wasMuted)
            {
                audioService.stopRecording();
                log.info("Stopped microphone recording to prevent feedback during AI response");
            }

            audioService.startAudioPlayback();
            log.info("Started streaming audio response");

            // Notify listener that audio response started
            if (serviceListener != null)
            {
                serviceListener.onAudioResponseStarted(wasMuted);
            }
        }

        // Stream the audio delta (only if not muted)
        audioService.streamAudioData(audioData);
    }

    @Override
    public void onResponseComplete()
    {
        log.info("Response generation completed");

        // Stop audio playback if it was active
        if (audioResponseActive)
        {
            audioService.stopAudioPlayback();
            audioResponseActive = false;
            log.info("Stopped streaming audio response");

            // Notify listener that audio response completed
            if (serviceListener != null)
            {
                serviceListener.onAudioResponseCompleted();
            }
        }
    }

    @Override
    public void onUserTranscript(String transcript)
    {
        log.info("User transcript: {}", transcript);
        if (serviceListener != null)
        {
            serviceListener.onUserTranscript(transcript);
        }
    }

    @Override
    public void onAgentTranscript(String transcript)
    {
        log.info("Agent transcript: {}", transcript);
        if (serviceListener != null)
        {
            serviceListener.onAgentTranscript(transcript);
        }
    }

    @Override
    public void onRequestLog(String requestType, String details, String responseCode)
    {
        log.debug("Request log: {} - {} [{}]", requestType, details, responseCode);
        if (serviceListener != null)
        {
            serviceListener.onRequestLog(requestType, details, responseCode);
        }
    }

    public interface VoiceServiceListener
    {
        void onConnected();

        void onDisconnected();

        void onVoiceSessionStarted();

        void onVoiceSessionStopped();

        void onResponseReceived(String response);

        void onTraceReceived(String trace);

        void onTextDelta(String delta);

        void onUserSpeechEnded();

        void onAudioResponseStarted(boolean micWasMuted);

        void onAudioResponseCompleted();

        void onUserTranscript(String transcript);

        void onAgentTranscript(String transcript);

        void onError(Throwable error);

        void onRequestLog(String requestType, String details, String responseCode);
    }
}