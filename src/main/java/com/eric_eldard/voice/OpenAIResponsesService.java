package com.eric_eldard.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for calling OpenAI's Chat Completions API to determine if user requests require code responses
 */
@Slf4j
public class OpenAIResponsesService
{
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4.1-mini";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public OpenAIResponsesService(String apiKey)
    {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Represents a transcript message for the API call
     */
    public static class TranscriptMessage
    {
        public final String role; // "user" or "assistant"
        public final String content;

        public TranscriptMessage(String role, String content)
        {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * Analyzes transcript messages to determine if code should be produced
     *
     * @param transcriptMessages List of recent transcript messages
     * @return CompletableFuture that resolves to either "[non-code-request]" or code in markdown
     */
    public CompletableFuture<String> analyzeForCodeRequest(List<TranscriptMessage> transcriptMessages)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try
            {
                ObjectNode requestBody = createRequestBody(transcriptMessages);
                String jsonRequest = objectMapper.writeValueAsString(requestBody);

                Request request = new Request.Builder()
                    .url(OPENAI_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(jsonRequest, JSON))
                    .build();

                try (Response response = httpClient.newCall(request).execute())
                {
                    if (!response.isSuccessful())
                    {
                        log.error("OpenAI API call failed with status: {}, body: {}",
                            response.code(), response.body() != null ? response.body().string() : "null");
                        return "[non-code-request]"; // Default to non-code on error
                    }

                    String responseBody = response.body().string();
                    JsonNode responseJson = objectMapper.readTree(responseBody);

                    JsonNode choices = responseJson.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0)
                    {
                        JsonNode firstChoice = choices.get(0);
                        JsonNode message = firstChoice.get("message");
                        if (message != null)
                        {
                            // OpenAI handles web search natively, no custom function handling needed

                            JsonNode content = message.get("content");
                            if (content != null)
                            {
                                String result = content.asText().trim();
                                log.debug("OpenAI Responses API result: {}", result);
                                return result;
                            }
                        }
                    }

                    log.warn("Unexpected response format from OpenAI API: {}", responseBody);
                    return "[non-code-request]"; // Default to non-code on unexpected format
                }
            }
            catch (IOException e)
            {
                log.error("Error calling OpenAI Responses API", e);
                return "[non-code-request]"; // Default to non-code on error
            }
        });
    }

    private ObjectNode createRequestBody(List<TranscriptMessage> transcriptMessages)
    {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", MODEL);
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.1);


        ArrayNode messages = objectMapper.createArrayNode();

        // Add system message with instructions
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");

        String systemContent = """
            You are analyzing a conversation between a user and a voice assistant.
            Your task is to determine if the user's latest request indicates they want code to be produced.
            
            If the user's latest request EXPLICITLY indicates they want code, respond only with an appropriate code snippet in markdown format.
            
            If the user's latest request does NOT EXPLICITLY indicate they want code, respond with exactly: [non-code-request]
            
            ALWAYS respond with an appropriate code snippet in markdown format OR with exactly: [non-code-request]
            
            Examples of requests that indicate code is wanted:
            - 'Can you write a function to...'
            - 'Show me how to implement...'
            - 'Create a class that...'
            - 'Generate code for...'
            
            Examples of requests that do NOT indicate code is wanted:
            - General questions about concepts
            - Requests for explanations
            - Casual conversation
            - Questions about how something works conceptually
            """;

        systemMessage.put("content", systemContent);
        messages.add(systemMessage);

        // Add transcript messages
        for (TranscriptMessage msg : transcriptMessages)
        {
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", msg.role);
            messageNode.put("content", msg.content);
            messages.add(messageNode);
        }

        requestBody.set("messages", messages);
        return requestBody;
    }
}