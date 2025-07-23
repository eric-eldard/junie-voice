package com.eric_eldard.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for connecting to OpenAI's Realtime voice API This service is agnostic from IntelliJ plugins and can be used
 * standalone
 */
@Slf4j
public class OpenAIRealtimeService
{
    private static final String OPENAI_REALTIME_URL = "wss://api.openai.com/v1/realtime";

    // Audio format constants for buffer size calculation
    private static final float SAMPLE_RATE = 24000.0f; // 24kHz

    private static final int SAMPLE_SIZE_IN_BITS = 16;

    private static final int CHANNELS = 1;

    private static final long MIN_BUFFER_DURATION_MS = 100; // Minimum 100ms of audio

    private final String instructions;

    private final String apiKey;

    private final String model;

    private final String voice;

    private final ObjectMapper objectMapper;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final AtomicLong audioBufferSize = new AtomicLong(0); // Track buffer size in bytes

    private volatile long lastApiCallTime = 0; // For rate limiting

    private static final long MIN_API_CALL_INTERVAL_MS = 500;
    // Increased to 500ms between API calls for better rate limiting

    private volatile long backoffDelayMs = MIN_API_CALL_INTERVAL_MS; // Exponential backoff delay

    private static final long MAX_BACKOFF_DELAY_MS = 5000; // Maximum 5 seconds backoff

    private volatile int consecutiveErrors = 0; // Track consecutive 429 errors

    private volatile long lastTranscriptionTime = 0; // Separate rate limiting for transcription operations

    private static final long MIN_TRANSCRIPTION_INTERVAL_MS = 1000; // Minimum 1 second between transcription operations

    // Temporary buffer for accumulating audio data when rate limited
    private final ByteArrayOutputStream tempAudioBuffer = new ByteArrayOutputStream();

    private final Object bufferLock = new Object();

    private final OkHttpClient client;

    private WebSocket webSocket;

    @Setter
    private VoiceEventListener eventListener;

    public OpenAIRealtimeService(String apiKey, String model, String voice, String junieConfig)
    {
        this.apiKey = apiKey;
        this.model = model;
        this.voice = voice;
        this.objectMapper = new ObjectMapper();
        this.client = new OkHttpClient();
        this.instructions = junieConfig + """
            
            # General Guidance
            You are a helpful AI assistant for developers. You are always **brief**, professional, and enthusiastic.

            # Discussing Code
            IMPORTANT: Never speak code aloud - provide _brief_ conceptual explanations only, not code syntax.
            
            # Creating Prompts
            IMPORTANT: Never speak prompts aloud - when the user wants to build something or create a prompt for another
            agent, simply say, "I'll generate a prompt for you". The text agent will handle the actual prompt creation.

            # Web Search & Browsing
            You have the ability to search the internet and retrieve content for specific URLs (aka, a scrape).
            Whenever you perform a web search or scrape, do the following steps:
            1. Inform the user that you are performing a web search (or scrape).
            2. Perform the search/scrape.
            3. Provide the results in a concise and easy-to-understand manner.
            4. Confirm that you have completed the search/scrape and delivered the information.
            Make sure to respond promptly after the search/scrape is complete without waiting for additional input.
            """;
    }

