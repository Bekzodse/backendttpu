package uz.ttpu.inventory.receipt.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.receipt.api.CreateReceiptRequest;
import uz.ttpu.inventory.receipt.api.ReceiptApprovedEvent;
import uz.ttpu.inventory.receipt.api.ReceiptDto;
import uz.ttpu.inventory.receipt.api.ReceiptLineRequest;
import uz.ttpu.inventory.receipt.domain.DuplicateSerialNumberException;
import uz.ttpu.inventory.receipt.domain.Receipt;
import uz.ttpu.inventory.receipt.domain.ReceiptLine;
import uz.ttpu.inventory.receipt.domain.ReceiptStatus;
import uz.ttpu.inventory.receipt.repository.ReceiptLineRepository;
import uz.ttpu.inventory.receipt.repository.ReceiptRepository;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptLineRepository receiptLineRepository;
    private final ReceiptMapper receiptMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<ReceiptDto> listReceipts() {
        return receiptRepository.findAll().stream().map(receiptMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ReceiptDto getReceipt(UUID id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", id));
        return receiptMapper.toDto(receipt);
    }

    @Transactional
    public ReceiptDto createReceipt(CreateReceiptRequest request) {
        validateSerialNumbersUniqueWithinCategory(request.lines());

        Receipt receipt = Receipt.builder()
                .receiptNumber(request.receiptNumber())
                .contractNumber(request.contractNumber())
                .contractDate(request.contractDate())
                .invoiceNumber(request.invoiceNumber())
                .invoiceDate(request.invoiceDate())
                .supplierName(request.supplierName())
                .supplierTin(request.supplierTin())
                .status(ReceiptStatus.DRAFT)
                .receivedAt(request.receivedAt())
                .warehouseBuildingId(request.warehouseBuildingId())
                .receivedByEmployeeId(request.receivedByEmployeeId())
                .notes(request.notes())
                .build();

        for (ReceiptLineRequest lineRequest : request.lines()) {
            ReceiptLine line = ReceiptLine.builder()
                    .name(lineRequest.name())
                    .categoryId(lineRequest.categoryId())
                    .serialNumber(lineRequest.serialNumber().trim())
                    .priceWithoutVat(lineRequest.priceWithoutVat())
                    .quantity(lineRequest.quantity() != null ? lineRequest.quantity() : 1)
                    .build();
            receipt.addLine(line);
        }

        return receiptMapper.toDto(receiptRepository.save(receipt));
    }

    @Transactional
    public ReceiptDto approveReceipt(UUID id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", id));

        if (receipt.getStatus() != ReceiptStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT receipts can be approved. Current status: " + receipt.getStatus());
        }

        Instant now = Instant.now();
        receipt.setStatus(ReceiptStatus.CONFIRMED);
        Receipt savedReceipt = receiptRepository.save(receipt);

        List<ReceiptApprovedEvent.ApprovedReceiptLineItem> lineItems = savedReceipt.getLines().stream()
                .map(line -> new ReceiptApprovedEvent.ApprovedReceiptLineItem(
                        line.getId(),
                        line.getName(),
                        line.getCategoryId(),
                        line.getSerialNumber(),
                        line.getPriceWithoutVat(),
                        line.getQuantity() != null ? line.getQuantity() : 1
                ))
                .toList();

        eventPublisher.publishEvent(new ReceiptApprovedEvent(
                savedReceipt.getId(),
                savedReceipt.getReceiptNumber(),
                savedReceipt.getWarehouseBuildingId(),
                now,
                lineItems
        ));

        return receiptMapper.toDto(savedReceipt);
    }

    private void validateSerialNumbersUniqueWithinCategory(List<ReceiptLineRequest> lines) {
        Set<String> seenCategorySerialPairs = new HashSet<>();
        for (ReceiptLineRequest line : lines) {
            String normalizedSerial = line.serialNumber().trim().toUpperCase(Locale.ROOT);
            String key = line.categoryId() + ":" + normalizedSerial;

            if (!seenCategorySerialPairs.add(key)) {
                throw new DuplicateSerialNumberException(line.categoryId(), line.serialNumber().trim());
            }

            if (receiptLineRepository.existsByCategoryIdAndSerialNumberIgnoreCase(
                    line.categoryId(),
                    line.serialNumber().trim()
            )) {
                throw new DuplicateSerialNumberException(line.categoryId(), line.serialNumber().trim());
            }
        }
    }
}
