package com.rmbreeding.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.features")
public class FeatureProperties {

    private boolean sync = true;
    private boolean billing = true;
    private boolean platformAdmin = true;

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public boolean isBilling() {
        return billing;
    }

    public void setBilling(boolean billing) {
        this.billing = billing;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public void setPlatformAdmin(boolean platformAdmin) {
        this.platformAdmin = platformAdmin;
    }
}
