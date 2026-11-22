package com.rmbreeding.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private long capacity = 120;
    private long refillTokens = 120;
    private Duration refillPeriod = Duration.ofMinutes(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(long refillTokens) {
        this.refillTokens = refillTokens;
    }

    public Duration getRefillPeriod() {
        return refillPeriod;
    }

    public void setRefillPeriod(Duration refillPeriod) {
        this.refillPeriod = refillPeriod;
    }
}
