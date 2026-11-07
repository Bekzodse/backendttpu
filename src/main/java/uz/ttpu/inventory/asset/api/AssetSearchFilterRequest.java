package uz.ttpu.inventory.asset.api;

import java.util.UUID;
import uz.ttpu.inventory.asset.domain.ItemState;

public record AssetSearchFilterRequest(
    String query,
    String name,
    String inventoryNumber,
    String serial,
    String model,
    String responsiblePerson,
    String room,
    ItemState state,
    UUID categoryId,
    UUID departmentId,
    UUID buildingId,
    UUID roomId,
    UUID custodianEmployeeId,
    boolean fuzzySimilarity) {}
