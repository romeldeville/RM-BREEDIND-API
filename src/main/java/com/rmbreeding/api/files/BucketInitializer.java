package com.rmbreeding.api.files;

import com.rmbreeding.api.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the private photo/document bucket and the export bucket at startup.
 * A storage outage must not prevent the API from starting.
 */
@Component
public class BucketInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BucketInitializer.class);

    private final ObjectStorage storage;
    private final StorageProperties properties;

    public BucketInitializer(ObjectStorage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            storage.ensureBuckets(properties.getBucket(), properties.getExportBucket());
            LOG.info("Storage buckets ready: {}, {}", properties.getBucket(), properties.getExportBucket());
        } catch (RuntimeException ex) {
            LOG.warn("Storage buckets could not be initialised: {}", ex.getMessage());
        }
    }
}
