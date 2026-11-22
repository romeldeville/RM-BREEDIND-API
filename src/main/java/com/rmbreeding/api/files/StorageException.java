package com.rmbreeding.api.files;

public class StorageException extends RuntimeException {

    private final boolean notFound;

    public StorageException(String message, Throwable cause) {
        super(message, cause);
        this.notFound = false;
    }

    private StorageException(String message, boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    public static StorageException notFound(String message) {
        return new StorageException(message, true);
    }

    public static StorageException map(String message, Exception cause) {
        String text = cause.getMessage() == null ? "" : cause.getMessage();
        if (text.contains("NoSuchKey") || text.contains("NoSuchObject") || text.contains("not found")) {
            return notFound(message);
        }
        return new StorageException(message, cause);
    }

    public boolean isNotFound() {
        return notFound;
    }
}
