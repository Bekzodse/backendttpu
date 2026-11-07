package uz.ttpu.inventory.movement.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.movement.api.MovementRecordDto;
import uz.ttpu.inventory.movement.domain.MovementRecord;

@Mapper(componentModel = "spring")
public interface MovementMapper {

    MovementRecordDto toDto(MovementRecord record);
}
