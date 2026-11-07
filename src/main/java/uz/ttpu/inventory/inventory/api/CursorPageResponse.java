package uz.ttpu.inventory.inventory.api;

import java.util.List;

public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasMore, int limit) {}
