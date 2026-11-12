package com.smartpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayPal (Sandbox) REST API settings (paypal.* properties). When
 * credentials are absent, payment endpoints run in a clearly-labelled
 * SIMULATED mode.
 */
@ConfigurationProperties(prefix = "paypal")
public class PayPalProperties {

    private String baseUrl = "https://api-m.sandbox.paypal.com";
    private String clientId = "";
    private String clientSecret = "";
    private String returnUrl = "http://localhost:5173/";
    private String cancelUrl = "http://localhost:5173/";

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
