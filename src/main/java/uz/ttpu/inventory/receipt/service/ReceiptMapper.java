package uz.ttpu.inventory.receipt.service;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.ttpu.inventory.receipt.api.ReceiptDto;
import uz.ttpu.inventory.receipt.api.ReceiptLineDto;
import uz.ttpu.inventory.receipt.domain.Receipt;
import uz.ttpu.inventory.receipt.domain.ReceiptLine;

@Mapper(componentModel = "spring")
public interface ReceiptMapper {

  ReceiptDto toDto(Receipt receipt);

  @Mapping(target = "receiptId", source = "receipt.id")
  ReceiptLineDto toLineDto(ReceiptLine line);
}
