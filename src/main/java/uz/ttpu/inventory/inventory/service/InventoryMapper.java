package uz.ttpu.inventory.inventory.service;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.ttpu.inventory.inventory.api.InventoryExpectedItemDto;
import uz.ttpu.inventory.inventory.api.InventorySessionDto;
import uz.ttpu.inventory.inventory.domain.InventoryExpectedItem;
import uz.ttpu.inventory.inventory.domain.InventorySession;

@Mapper
public interface InventoryMapper {

  InventorySessionDto toDto(InventorySession entity);

  @Mapping(target = "sessionId", source = "session.id")
  InventoryExpectedItemDto toExpectedItemDto(InventoryExpectedItem entity);
}
