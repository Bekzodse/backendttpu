package uz.ttpu.inventory.asset.api;

import java.util.List;

public record AssetSearchResultDto(
    List<ItemDto> items,
    long totalElements,
    int totalPages,
    int page,
    int size,
    long queryTimeMs) {}
