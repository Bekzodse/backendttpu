package uz.ttpu.inventory.movement.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.movement.api.CreateOffCampusPermitRequest;
import uz.ttpu.inventory.movement.api.OffCampusPermitDto;
import uz.ttpu.inventory.movement.api.PermitCheckpointResultDto;
import uz.ttpu.inventory.movement.domain.OffCampusPermit;
import uz.ttpu.inventory.movement.domain.PermitStatus;
import uz.ttpu.inventory.movement.repository.OffCampusPermitRepository;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

@Service
@RequiredArgsConstructor
public class OffCampusPermitService {

  private final OffCampusPermitRepository permitRepository;
  private final AssetLogisticsApi assetLogisticsApi;
  private final ReferenceSyncApi referenceSyncApi;

  @Transactional
  public OffCampusPermitDto createPermit(CreateOffCampusPermitRequest request) {
    ItemDto item = assetLogisticsApi.resolveItemByIdOrQr(request.itemId(), request.qrCode());

    String employeeName = request.employeeFullName();
    if (employeeName == null || employeeName.isBlank()) {
      employeeName =
          referenceSyncApi
              .findEmployeeByCodeOrFullName(null, null)
              .filter(e -> e.id().equals(request.employeeId()))
              .map(e -> (e.firstName() + " " + e.lastName()).trim())
              .orElse("Employee " + request.employeeId());
    }

    String qr =
        (item.qrCode() != null && !item.qrCode().isBlank()) ? item.qrCode() : request.qrCode();
    String permitNumber = "PRM-" + Instant.now().toEpochMilli();

    OffCampusPermit permit =
        OffCampusPermit.builder()
            .permitNumber(permitNumber)
            .itemId(item.id())
            .qrCode(qr != null ? qr : item.inventoryNumber())
            .inventoryNumber(item.inventoryNumber())
            .itemName(item.name())
            .employeeId(request.employeeId())
            .employeeFullName(employeeName)
            .purpose(request.purpose())
            .validFrom(request.validFrom())
            .validUntil(request.validUntil())
            .status(PermitStatus.ACTIVE)
            .approvedBy(
                request.approvedBy() != null
                    ? request.approvedBy()
                    : "Security & Logistics Division")
            .build();

    OffCampusPermit saved = permitRepository.save(permit);
    return toDto(saved);
  }

  @Transactional(readOnly = true)
  public PermitCheckpointResultDto verifySecurityCheckpoint(String qrId) {
    Instant now = Instant.now();
    String normalizedQr = qrId != null ? qrId.trim() : "";

    Optional<ItemDto> itemOpt = assetLogisticsApi.findItemByIdOrQrOptional(null, normalizedQr);
    if (itemOpt.isEmpty()) {
      return new PermitCheckpointResultDto(
          false,
          "MAY_NOT_LEAVE",
          "Denied: Item not recognized for scanned QR: " + normalizedQr,
          normalizedQr,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          now);
    }

    ItemDto item = itemOpt.get();

    Optional<OffCampusPermit> permitOpt =
        permitRepository
            .findTopByQrCodeIgnoreCaseAndStatus(normalizedQr, PermitStatus.ACTIVE)
            .or(() -> permitRepository.findTopByItemIdAndStatus(item.id(), PermitStatus.ACTIVE));

    if (permitOpt.isEmpty()) {
      return new PermitCheckpointResultDto(
          false,
          "MAY_NOT_LEAVE",
          "Denied: No active off-campus permit found for item "
              + item.inventoryNumber()
              + " ("
              + item.name()
              + ").",
          normalizedQr,
          item.id(),
          item.inventoryNumber(),
          item.name(),
          null,
          null,
          null,
          null,
          now);
    }

    OffCampusPermit permit = permitOpt.get();
    if (!permit.isCurrentlyValid(now)) {
      return new PermitCheckpointResultDto(
          false,
          "MAY_NOT_LEAVE",
          "Denied: Permit "
              + permit.getPermitNumber()
              + " is outside the authorized time window ("
              + permit.getValidFrom()
              + " to "
              + permit.getValidUntil()
              + ").",
          normalizedQr,
          item.id(),
          item.inventoryNumber(),
          item.name(),
          permit.getPermitNumber(),
          permit.getEmployeeFullName(),
          permit.getValidFrom(),
          permit.getValidUntil(),
          now);
    }

    return new PermitCheckpointResultDto(
        true,
        "MAY_LEAVE",
        "Authorized: Item "
            + item.inventoryNumber()
            + " may leave campus under permit "
            + permit.getPermitNumber()
            + " (issued to "
            + permit.getEmployeeFullName()
            + ").",
        normalizedQr,
        item.id(),
        item.inventoryNumber(),
        item.name(),
        permit.getPermitNumber(),
        permit.getEmployeeFullName(),
        permit.getValidFrom(),
        permit.getValidUntil(),
        now);
  }

  @Transactional
  public OffCampusPermitDto revokePermit(UUID permitId, String reason) {
    OffCampusPermit permit =
        permitRepository
            .findById(permitId)
            .orElseThrow(() -> new ResourceNotFoundException("OffCampusPermit", permitId));
    permit.setStatus(PermitStatus.REVOKED);
    permit.setRevokedAt(Instant.now());
    permit.setRevocationReason(reason != null ? reason : "Revoked by administrator");
    return toDto(permitRepository.save(permit));
  }

  @Transactional(readOnly = true)
  public List<OffCampusPermitDto> listPermits() {
    return permitRepository.findAll().stream().map(this::toDto).toList();
  }

  private OffCampusPermitDto toDto(OffCampusPermit p) {
    return new OffCampusPermitDto(
        p.getId(),
        p.getPermitNumber(),
        p.getItemId(),
        p.getQrCode(),
        p.getInventoryNumber(),
        p.getItemName(),
        p.getEmployeeId(),
        p.getEmployeeFullName(),
        p.getPurpose(),
        p.getValidFrom(),
        p.getValidUntil(),
        p.getStatus(),
        p.getApprovedBy(),
        p.getRevokedAt(),
        p.getRevocationReason(),
        p.getCreatedAt());
  }
}
