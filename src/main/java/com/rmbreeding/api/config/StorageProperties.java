package com.rmbreeding.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.storage")
public class StorageProperties {

    private String endpoint = "http://localhost:9000";
    private String accessKey;
    private String secretKey;
    private String bucket = "rm-breeding";
    private String exportBucket = "rm-breeding-exports";

    /** Public base URL used to build share links (no trailing slash). */
    private String publicBaseUrl = "http://localhost:8081";

    /** HMAC secret used to sign share tokens. */
    private String shareSecret = "local-dev-share-secret-change-me-0123456789abcdef";

    /** TTL of presigned upload URLs. */
    private Duration uploadUrlTtl = Duration.ofMinutes(15);

    /** TTL of presigned download URLs. */
    private Duration downloadUrlTtl = Duration.ofMinutes(5);

    /** Maximum accepted upload size. */
    private long maxUploadBytes = 15L * 1024 * 1024;

    /** Age after which an unconfirmed upload is considered orphaned. */
    private Duration orphanRetention = Duration.ofHours(24);

    /** Age after which a soft-deleted attachment is purged from storage. */
    private Duration deletedRetention = Duration.ofDays(7);

    /** Default lifetime of a share link. */
    private Duration shareTtl = Duration.ofDays(7);

    /** Maximum lifetime of a share link. */
    private Duration shareMaxTtl = Duration.ofDays(30);

    /** Lifetime of a presigned download URL produced by an export job. */
    private Duration exportUrlTtl = Duration.ofMinutes(15);

    /** Prefix allowed for public share URLs. */
    private String sharePath = "/api/v1/public/shares";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getExportBucket() {
        return exportBucket;
    }

    public void setExportBucket(String exportBucket) {
        this.exportBucket = exportBucket;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getShareSecret() {
        return shareSecret;
    }

    public void setShareSecret(String shareSecret) {
        this.shareSecret = shareSecret;
    }

    public Duration getUploadUrlTtl() {
        return uploadUrlTtl;
    }

    public void setUploadUrlTtl(Duration uploadUrlTtl) {
        this.uploadUrlTtl = uploadUrlTtl;
    }

    public Duration getDownloadUrlTtl() {
        return downloadUrlTtl;
    }

    public void setDownloadUrlTtl(Duration downloadUrlTtl) {
        this.downloadUrlTtl = downloadUrlTtl;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
    }

    public Duration getOrphanRetention() {
        return orphanRetention;
    }

    public void setOrphanRetention(Duration orphanRetention) {
        this.orphanRetention = orphanRetention;
    }

    public Duration getDeletedRetention() {
        return deletedRetention;
    }

    public void setDeletedRetention(Duration deletedRetention) {
        this.deletedRetention = deletedRetention;
    }

    public Duration getShareTtl() {
        return shareTtl;
    }

    public void setShareTtl(Duration shareTtl) {
        this.shareTtl = shareTtl;
    }

    public Duration getShareMaxTtl() {
        return shareMaxTtl;
    }

    public void setShareMaxTtl(Duration shareMaxTtl) {
        this.shareMaxTtl = shareMaxTtl;
    }

    public Duration getExportUrlTtl() {
        return exportUrlTtl;
    }

    public void setExportUrlTtl(Duration exportUrlTtl) {
        this.exportUrlTtl = exportUrlTtl;
    }

    public String getSharePath() {
        return sharePath;
    }

    public void setSharePath(String sharePath) {
        this.sharePath = sharePath;
    }
}
