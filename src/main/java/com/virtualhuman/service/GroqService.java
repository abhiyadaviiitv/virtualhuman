package com.virtualhuman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private final WebClient webClient;
    private final String apiUrl;
    private final String apiKey;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GroqService.class);

    public GroqService(WebClient.Builder webClientBuilder,
            @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}") String apiUrl,
            @Value("${groq.api.key}") String apiKey) {
        this.webClient = webClientBuilder.build();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    @io.github.resilience4j.retry.annotation.Retry(name = "groq", fallbackMethod = "fallbackResponse")
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "groq", fallbackMethod = "fallbackResponse")
    public String sendPrompt(String systemMessage, String userPrompt, double temperature) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.3-70b-versatile");

        List<Map<String, String>> messages = new java.util.ArrayList<>();

        // Inject System Message if provided
        if (systemMessage != null && !systemMessage.isBlank()) {
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemMessage);
            messages.add(sysMsg);
        }

        // Inject User Prompt
        Map<String, String> usrMsg = new HashMap<>();
        usrMsg.put("role", "user");
        usrMsg.put("content", userPrompt);
        messages.add(usrMsg);

        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", 512); // JSON payloads are small
        body.put("response_format", Map.of("type", "json_object")); // Enforce JSON Output

        try {
            String response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                return root.get("choices").get(0).get("message").get("content").asText();
            }
            return response;
        } catch (Exception e) {
            log.error("Groq API Call Failed: {}", e.getMessage());
            throw new RuntimeException("API Call Failed", e); // Throw to trigger retry/circuitbreaker
        }
    }

    /**
     * Fallback method called when all retries fail, or circuit breaker is open.
     * Prevents thread blocking and fast-fails.
     */
    public String fallbackResponse(String systemMessage, String userPrompt, double temperature, Throwable t) {
        log.error("Circuit Breaker OPEN or Retries Exhausted. Triggering fallback for Groq API. Reason: {}",
                t.getMessage());
        return "{}"; // Send empty JSON to trigger the service's own parsable fallbacks
    }
}
