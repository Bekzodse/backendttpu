package uz.ttpu.inventory.repair.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.repair.api.RepairTicketDto;
import uz.ttpu.inventory.repair.domain.RepairTicket;

@Mapper(componentModel = "spring")
public interface RepairMapper {

  RepairTicketDto toDto(RepairTicket ticket);
}
