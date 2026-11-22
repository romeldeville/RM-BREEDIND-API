package com.rmbreeding.api.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttachmentCleanupScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AttachmentCleanupScheduler.class);

    private final AttachmentCleanupService cleanupService;

    public AttachmentCleanupScheduler(AttachmentCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void purgeOrphans() {
        int purged = cleanupService.cleanupAllTenants();
        if (purged > 0) {
            LOG.info("Purged {} orphaned attachments", purged);
        }
    }
}
