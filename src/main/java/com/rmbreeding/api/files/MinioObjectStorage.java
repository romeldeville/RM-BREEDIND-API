package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;

    public MinioObjectStorage(MinioClient client) {
        this.client = client;
    }

    @Override
    public String presignedPutUrl(String bucket, String key, Duration ttl) {
        return presign(Method.PUT, bucket, key, ttl);
    }

    @Override
    public String presignedGetUrl(String bucket, String key, Duration ttl) {
        return presign(Method.GET, bucket, key, ttl);
    }

    @Override
    public void putObject(String bucket, String key, byte[] content, String contentType) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(stream, content.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception ex) {
            throw new StorageException("Unable to store object " + key, ex);
        }
    }

    @Override
    public byte[] getObject(String bucket, String key) {
        try (var stream = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return stream.readAllBytes();
        } catch (Exception ex) {
            throw StorageException.map("Unable to read object " + key, ex);
        }
    }

    @Override
    public long size(String bucket, String key) {
        try {
            return client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()).size();
        } catch (Exception ex) {
            throw StorageException.map("Unable to stat object " + key, ex);
        }
    }

    @Override
    public void removeObject(String bucket, String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            throw new StorageException("Unable to remove object " + key, ex);
        }
    }

    @Override
    public void ensureBuckets(String... buckets) {
        for (String bucket : buckets) {
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            } catch (Exception ex) {
                throw new StorageException("Unable to initialise bucket " + bucket, ex);
            }
        }
    }

    private String presign(Method method, String bucket, String key, Duration ttl) {
        try {
            int expiry = (int) Math.max(1, Math.min(ttl.toSeconds(), TimeUnit.DAYS.toSeconds(7)));
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(key)
                    .expiry(expiry, TimeUnit.SECONDS)
                    .build());
        } catch (Exception ex) {
            throw new StorageException("Unable to sign " + method + " URL for " + key, ex);
        }
    }
}