    public CompletableFuture<Boolean> connect()
    {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Request request = new Request.Builder()
            .url(OPENAI_REALTIME_URL + "?model=" + model)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build();

        // Log the connection request
        if (eventListener != null)
        {
            eventListener.onRequestLog("WebSocket Connection",
                "Connecting to " + OPENAI_REALTIME_URL + " with model " + model,
                "PENDING");
        }

        webSocket = client.newWebSocket(request, new WebSocketListener()
        {
            @Override
            public void onOpen(WebSocket webSocket, Response response)
            {
                log.info("Connected to OpenAI Realtime API");
                connected.set(true);

                // Log successful connection with response code
                if (eventListener != null)
                {
                    String responseCode = response != null ? String.valueOf(response.code()) : "101";
                    eventListener.onRequestLog("WebSocket Connection",
                        "Successfully connected to OpenAI Realtime API",
                        responseCode);
                }

                // Send session configuration
                sendSessionUpdate();

                future.complete(true);
                if (eventListener != null)
                {
                    eventListener.onConnected();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text)
            {
                log.debug("Received message: {}", text);
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason)
            {
                log.info("Connection closing: {} {}", code, reason);
                connected.set(false);
                if (eventListener != null)
                {
                    eventListener.onDisconnected();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response)
            {
                log.error("WebSocket failure", t);
                connected.set(false);

                // Log connection failure with response code if available
                if (eventListener != null)
                {
                    String responseCode = response != null ? String.valueOf(response.code()) : "ERROR";
                    eventListener.onRequestLog("WebSocket Connection",
                        "Connection failed: " + t.getMessage(),
                        responseCode);
                }

                future.complete(false);
                if (eventListener != null)
                {
                    eventListener.onError(t);
                }
            }
        });

        return future;
    }

    public void disconnect()
    {
        if (webSocket != null)
        {
            webSocket.close(1000, "Client disconnect");
        }
        connected.set(false);
    }

    public boolean isConnected()
    {
        return connected.get();
    }

    private void sendSessionUpdate()
    {
        try
        {
            ObjectNode sessionUpdate = objectMapper.createObjectNode();
            sessionUpdate.put("type", "session.update");

            ObjectNode session = objectMapper.createObjectNode();
            session.put("modalities", objectMapper.createArrayNode().add("text").add("audio"));
            session.put("instructions", instructions);
            session.put("voice", voice);
            session.put("input_audio_format", "pcm16");
            session.put("output_audio_format", "pcm16");
            session.put("input_audio_transcription", objectMapper.createObjectNode().put("model", "whisper-1"));
            ObjectNode turnDetection = objectMapper.createObjectNode();
            turnDetection.put("type", "server_vad");
            turnDetection.put("silence_duration_ms", 1000);
            session.put("turn_detection", turnDetection);

            session.set("tools", objectMapper.createArrayNode());
            session.put("temperature", 0.8);
            session.put("max_response_output_tokens", 4096);

            sessionUpdate.set("session", session);

            String message = objectMapper.writeValueAsString(sessionUpdate);

            // Log the session update request
            if (eventListener != null)
            {
                eventListener.onRequestLog("Session Update",
                    "Configuring session with voice=" + voice + ", modalities=[text,audio], format=pcm16",
                    "SENT");
            }

            webSocket.send(message);
            log.debug("Sent session update: {}", message);
        }
        catch (Exception e)
        {
            log.error("Failed to send session update", e);

            // Log session update failure
            if (eventListener != null)
            {
                eventListener.onRequestLog("Session Update",
                    "Failed to send session configuration: " + e.getMessage(),
                    "ERROR");
            }
        }
    }

    public void sendAudioData(byte[] audioData)
    {
        if (!isConnected())
        {
            log.warn("Cannot send audio data - not connected");
            return;
        }

        byte[] dataToSend = null;

        synchronized (bufferLock)
        {
            // Always accumulate incoming audio data
            try
            {
                tempAudioBuffer.write(audioData);
                // Reduce debug logging frequency to improve performance
                if (tempAudioBuffer.size() % 51200 == 0)
                { // Log every 50KB accumulated for better performance
                    log.debug("Accumulated audio data: {} bytes, temp buffer size: {} bytes",
                        audioData.length, tempAudioBuffer.size());
                }
            }
            catch (IOException e)
            {
                log.error("Failed to accumulate audio data", e);
                return;
            }

            // Enhanced rate limiting with exponential backoff to prevent 429 errors
            long currentTime = System.currentTimeMillis();
            long requiredDelay = Math.max(MIN_API_CALL_INTERVAL_MS, backoffDelayMs);

            if (currentTime - lastApiCallTime < requiredDelay)
            {
                log.debug("Rate limiting: delaying send ({}ms remaining)",
                    requiredDelay - (currentTime - lastApiCallTime));
                return;
            }

            // Prepare data for sending (minimize time in synchronized block)
            if (tempAudioBuffer.size() > 0)
            {
                dataToSend = tempAudioBuffer.toByteArray();
                tempAudioBuffer.reset(); // Clear the buffer
                lastApiCallTime = currentTime;
            }
        }

        // Perform expensive operations outside synchronized block
        if (dataToSend != null)
        {
            try
            {
                // Encode audio data as base64 (expensive operation)
                String base64Audio = Base64.getEncoder().encodeToString(dataToSend);

                // Create JSON message (expensive operation)
                ObjectNode audioMessage = objectMapper.createObjectNode();
                audioMessage.put("type", "input_audio_buffer.append");
                audioMessage.put("audio", base64Audio);

                String message = objectMapper.writeValueAsString(audioMessage);

                // Send via WebSocket
                webSocket.send(message);

                // Track buffer size ONLY after successful send
                long totalBufferSize = audioBufferSize.addAndGet(dataToSend.length);
                log.debug("Sent accumulated audio data: {} bytes, total buffer: {} bytes",
                    dataToSend.length, totalBufferSize);

                // Log audio data transmission
                if (eventListener != null)
                {
                    eventListener.onTraceMessage(
                        String.format("Audio Data: Sent %d bytes of audio data (total buffer: %d bytes) [SENT]",
                            dataToSend.length, totalBufferSize));
                }

                // Reset backoff on successful send
                resetBackoff();
            }
            catch (Exception e)
            {
                log.error("Failed to send accumulated audio data", e);

                // Log audio data transmission failure
                if (eventListener != null)
                {
                    eventListener.onTraceMessage(
                        "Audio Data: Failed to send audio data: " + e.getMessage() + " [ERROR]");
                }

                // Increase backoff delay on failure to prevent rapid retries
                increaseBackoff();
            }
        }
    }

    public void sendTextMessage(String text)
    {
        if (!isConnected())
        {
            log.warn("Cannot send text message - not connected");
            return;
        }

        try
        {
            // Create conversation item with text content
            ObjectNode itemMessage = objectMapper.createObjectNode();
            itemMessage.put("type", "conversation.item.create");

            ObjectNode item = objectMapper.createObjectNode();
            item.put("type", "message");
            item.put("role", "user");

            // Create content array with text content
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "input_text");
            textContent.put("text", text);

            item.set("content", objectMapper.createArrayNode().add(textContent));
            itemMessage.set("item", item);

            String message = objectMapper.writeValueAsString(itemMessage);

            // Log text message sending
            if (eventListener != null)
            {
                eventListener.onRequestLog("Text Message",
                    "Sending text message: " + text,
                    "SENT");
            }

            webSocket.send(message);
            log.debug("Sent text message: {}", message);

            // After sending the text message, request a response
            requestResponse();
        }
        catch (Exception e)
        {
            log.error("Failed to send text message", e);

            // Log text message failure
            if (eventListener != null)
            {
                eventListener.onRequestLog("Text Message",
                    "Failed to send text message: " + e.getMessage(),
                    "ERROR");
                eventListener.onError(e);
            }
        }
    }

    public void injectAssistantMessage(String text)
    {
        if (!isConnected())
        {
            log.warn("Cannot inject assistant message - not connected");
            return;
        }

        try
        {
            // Create conversation item with assistant text content
            ObjectNode itemMessage = objectMapper.createObjectNode();
            itemMessage.put("type", "conversation.item.create");

            ObjectNode item = objectMapper.createObjectNode();
            item.put("type", "message");
            item.put("role", "assistant");

            // Create content array with text content
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", text);

            item.set("content", objectMapper.createArrayNode().add(textContent));
            itemMessage.set("item", item);

            String message = objectMapper.writeValueAsString(itemMessage);

            // Log assistant message injection
            if (eventListener != null)
            {
                eventListener.onRequestLog("Assistant Message",
                    "Injecting assistant message into conversation: " + text,
                    "SENT");
            }

            webSocket.send(message);
            log.debug("Injected assistant message: {}", message);

            // Note: We don't request a response after injecting assistant messages
            // as this is meant to add context to the conversation, not trigger new responses
        }
        catch (Exception e)
        {
            log.error("Failed to inject assistant message", e);

            // Log assistant message failure
            if (eventListener != null)
            {
                eventListener.onRequestLog("Assistant Message",
                    "Failed to inject assistant message: " + e.getMessage(),
                    "ERROR");
                eventListener.onError(e);
            }
        }
    }

    public void commitAudioBuffer()
    {
        commitAudioBuffer(false);
    }

    public void commitAudioBuffer(boolean forceCommit)
    {
        if (!isConnected())
        {
            log.warn("Cannot commit audio buffer - not connected");
            return;
        }

        // Transcription-specific rate limiting to prevent 429 errors
        long currentTime = System.currentTimeMillis();
        long timeSinceLastTranscription = currentTime - lastTranscriptionTime;
        long requiredTranscriptionDelay = Math.max(MIN_TRANSCRIPTION_INTERVAL_MS, backoffDelayMs);

        if (timeSinceLastTranscription < requiredTranscriptionDelay)
        {
            log.warn("Transcription rate limiting: skipping commit ({}ms since last, {}ms required)",
                timeSinceLastTranscription, requiredTranscriptionDelay);

            // Log rate limiting
            if (eventListener != null)
            {
                eventListener.onRequestLog("Buffer Commit",
                    String.format("Rate limited: need to wait %dms before next commit",
                        requiredTranscriptionDelay - timeSinceLastTranscription),
                    "RATE_LIMITED");
                eventListener.onError(new RuntimeException(
                    String.format("Transcription rate limited. Please wait %dms before next commit.",
                        requiredTranscriptionDelay - timeSinceLastTranscription)));
            }
            return;
        }

        // Check if we have enough audio data (minimum 100ms) - skip check if forced
        long currentBufferSize = audioBufferSize.get();
        long minBufferSizeBytes = calculateMinBufferSizeBytes();

        if (!forceCommit && currentBufferSize < minBufferSizeBytes)
        {
            double durationMs = calculateBufferDurationMs(currentBufferSize);
            log.warn("Audio buffer too small: {}ms (minimum {}ms required). Skipping commit.",
                durationMs, MIN_BUFFER_DURATION_MS);

            // Reset buffer size since we're not committing
            audioBufferSize.set(0);

            if (eventListener != null)
            {
                eventListener.onError(new RuntimeException(
                    String.format("Audio buffer too small: %.2fms (minimum %dms required)",
                        durationMs, MIN_BUFFER_DURATION_MS)));
            }
            return;
        }

        // Final safety check - never commit if buffer size is zero
        if (currentBufferSize == 0)
        {
            log.warn("Skipping commit - buffer size is zero (forceCommit: {})", forceCommit);
            return;
        }

        try
        {
            ObjectNode commitMessage = objectMapper.createObjectNode();
            commitMessage.put("type", "input_audio_buffer.commit");

            String message = objectMapper.writeValueAsString(commitMessage);

            double durationMs = calculateBufferDurationMs(currentBufferSize);

            // Log buffer commit request
            if (eventListener != null)
            {
                eventListener.onRequestLog("Buffer Commit",
                    String.format("Committing audio buffer: %d bytes (%.2fms)", currentBufferSize, durationMs),
                    "SENT");
            }

            webSocket.send(message);

            // Record transcription time for rate limiting
            lastTranscriptionTime = currentTime;

            // Reset buffer size after successful commit
            audioBufferSize.set(0);

            // Reset backoff on successful commit
            resetBackoff();

            log.info("Committed audio buffer: {} bytes ({}ms)", currentBufferSize, durationMs);

            // After committing the audio buffer, request a response to trigger AI generation
            requestResponse();
        }
        catch (Exception e)
        {
            log.error("Failed to commit audio buffer", e);

            // Log buffer commit failure
            if (eventListener != null)
            {
                eventListener.onRequestLog("Buffer Commit",
                    "Failed to commit audio buffer: " + e.getMessage(),
                    "ERROR");
                eventListener.onError(e);
            }
        }
    }

    private void requestResponse()
    {
        if (!isConnected())
        {
            log.warn("Cannot request response - not connected");
            return;
        }

        try
        {
            ObjectNode responseRequest = objectMapper.createObjectNode();
            responseRequest.put("type", "response.create");

            // Configure response to include both text and audio
            ObjectNode response = objectMapper.createObjectNode();
            response.put("modalities", objectMapper.createArrayNode().add("text").add("audio"));
            response.put("instructions",
                "Respond with energy and enthusiasm while maintaining strict professionalism! Be engaging and upbeat," +
                    " but always use proper business language. Avoid roleplay, slang, casual expressions, or " +
                    "character voices. Stay focused on being a helpful, knowledgeable assistant.");

            responseRequest.set("response", response);

            String message = objectMapper.writeValueAsString(responseRequest);

            // Log response request
            if (eventListener != null)
            {
                eventListener.onRequestLog("Response Request",
                    "Requesting AI response with text and audio modalities",
                    "SENT");
            }

            webSocket.send(message);
            log.debug("Sent response request: {}", message);
        }
        catch (Exception e)
        {
            log.error("Failed to request response", e);

            // Log response request failure
            if (eventListener != null)
            {
                eventListener.onRequestLog("Response Request",
                    "Failed to request AI response: " + e.getMessage(),
                    "ERROR");
            }
        }
    }

    private long calculateMinBufferSizeBytes()
    {
        // Calculate minimum buffer size for 100ms of audio
        // Formula: (sample_rate * channels * bits_per_sample / 8) * (duration_ms / 1000)
        return (long) ((SAMPLE_RATE * CHANNELS * SAMPLE_SIZE_IN_BITS / 8) * (MIN_BUFFER_DURATION_MS / 1000.0));
    }

    private double calculateBufferDurationMs(long bufferSizeBytes)
    {
        // Calculate duration in milliseconds from buffer size
        // Formula: (buffer_size_bytes * 8 * 1000) / (sample_rate * channels * bits_per_sample)
        return (bufferSizeBytes * 8.0 * 1000.0) / (SAMPLE_RATE * CHANNELS * SAMPLE_SIZE_IN_BITS);
    }

    public void clearAudioBuffer()
    {
        synchronized (bufferLock)
        {
            audioBufferSize.set(0);
            tempAudioBuffer.reset();
            log.debug("Audio buffer cleared");
        }
    }

    public void flushAudioBuffer()
    {
        synchronized (bufferLock)
        {
            if (tempAudioBuffer.size() > 0)
            {
                try
                {
                    byte[] remainingData = tempAudioBuffer.toByteArray();
                    tempAudioBuffer.reset();

                    // Encode and send remaining audio data
                    String base64Audio = Base64.getEncoder().encodeToString(remainingData);

                    ObjectNode audioMessage = objectMapper.createObjectNode();
                    audioMessage.put("type", "input_audio_buffer.append");
                    audioMessage.put("audio", base64Audio);

                    String message = objectMapper.writeValueAsString(audioMessage);
                    webSocket.send(message);

                    // Track buffer size ONLY after successful send
                    long totalBufferSize = audioBufferSize.addAndGet(remainingData.length);
                    log.info("Flushed remaining audio data: {} bytes, total buffer: {} bytes",
                        remainingData.length, totalBufferSize);
                }
                catch (Exception e)
                {
                    log.error("Failed to flush remaining audio data", e);
                    // Increase backoff delay on failure
                    increaseBackoff();
                }
            }
        }
    }

    public long getAudioBufferSize()
    {
        return audioBufferSize.get();
    }

    public double getAudioBufferDurationMs()
    {
        return calculateBufferDurationMs(audioBufferSize.get());
    }

    public boolean hasMinimumAudioData()
    {
        long currentBufferSize = audioBufferSize.get();
        long minBufferSizeBytes = calculateMinBufferSizeBytes();
        return currentBufferSize >= minBufferSizeBytes;
    }

    private void resetBackoff()
    {
        if (consecutiveErrors > 0)
        {
            log.debug("Resetting backoff after successful operation");
            consecutiveErrors = 0;
            backoffDelayMs = MIN_API_CALL_INTERVAL_MS;
        }
    }

    private void increaseBackoff()
    {
        consecutiveErrors++;
        backoffDelayMs = Math.min(backoffDelayMs * 2, MAX_BACKOFF_DELAY_MS);
        log.warn("Increased backoff delay to {}ms after {} consecutive errors", backoffDelayMs, consecutiveErrors);
    }

    private void handleMessage(String messageText)
    {
        try
        {
            ObjectNode message = (ObjectNode) objectMapper.readTree(messageText);
            String type = message.get("type").asText();

            log.debug("Handling message type: {}", type);

            switch (type)
            {
                case "session.created":
                    log.info("Session created successfully");
                    if (eventListener != null)
                    {
                        eventListener.onRequestLog("API Response", "Session created successfully", "200");
                    }
                    break;

                case "session.updated":
                    log.info("Session updated successfully");
                    if (eventListener != null)
                    {
                        eventListener.onRequestLog("API Response", "Session configuration updated", "200");
                    }
                    break;

                case "input_audio_buffer.committed":
                    log.info("Audio buffer committed, processing...");
                    if (eventListener != null)
                    {
                        eventListener.onRequestLog("API Response", "Audio buffer committed for processing", "200");
                        eventListener.onUserSpeechEnded(type);
                    }
                    break;

                case "input_audio_buffer.speech_started":
                    log.info("Speech detected");
                    if (eventListener != null)
                    {
                        eventListener.onSpeechStarted();
                    }
                    break;

                case "input_audio_buffer.speech_stopped":
                    log.info("Speech ended");
                    if (eventListener != null)
                    {
                        eventListener.onSpeechStopped();
                    }
                    break;

                case "conversation.item.created":
                    log.info("Conversation item created");
                    break;

                case "response.created":
                    log.info("Response generation started");
                    if (eventListener != null)
                    {
                        eventListener.onResponseStarted();
                    }
                    break;

                case "response.output_item.added":
                    log.info("Response item added");
                    break;

                case "response.content_part.added":
                    if (message.has("part"))
                    {
                        ObjectNode part = (ObjectNode) message.get("part");
                        if ("text".equals(part.get("type").asText()))
                        {
                            String text = part.get("text").asText();
                            log.info("Received text response: {}", text);
                            if (eventListener != null)
                            {
                                eventListener.onTextResponse(text);
                            }
                        }
                    }
                    break;

                case "response.content_part.done":
                    eventListener.onTraceMessage(messageText);
                    break;

                case "response.text.delta":
                    if (message.has("delta"))
                    {
                        String textDelta = message.get("delta").asText();
                        log.debug("Received text delta: {}", textDelta);
                        if (eventListener != null)
                        {
                            eventListener.onTextDelta(textDelta);
                        }
                    }
                    break;

                case "response.audio.delta":
                    if (message.has("delta"))
                    {
                        String audioBase64 = message.get("delta").asText();
                        byte[] audioData = Base64.getDecoder().decode(audioBase64);
                        log.debug("Received audio delta: {} bytes", audioData.length);
                        if (eventListener != null)
                        {
                            eventListener.onAudioResponse(audioData);
                        }
                    }
                    break;

                case "response.done":
                    log.info("Response completed");
                    if (eventListener != null)
                    {
                        eventListener.onRequestLog("API Response", "AI response generation completed", "200");
                        eventListener.onResponseComplete();
                    }
                    break;

                case "error":
                    handleApiError(message);
                    break;

                case "conversation.item.input_audio_transcription.delta":
                    // Route transcript deltas to TRACE level logging
                    if (eventListener != null)
                    {
                        eventListener.onTraceMessage(messageText);
                    }
                    break;

                case "conversation.item.input_audio_transcription.completed":
                    // Extract user transcript text
                    log.debug("Input audio transcription completed");
                    if (eventListener != null && message.has("transcript"))
                    {
                        String transcript = message.get("transcript").asText();
                        eventListener.onUserTranscript(transcript);
                    }
                    break;

                case "response.audio_transcript.delta":
                    // Extract agent transcript delta for real-time streaming
                    if (eventListener != null && message.has("delta"))
                    {
                        String textDelta = message.get("delta").asText();
                        log.debug("Received agent transcript delta: {}", textDelta);
                        eventListener.onTextDelta(textDelta);
                        // Also log to TRACE for debugging
                        eventListener.onTraceMessage(messageText);
                    }
                    break;

                case "response.audio_transcript.done":
                    // Extract complete agent transcript
                    log.debug("Agent audio transcript completed");
                    if (eventListener != null && message.has("transcript"))
                    {
                        String transcript = message.get("transcript").asText();
                        eventListener.onAgentTranscript(transcript);
                    }
                    break;

                case "response.audio.done":
                    // Route audio response completion to DEBUG level
                    log.debug("Audio response completed");
                    if (eventListener != null)
                    {
                        eventListener.onRequestLog("API Response", "Audio response completed", "200");
                    }
                    break;

                case "conversation.item.input_audio_transcription.failed":
                    handleTranscriptionError(message);
                    break;

                case "response.function_call_delta":
                    // Handle function call deltas (streaming function calls)
                    log.debug("Received function call delta");
                    if (eventListener != null)
                    {
                        eventListener.onTraceMessage(messageText);
                    }
                    break;

                case "response.function_call.done":
                    // Function calls are handled natively by OpenAI
                    log.debug("Function call completed");
                    if (eventListener != null)
                    {
                        eventListener.onTraceMessage(messageText);
                    }
                    break;

                default:
                    log.debug("Unhandled message type: {}", type);
                    if (eventListener != null)
                    {
                        eventListener.onMessage(messageText);
                    }
                    break;
            }
        }
        catch (Exception e)
        {
            log.error("Failed to parse message: {}", messageText, e);
            if (eventListener != null)
            {
                eventListener.onError(e);
            }
        }
    }


    private void handleApiError(ObjectNode message)
    {
        String errorMessage = "Unknown error";
        String errorCode = null;

        if (message.has("error"))
        {
            ObjectNode error = (ObjectNode) message.get("error");
            if (error.has("message"))
            {
                errorMessage = error.get("message").asText();
            }
            if (error.has("code"))
            {
                errorCode = error.get("code").asText();
            }
        }

        log.error("OpenAI API error: {} (code: {})", errorMessage, errorCode);

        // Handle specific error types
        if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests"))
        {
            log.warn("Rate limit exceeded. Implementing exponential backoff.");
            increaseBackoff();

            // Only reset buffer if we have too many consecutive errors to prevent data loss
            if (consecutiveErrors > 3)
            {
                log.warn("Too many consecutive rate limit errors ({}), clearing buffer to recover",
                    consecutiveErrors);
                audioBufferSize.set(0);
            }
        }
        else
        {
            // For non-rate-limit errors, reset backoff
            resetBackoff();
        }

        if (eventListener != null)
        {
            eventListener.onError(new RuntimeException("OpenAI API error: " + errorMessage));
        }
    }

    private void handleTranscriptionError(ObjectNode message)
    {
        String itemId = message.has("item_id") ? message.get("item_id").asText() : "unknown";
        String errorMessage = "Transcription failed";

        if (message.has("error"))
        {
            ObjectNode error = (ObjectNode) message.get("error");
            if (error.has("message"))
            {
                errorMessage = error.get("message").asText();
            }
        }

        log.error("Audio transcription failed for item '{}': {}", itemId, errorMessage);

        // Handle specific transcription errors
        if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests"))
        {
            log.warn("Transcription rate limit exceeded. Implementing exponential backoff.");
            increaseBackoff();

            // Only reset buffer if we have too many consecutive errors to prevent data loss
            if (consecutiveErrors > 3)
            {
                log.warn("Too many consecutive transcription rate limit errors ({}), clearing buffer to recover",
                    consecutiveErrors);
                audioBufferSize.set(0);
            }
        }
        else
        {
            // For non-rate-limit transcription errors, reset backoff
            resetBackoff();
        }

        if (eventListener != null)
        {
            eventListener.onError(new RuntimeException("Audio transcription failed: " + errorMessage));
        }
    }

    public interface VoiceEventListener
    {
        void onConnected();

        void onDisconnected();

        void onMessage(String message);

        void onTraceMessage(String message);

        void onError(Throwable error);

        void onSpeechStarted();

        void onSpeechStopped();

        void onTextResponse(String text);

        void onTextDelta(String delta);

        void onUserSpeechEnded(String message);

        void onAudioResponse(byte[] audioData);

        void onResponseStarted();

        void onResponseComplete();

        void onUserTranscript(String transcript);

        void onAgentTranscript(String transcript);

        void onRequestLog(String requestType, String details, String responseCode);
    }
}
