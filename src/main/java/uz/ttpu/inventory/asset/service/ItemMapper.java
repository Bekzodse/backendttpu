package uz.ttpu.inventory.asset.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.ItemStateHistoryDto;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemStateHistory;

@Mapper(componentModel = "spring")
public interface ItemMapper {

    ItemDto toDto(Item item);

    ItemStateHistoryDto toHistoryDto(ItemStateHistory history);
}
