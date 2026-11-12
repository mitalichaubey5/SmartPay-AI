package com.smartpay.integration.gemini;

import com.smartpay.config.GeminiProperties;
import com.smartpay.exception.IntegrationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for the Google Gemini generateContent API.
 * Returns the raw model text (expected to be JSON); parsing happens in
 * {@link GeminiInvoiceParser}.
 */
@Component
public class GeminiClient {

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Sends the prompt with a strict system instruction and returns the
     * model's JSON answer.
     */
    public String generateJson(String systemInstruction, String prompt) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.1));

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/models/" + properties.getModel() + ":generateContent"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (JacksonException e) {
            throw new IntegrationException("Could not serialize Gemini request", e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IntegrationException("Gemini API call failed: " + e.getMessage(), e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IntegrationException("Gemini API returned HTTP " + response.statusCode()
                    + " — check GEMINI_API_KEY and GEMINI_MODEL");
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new IntegrationException("Gemini returned an empty response");
            }
            return text;
        } catch (JacksonException e) {
            throw new IntegrationException("Gemini response was not valid JSON", e);
        }
    }
}
