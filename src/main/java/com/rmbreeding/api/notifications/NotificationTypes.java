package com.rmbreeding.api.notifications;

import java.util.LinkedHashSet;
import java.util.Set;

public final class NotificationTypes {

    public static final String CHANNEL_PUSH = "PUSH";
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String DAILY_DIGEST = "DAILY_DIGEST";

    public static final Set<String> CHANNELS = Set.of(CHANNEL_PUSH, CHANNEL_EMAIL);

    public static final Set<String> ALERT_TYPES = Set.of(
            "PREGNANCY_DIAGNOSIS_DUE",
            "FARROWING_DUE",
            "WEANING_DUE",
            "HEAT_RETURN",
            "WITHDRAWAL_START",
            "WITHDRAWAL_END",
            "QUARANTINE_END",
            "WEIGHING_DUE",
            "OVERCROWDING",
            "PAYMENT_OVERDUE",
            "LOW_STOCK",
            "FEED_EXPIRY",
            "LAYING_DROP");

    /** Types exposed in the preference matrix, including the daily digest. */
    public static final Set<String> TYPES;

    static {
        Set<String> types = new LinkedHashSet<>(ALERT_TYPES);
        types.add(DAILY_DIGEST);
        TYPES = Set.copyOf(types);
    }

    private NotificationTypes() {
    }

    public static boolean isKnownType(String type) {
        return type != null && TYPES.contains(type);
    }

    public static boolean isKnownChannel(String channel) {
        return channel != null && CHANNELS.contains(channel);
    }
}
