package com.rmbreeding.api.files;

import java.time.Duration;

/**
 * Minimal storage abstraction so the file features can be unit tested without MinIO.
 */
public interface ObjectStorage {

    String presignedPutUrl(String bucket, String key, Duration ttl);

    String presignedGetUrl(String bucket, String key, Duration ttl);

    void putObject(String bucket, String key, byte[] content, String contentType);

    byte[] getObject(String bucket, String key);

    long size(String bucket, String key);

    void removeObject(String bucket, String key);

    void ensureBuckets(String... buckets);
}
