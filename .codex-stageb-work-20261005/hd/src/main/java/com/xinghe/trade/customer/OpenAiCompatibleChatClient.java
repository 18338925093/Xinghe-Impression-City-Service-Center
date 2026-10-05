package com.xinghe.trade.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAiCompatibleChatClient implements CustomerAgentClient {
    private final CustomerAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleChatClient(CustomerAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public JsonNode complete(ObjectNode requestBody) {
        // 调用 OpenAI-compatible 聊天接口；网络、超时或非 2xx 响应交由上层降级处理。
        if (!StringUtils.hasText(properties.getBaseUrl())) throw new IllegalStateException("Agent base URL is not configured");
        String baseUrl = properties.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(requestBody);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not encode Agent request", ex);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(properties.getTimeout())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Agent provider returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent request was interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Agent request failed", ex);
        }
    }
}
