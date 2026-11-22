package com.rmbreeding.api.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.notifications")
public class NotificationProperties {

    private final Push push = new Push();
    private final Email email = new Email();
    private final Digest digest = new Digest();

    public Push getPush() {
        return push;
    }

    public Email getEmail() {
        return email;
    }

    public Digest getDigest() {
        return digest;
    }

    public static class Push {

        private boolean enabled;
        private String projectId;
        private String serviceAccountJson;
        private String tokenUri = "https://oauth2.googleapis.com/token";
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getServiceAccountJson() {
            return serviceAccountJson;
        }

        public void setServiceAccountJson(String serviceAccountJson) {
            this.serviceAccountJson = serviceAccountJson;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }
    }

    public static class Email {

        private boolean enabled;
        private String host;
        private int port = 587;
        private String username;
        private String password;
        private String from = "no-reply@rmbreeding.local";
        private boolean starttls = true;
        private boolean ssl;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }
    }

    public static class Digest {

        private boolean enabled = true;
        private String cron = "0 0 7 * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }
    }
}
