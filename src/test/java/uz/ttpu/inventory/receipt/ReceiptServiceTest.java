package uz.ttpu.inventory.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
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
import uz.ttpu.inventory.receipt.service.ReceiptMapper;
import uz.ttpu.inventory.receipt.service.ReceiptService;

class ReceiptServiceTest {

    private ReceiptRepository receiptRepository;
    private ReceiptLineRepository receiptLineRepository;
    private ApplicationEventPublisher eventPublisher;
    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptRepository = mock(ReceiptRepository.class);
        receiptLineRepository = mock(ReceiptLineRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        ReceiptMapper mapper = Mappers.getMapper(ReceiptMapper.class);

        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> {
            Receipt saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            for (ReceiptLine line : saved.getLines()) {
                if (line.getId() == null) {
                    line.setId(UUID.randomUUID());
                }
            }
            return saved;
        });

        receiptService = new ReceiptService(receiptRepository, receiptLineRepository, mapper, eventPublisher);
    }

    @Test
    void createsReceiptWithLinesAndAllowsSameSerialInDifferentCategory() {
        UUID laptopCategoryId = UUID.randomUUID();
        UUID monitorCategoryId = UUID.randomUUID();

        CreateReceiptRequest request = new CreateReceiptRequest(
                "REC-2026-001",
                "CTR-2026-100",
                Instant.now(),
                "INV-2026-500",
                Instant.now(),
                "TechSupply LLC",
                "123456789",
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Initial warehouse delivery",
                List.of(
                        new ReceiptLineRequest("MacBook Pro 16", laptopCategoryId, "SN-SHARED-001", new BigDecimal("28500000.00"), 1),
                        new ReceiptLineRequest("Dell UltraSharp 27", monitorCategoryId, "SN-SHARED-001", new BigDecimal("6200000.00"), 1)
                )
        );

        ReceiptDto result = receiptService.createReceipt(request);

        assertThat(result.id()).isNotNull();
        assertThat(result.receiptNumber()).isEqualTo("REC-2026-001");
        assertThat(result.contractNumber()).isEqualTo("CTR-2026-100");
        assertThat(result.invoiceNumber()).isEqualTo("INV-2026-500");
        assertThat(result.supplierName()).isEqualTo("TechSupply LLC");
        assertThat(result.status()).isEqualTo(ReceiptStatus.DRAFT);
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(0).priceWithoutVat()).isEqualByComparingTo("28500000.00");
    }

    @Test
    void approveReceiptTransitionsStatusToConfirmedAndPublishesReceiptApprovedEvent() {
        UUID receiptId = UUID.randomUUID();
        Receipt receipt = Receipt.builder()
                .receiptNumber("REC-2026-010")
                .contractNumber("CTR-10")
                .invoiceNumber("INV-10")
                .supplierName("Vendor")
                .status(ReceiptStatus.DRAFT)
                .receivedAt(Instant.now())
                .build();
        receipt.setId(receiptId);
        receipt.addLine(ReceiptLine.builder()
                .name("HP LaserJet")
                .categoryId(UUID.randomUUID())
                .serialNumber("SN-HP-100")
                .priceWithoutVat(new BigDecimal("4500000.00"))
                .quantity(1)
                .build());

        when(receiptRepository.findById(receiptId)).thenReturn(Optional.of(receipt));

        ReceiptDto approved = receiptService.approveReceipt(receiptId);

        assertThat(approved.status()).isEqualTo(ReceiptStatus.CONFIRMED);
        ArgumentCaptor<ReceiptApprovedEvent> eventCaptor = ArgumentCaptor.forClass(ReceiptApprovedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().receiptNumber()).isEqualTo("REC-2026-010");
        assertThat(eventCaptor.getValue().lines()).hasSize(1);
    }

    @Test
    void rejectsDuplicateSerialNumberWithinSameCategoryInSamePayload() {
        UUID laptopCategoryId = UUID.randomUUID();

        CreateReceiptRequest request = new CreateReceiptRequest(
                "REC-2026-002",
                "CTR-2026-101",
                Instant.now(),
                "INV-2026-501",
                Instant.now(),
                "TechSupply LLC",
                "123456789",
                Instant.now(),
                null,
                null,
                null,
                List.of(
                        new ReceiptLineRequest("ThinkPad X1", laptopCategoryId, "SN-DUP-999", new BigDecimal("19000000.00"), 1),
                        new ReceiptLineRequest("ThinkPad X1 Gen2", laptopCategoryId, "sn-dup-999", new BigDecimal("21000000.00"), 1)
                )
        );

        assertThatThrownBy(() -> receiptService.createReceipt(request))
                .isInstanceOf(DuplicateSerialNumberException.class)
                .hasMessageContaining("sn-dup-999");

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void rejectsSerialNumberAlreadyExistingInDatabaseForSameCategory() {
        UUID laptopCategoryId = UUID.randomUUID();
        when(receiptLineRepository.existsByCategoryIdAndSerialNumberIgnoreCase(laptopCategoryId, "SN-EXISTING-1"))
                .thenReturn(true);

        CreateReceiptRequest request = new CreateReceiptRequest(
                "REC-2026-003",
                "CTR-2026-102",
                Instant.now(),
                "INV-2026-502",
                Instant.now(),
                "TechSupply LLC",
                "123456789",
                Instant.now(),
                null,
                null,
                null,
                List.of(
                        new ReceiptLineRequest("MacBook Air", laptopCategoryId, "SN-EXISTING-1", new BigDecimal("14000000.00"), 1)
                )
        );

        assertThatThrownBy(() -> receiptService.createReceipt(request))
                .isInstanceOf(DuplicateSerialNumberException.class)
                .hasMessageContaining("SN-EXISTING-1");

        verify(receiptRepository, never()).save(any());
    }
}
