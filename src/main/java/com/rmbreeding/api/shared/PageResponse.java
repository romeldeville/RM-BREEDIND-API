package com.rmbreeding.api.shared;

import java.util.List;

public record PageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> PageResponse<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return new PageResponse<>(List.copyOf(items), nextCursor, hasMore);
    }

    public static <T> PageResponse<T> last(List<T> items) {
        return new PageResponse<>(List.copyOf(items), null, false);
    }
}
