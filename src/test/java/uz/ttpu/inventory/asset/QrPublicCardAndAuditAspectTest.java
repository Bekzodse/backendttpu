package uz.ttpu.inventory.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import uz.ttpu.inventory.asset.api.CreateItemRequest;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.api.PublicItemCardDto;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.asset.service.AssetService;
import uz.ttpu.inventory.asset.service.ItemMapper;
import uz.ttpu.inventory.asset.service.ItemStateService;
import uz.ttpu.inventory.asset.service.QrIdService;
import uz.ttpu.inventory.audit.domain.AuditRecord;
import uz.ttpu.inventory.audit.repository.AuditRecordRepository;
import uz.ttpu.inventory.audit.service.AuditMapper;
import uz.ttpu.inventory.audit.service.AuditService;
import uz.ttpu.inventory.audit.service.ItemCardAuditAspect;
import uz.ttpu.inventory.common.domain.AuditActionType;
import uz.ttpu.inventory.receipt.api.ReceiptApprovedEvent;

class QrPublicCardAndAuditAspectTest {

    @Test
    void qrIdServiceGeneratesTenCharBase32AndReceiptApprovalCreatesItemsWithPublicCardAndAuditLogs() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        ItemStateHistoryRepository historyRepository = mock(ItemStateHistoryRepository.class);
        ItemStateService itemStateService = mock(ItemStateService.class);
        ItemMapper itemMapper = Mappers.getMapper(ItemMapper.class);

        when(itemRepository.existsByQrCode(any())).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        QrIdService qrIdService = new QrIdService(itemRepository);

        // 1. Verify 10-character Base32 non-guessable ID generation
        String generatedQr = qrIdService.generateUniquePublicId();
        assertThat(generatedQr).hasSize(10).matches("^[A-Z2-7]{10}$");

        // 2. Wire AssetService with ItemCardAuditAspect via AspectJProxyFactory
        AssetService rawAssetService = new AssetService(
                itemRepository,
                historyRepository,
                itemStateService,
                qrIdService,
                itemMapper
        );

        AuditRecordRepository auditRecordRepository = mock(AuditRecordRepository.class);
        when(auditRecordRepository.save(any(AuditRecord.class))).thenAnswer(inv -> {
            AuditRecord rec = inv.getArgument(0);
            rec.setId(UUID.randomUUID());
            return rec;
        });
        when(auditRecordRepository.saveAndFlush(any(AuditRecord.class))).thenAnswer(inv -> {
            AuditRecord rec = inv.getArgument(0);
            rec.setId(UUID.randomUUID());
            return rec;
        });
        AuditService auditService = new AuditService(auditRecordRepository, Mappers.getMapper(AuditMapper.class));
        ItemCardAuditAspect auditAspect = new ItemCardAuditAspect(auditService);

        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(rawAssetService);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(auditAspect);
        AssetService proxiedAssetService = proxyFactory.getProxy();

        // 3. Receipt approval generates items with 10-char Base32 QR IDs and logs CHANGE audit record
        ReceiptApprovedEvent approvedEvent = new ReceiptApprovedEvent(
                UUID.randomUUID(),
                "REC-2026-777",
                UUID.randomUUID(),
                Instant.now(),
                List.of(new ReceiptApprovedEvent.ApprovedReceiptLineItem(
                        UUID.randomUUID(),
                        "Cisco Catalyst Switch",
                        UUID.randomUUID(),
                        "SN-CISCO-001",
                        new BigDecimal("18000000.00"),
                        1
                ))
        );

        List<ItemDto> createdOnApproval = proxiedAssetService.onReceiptApproved(approvedEvent);
        assertThat(createdOnApproval).hasSize(1);
        ItemDto createdItem = createdOnApproval.get(0);
        assertThat(createdItem.qrCode()).hasSize(10).matches("^[A-Z2-7]{10}$");
        assertThat(createdItem.state()).isEqualTo(ItemState.OMBORDA);

        // 4. Public item card endpoint returns name, inventory number, "TTPU mulki", and contact phone, and logs READ
        Item persistedItem = Item.builder()
                .inventoryNumber(createdItem.inventoryNumber())
                .qrCode(createdItem.qrCode())
                .name("Cisco Catalyst Switch")
                .categoryId(createdItem.categoryId())
                .purchasePrice(createdItem.purchasePrice())
                .residualValue(createdItem.residualValue())
                .build();
        persistedItem.setId(createdItem.id());

        when(itemRepository.findByQrCode(createdItem.qrCode())).thenReturn(Optional.of(persistedItem));
        when(itemRepository.findById(createdItem.id())).thenReturn(Optional.of(persistedItem));

        PublicItemCardDto publicCard = proxiedAssetService.getPublicItemCard(createdItem.qrCode());
        assertThat(publicCard.qrId()).isEqualTo(createdItem.qrCode());
        assertThat(publicCard.name()).isEqualTo("Cisco Catalyst Switch");
        assertThat(publicCard.inventoryNumber()).isEqualTo(createdItem.inventoryNumber());
        assertThat(publicCard.ownershipLabel()).isEqualTo("TTPU mulki");
        assertThat(publicCard.contactPhone()).isNotBlank();

        // 5. Export item card logs EXPORT audit record
        ItemDto exported = proxiedAssetService.exportItemCard(createdItem.id());
        assertThat(exported.id()).isEqualTo(createdItem.id());

        // 6. Verify all 3 audit actions (CHANGE, READ, EXPORT) were recorded in audit_record
        ArgumentCaptor<AuditRecord> auditCaptor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordRepository, times(3)).saveAndFlush(auditCaptor.capture());

        List<AuditRecord> recordedAudits = auditCaptor.getAllValues();
        assertThat(recordedAudits)
                .extracting(AuditRecord::getActionType)
                .containsExactly(AuditActionType.CHANGE, AuditActionType.READ, AuditActionType.EXPORT);
        assertThat(recordedAudits.get(0).getTargetId()).isEqualTo(createdItem.id().toString());
        assertThat(recordedAudits.get(1).getTargetId()).isEqualTo(createdItem.qrCode());
        assertThat(recordedAudits.get(2).getTargetId()).isEqualTo(createdItem.id().toString());
    }
}
