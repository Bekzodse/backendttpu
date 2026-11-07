package uz.ttpu.inventory.integration.service;

import org.mapstruct.Mapper;
import uz.ttpu.inventory.integration.api.EImzoSignatureRecordDto;
import uz.ttpu.inventory.integration.api.OutboxMessageDto;
import uz.ttpu.inventory.integration.domain.EImzoSignatureRecord;
import uz.ttpu.inventory.integration.domain.OutboxMessage;

@Mapper
public interface IntegrationMapper {

  OutboxMessageDto toDto(OutboxMessage entity);

  EImzoSignatureRecordDto toEImzoDto(EImzoSignatureRecord entity);
}
