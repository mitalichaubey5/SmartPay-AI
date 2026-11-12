package com.smartpay.integration.paypal;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.smartpay.config.PayPalProperties;
import com.smartpay.exception.BadRequestException;
import com.smartpay.exception.IntegrationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * PayPal REST (Sandbox) client: OAuth2 client-credentials token with
 * caching, Orders v2 create + capture. Throwing {@link BadRequestException}
 * for buyer-action-required cases keeps the API semantics honest.
 */
@Component
public class PayPalClient {

    private final PayPalProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public PayPalClient(PayPalProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    // ----------------------------------------------------------------- orders

    /**
     * Creates a CAPTURE-intent order for an invoice. The approval link
     * returned by PayPal is what the buyer opens; after approving they are
     * redirected to the configured return URL.
     */
    public PayPalOrder createOrder(String referenceId, String description,
                                   BigDecimal amount, String currency) {
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "reference_id", referenceId,
                        "description", clip(description, 127),
                        "amount", Map.of("currency_code", currency, "value", amount.toPlainString()))),
                "application_context", Map.of(
                        "brand_name", "SmartPay AI",
                        "shipping_preference", "NO_SHIPPING",
                        "return_url", properties.getReturnUrl(),
                        "cancel_url", properties.getCancelUrl()));

        JsonNode root = exchange(
                "POST",
                properties.getBaseUrl() + "/v2/checkout/orders",
                body,
                true);

        String orderId = root.path("id").asText("");
        String approveUrl = "";
        for (JsonNode link : root.path("links")) {
            if ("approve".equals(link.path("rel").asText())) {
                approveUrl = link.path("href").asText();
            }
        }
        if (orderId.isBlank()) {
            throw new IntegrationException("PayPal did not return an order id");
        }
        return new PayPalOrder(orderId, approveUrl);
    }

    /**
     * Captures an approved order.
     */
    public CaptureResult captureOrder(String orderId) {
        JsonNode root = exchange(
                "POST",
                properties.getBaseUrl() + "/v2/checkout/orders/" + orderId + "/capture",
                Map.of(),
                true);

        String status = root.path("status").asText("UNKNOWN");
        JsonNode payer = root.path("payer");
        String payerEmail = payer.path("email_address").asText("");
        String payerName = (payer.path("name").path("given_name").asText("") + " "
                + payer.path("name").path("surname").asText("")).trim();

        JsonNode capture = root.path("purchase_units").path(0)
                .path("payments").path("captures").path(0);
        String captureId = capture.path("id").asText("");

        if (!"COMPLETED".equals(status)) {
            String reason = root.path("details").path(0).path("issue").asText(status);
            if (reason.contains("ORDER_NOT_APPROVED")) {
                throw new BadRequestException(
                        "The buyer has not approved this payment yet — open the approval link first, then capture.");
            }
            throw new IntegrationException("PayPal capture returned status " + status + " (" + reason + ")");
        }
        return new CaptureResult(status, captureId, payerEmail, payerName);
    }

    // ------------------------------------------------------------------- auth

    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        String credentials = properties.getClientId() + ":" + properties.getClientSecret();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/v1/oauth2/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        JsonNode root = send(request);
        long expiresIn = root.path("expires_in").asLong(0);
        if (expiresIn <= 0) {
            throw new IntegrationException("PayPal OAuth did not return expires_in — check PAYPAL_CLIENT_ID/SECRET");
        }
        cachedToken = root.path("access_token").asText("");
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
        return cachedToken;
    }

    // ------------------------------------------------------------------ http

    private JsonNode exchange(String method, String url, Map<String, Object> body, boolean withAuth) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json");
            if (withAuth) {
                builder.header("Authorization", "Bearer " + accessToken());
            }
            String payload = objectMapper.writeValueAsString(body);
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload));
            return send(builder.build());
        } catch (JacksonException e) {
            throw new IntegrationException("Could not serialize PayPal request", e);
        }
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IntegrationException("PayPal API unreachable: " + e.getMessage(), e);
        }
        try {
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String issue = root.path("details").path(0).path("issue").asText("");
                String message = root.path("message").asText("no message");
                throw new IntegrationException(
                        "PayPal API HTTP " + response.statusCode()
                                + (issue.isBlank() ? "" : " (" + issue + ")")
                                + ": " + message);
            }
            return root;
        } catch (JacksonException e) {
            throw new IntegrationException("PayPal response was not valid JSON", e);
        }
    }

    private String clip(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
