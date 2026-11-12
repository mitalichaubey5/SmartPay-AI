package com.smartpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMTP settings (email.* properties, sourced from SMTP_* environment
 * variables). When these are absent, invoice sending answers
 * NOT_CONFIGURED instead of pretending an email was delivered.
 */
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "";
    private boolean starttls = true;

    public boolean isConfigured() {
        return notBlank(host) && notBlank(username) && notBlank(password) && notBlank(from);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isStarttls() {
        return starttls;
    }

    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }
}
