package uz.ttpu.inventory.audit.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.audit.api.AuditRecordDto;
import uz.ttpu.inventory.audit.domain.AuditRecord;

@Mapper(componentModel = "spring")
public interface AuditMapper {

    AuditRecordDto toDto(AuditRecord record);
}
