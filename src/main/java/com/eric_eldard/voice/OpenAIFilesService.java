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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for analyzing images using OpenAI's Chat Completions API with gpt-4.1-mini
 */
@Slf4j
public class OpenAIFilesService
{
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4.1-mini";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    
    public OpenAIFilesService(String apiKey)
    {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Longer timeout for file uploads
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Analyzes multiple image files using OpenAI's Chat Completions API
     * @param files Array of image files to analyze
     * @return CompletableFuture that resolves to the API response as a string
     */
    public CompletableFuture<String> uploadFiles(File[] files)
    {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder responseBuilder = new StringBuilder();
            
            for (File file : files)
            {
                try
                {
                    String imageAnalysis = analyzeImage(file);
                    if (responseBuilder.length() > 0)
                    {
                        responseBuilder.append("\n\n");
                    }
                    responseBuilder.append("Analysis of ").append(file.getName()).append(":\n");
                    responseBuilder.append(imageAnalysis);
                }
                catch (IOException e)
                {
                    log.error("Error analyzing image: {}", file.getName(), e);
                    if (responseBuilder.length() > 0)
                    {
                        responseBuilder.append("\n\n");
                    }
                    responseBuilder.append("Error analyzing ").append(file.getName()).append(": ").append(e.getMessage());
                }
            }
            
            return responseBuilder.toString().trim();
        });
    }
    
    private String analyzeImage(File imageFile) throws IOException
    {
        // Convert image to base64
        byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        
        // Determine MIME type based on file extension
        String fileName = imageFile.getName().toLowerCase();
        String mimeType = "image/jpeg"; // default
        if (fileName.endsWith(".png")) {
            mimeType = "image/png";
        } else if (fileName.endsWith(".gif")) {
            mimeType = "image/gif";
        } else if (fileName.endsWith(".bmp")) {
            mimeType = "image/bmp";
        } else if (fileName.endsWith(".webp")) {
            mimeType = "image/webp";
        }
        
        // Create request body for Chat Completions API
        ObjectNode requestBody = createImageAnalysisRequest(base64Image, mimeType);
        String jsonRequest = objectMapper.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
            .url(OPENAI_API_URL)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(jsonRequest, JSON))
            .build();
        
        try (Response response = httpClient.newCall(request).execute())
        {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful())
            {
                log.error("OpenAI Chat Completions API call failed with status: {}, body: {}", 
                    response.code(), responseBody);
                return "Image analysis failed with status: " + response.code() + "\nError: " + responseBody;
            }
            
            // Parse the response to extract the image description
            try
            {
                JsonNode responseJson = objectMapper.readTree(responseBody);
                JsonNode choices = responseJson.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0)
                {
                    JsonNode firstChoice = choices.get(0);
                    JsonNode message = firstChoice.get("message");
                    if (message != null)
                    {
                        JsonNode content = message.get("content");
                        if (content != null)
                        {
                            String result = content.asText().trim();
                            log.debug("OpenAI image analysis result: {}", result);
                            return result;
                        }
                    }
                }
                
                log.warn("Unexpected response format from OpenAI API: {}", responseBody);
                return "Could not analyze image - unexpected response format";
            }
            catch (Exception e)
            {
                log.error("Error parsing OpenAI response", e);
                return "Error parsing image analysis response: " + e.getMessage();
            }
        }
    }
    
    private ObjectNode createImageAnalysisRequest(String base64Image, String mimeType)
    {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", MODEL);
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.1);
        
        ArrayNode messages = objectMapper.createArrayNode();
        
        // Add user message with image
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        
        ArrayNode content = objectMapper.createArrayNode();
        
        // Add text part
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("type", "text");
        textPart.put("text", "Please describe what you see in this image in detail.");
        content.add(textPart);
        
        // Add image part
        ObjectNode imagePart = objectMapper.createObjectNode();
        imagePart.put("type", "image_url");
        ObjectNode imageUrl = objectMapper.createObjectNode();
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);
        imagePart.set("image_url", imageUrl);
        content.add(imagePart);
        
        userMessage.set("content", content);
        messages.add(userMessage);
        
        requestBody.set("messages", messages);
        return requestBody;
    }
}