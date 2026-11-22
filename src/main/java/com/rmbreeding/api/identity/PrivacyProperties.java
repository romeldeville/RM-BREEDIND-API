package com.rmbreeding.api.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.privacy")
public class PrivacyProperties {

    private String consentVersion = "2026-11-01";
    private String termsVersion = "2026-11-01";
    private String privacyVersion = "2026-11-01";

    public String getConsentVersion() {
        return consentVersion;
    }

    public void setConsentVersion(String consentVersion) {
        this.consentVersion = consentVersion;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
        this.termsVersion = termsVersion;
    }

    public String getPrivacyVersion() {
        return privacyVersion;
    }

    public void setPrivacyVersion(String privacyVersion) {
        this.privacyVersion = privacyVersion;
    }
}
