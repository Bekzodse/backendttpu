package uz.ttpu.inventory.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.ttpu.inventory.asset.api.AssetLogisticsApi;
import uz.ttpu.inventory.asset.api.ItemDto;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.movement.api.CreateOffCampusPermitRequest;
import uz.ttpu.inventory.movement.api.PermitCheckpointResultDto;
import uz.ttpu.inventory.movement.domain.OffCampusPermit;
import uz.ttpu.inventory.movement.domain.PermitStatus;
import uz.ttpu.inventory.movement.repository.OffCampusPermitRepository;
import uz.ttpu.inventory.movement.service.OffCampusPermitService;

class SecurityCheckpointPermitTest {

  private final List<OffCampusPermit> permitDb = new ArrayList<>();
  private AssetLogisticsApi assetLogisticsApi;
  private OffCampusPermitRepository permitRepository;
  private OffCampusPermitService permitService;

  @BeforeEach
  void setUp() {
    permitDb.clear();
    assetLogisticsApi = mock(AssetLogisticsApi.class);
    permitRepository = mock(OffCampusPermitRepository.class);

    when(permitRepository.save(any(OffCampusPermit.class)))
        .thenAnswer(
            inv -> {
              OffCampusPermit p = inv.getArgument(0);
              if (p.getId() == null) {
                p.setId(UUID.randomUUID());
              }
              if (p.getCreatedAt() == null) {
                p.setCreatedAt(Instant.now());
              }
              permitDb.add(p);
              return p;
            });

    when(permitRepository.findTopByQrCodeIgnoreCaseAndStatus(
            any(String.class), any(PermitStatus.class)))
        .thenAnswer(
            inv -> {
              String qr = inv.getArgument(0);
              PermitStatus status = inv.getArgument(1);
              return permitDb.stream()
                  .filter(
                      p ->
                          p.getQrCode() != null
                              && p.getQrCode().equalsIgnoreCase(qr)
                              && p.getStatus() == status)
                  .findFirst();
            });

    when(permitRepository.findTopByItemIdAndStatus(any(UUID.class), any(PermitStatus.class)))
        .thenAnswer(
            inv -> {
              UUID itemId = inv.getArgument(0);
              PermitStatus status = inv.getArgument(1);
              return permitDb.stream()
                  .filter(p -> p.getItemId().equals(itemId) && p.getStatus() == status)
                  .findFirst();
            });

    when(permitRepository.findByPermitNumber(any(String.class)))
        .thenAnswer(
            inv -> {
              String num = inv.getArgument(0);
              return permitDb.stream()
                  .filter(p -> p.getPermitNumber().equalsIgnoreCase(num))
                  .findFirst();
            });

    uz.ttpu.inventory.references.api.ReferenceSyncApi referenceSyncApi =
        mock(uz.ttpu.inventory.references.api.ReferenceSyncApi.class);
    permitService =
        new OffCampusPermitService(permitRepository, assetLogisticsApi, referenceSyncApi);
  }

  private ItemDto createItemDto(UUID id, String invNum, String qr, String name) {
    return new ItemDto(
        id,
        invNum,
        "SN-" + invNum,
        qr,
        name,
        "Laboratory Equipment",
        UUID.randomUUID(),
        ItemState.FOYDALANISHDA,
        new BigDecimal("15000000.00"),
        new BigDecimal("10000000.00"),
        false,
        Instant.now(),
        Instant.now().plusSeconds(86400 * 365),
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        Map.of(),
        "1C-" + invNum,
        Instant.now(),
        Instant.now());
  }

  @Test
  @DisplayName("B3.4: Active permit scanned within validity window returns MAY_LEAVE")
  void testActivePermitReturnsMayLeave() {
    UUID itemId = UUID.randomUUID();
    String qrCode = "TTPU-QR-LAB-01";
    ItemDto item = createItemDto(itemId, "INV-OSC-01", qrCode, "Digital Oscilloscope");

    when(assetLogisticsApi.findItemByIdOrQrOptional(null, qrCode)).thenReturn(Optional.of(item));
    when(assetLogisticsApi.resolveItemByIdOrQr(any(), any())).thenReturn(item);

    Instant now = Instant.now();
    permitService.createPermit(
        new CreateOffCampusPermitRequest(
            itemId,
            qrCode,
            UUID.randomUUID(),
            "Farrukh Alimov",
            "Field research measurement at external laboratory",
            now.minus(1, ChronoUnit.HOURS),
            now.plus(8, ChronoUnit.HOURS),
            "Gate 1 Security approved"));

    PermitCheckpointResultDto checkpoint = permitService.verifySecurityCheckpoint(qrCode);

    assertThat(checkpoint).isNotNull();
    assertThat(checkpoint.mayLeave()).isTrue();
    assertThat(checkpoint.decision()).isEqualTo("MAY_LEAVE");
    assertThat(checkpoint.itemName()).isEqualTo("Digital Oscilloscope");
    assertThat(checkpoint.message()).contains("Authorized");
  }

  @Test
  @DisplayName("B3.4: Expired permit scanned past validUntil returns MAY_NOT_LEAVE")
  void testExpiredPermitReturnsMayNotLeave() {
    UUID itemId = UUID.randomUUID();
    String qrCode = "TTPU-QR-EXP-02";
    ItemDto item = createItemDto(itemId, "INV-EXP-02", qrCode, "Survey Drone DJI");

    when(assetLogisticsApi.findItemByIdOrQrOptional(null, qrCode)).thenReturn(Optional.of(item));
    when(assetLogisticsApi.resolveItemByIdOrQr(any(), any())).thenReturn(item);

    Instant now = Instant.now();
    // Expired yesterday
    OffCampusPermit permit =
        OffCampusPermit.builder()
            .permitNumber("PRM-2026-EXPIRED")
            .itemId(itemId)
            .qrCode(qrCode)
            .inventoryNumber("INV-EXP-02")
            .itemName("Survey Drone DJI")
            .employeeId(UUID.randomUUID())
            .employeeFullName("Farrukh Alimov")
            .purpose("Aerial mapping")
            .status(PermitStatus.ACTIVE)
            .validFrom(now.minus(2, ChronoUnit.DAYS))
            .validUntil(now.minus(1, ChronoUnit.DAYS))
            .build();
    permit.setId(UUID.randomUUID());
    permit.setCreatedAt(now.minus(2, ChronoUnit.DAYS));
    permitDb.add(permit);

    PermitCheckpointResultDto checkpoint = permitService.verifySecurityCheckpoint(qrCode);

    assertThat(checkpoint.mayLeave()).isFalse();
    assertThat(checkpoint.decision()).isEqualTo("MAY_NOT_LEAVE");
    assertThat(checkpoint.message()).contains("outside the authorized time window");
  }

  @Test
  @DisplayName("B3.4: Item with no permit returns MAY_NOT_LEAVE")
  void testItemWithoutPermitReturnsMayNotLeave() {
    UUID itemId = UUID.randomUUID();
    String qrCode = "TTPU-QR-NO-PERMIT";
    ItemDto item = createItemDto(itemId, "INV-NOPRM-03", qrCode, "Laboratory Centrifuge");

    when(assetLogisticsApi.findItemByIdOrQrOptional(null, qrCode)).thenReturn(Optional.of(item));
    when(assetLogisticsApi.resolveItemByIdOrQr(any(), any())).thenReturn(item);

    PermitCheckpointResultDto checkpoint = permitService.verifySecurityCheckpoint(qrCode);

    assertThat(checkpoint.mayLeave()).isFalse();
    assertThat(checkpoint.decision()).isEqualTo("MAY_NOT_LEAVE");
    assertThat(checkpoint.message()).contains("No active off-campus permit found");
  }

  @Test
  @DisplayName("B3.4: Unrecognized QR scanned returns MAY_NOT_LEAVE")
  void testUnrecognizedQrReturnsMayNotLeave() {
    String qrCode = "TTPU-QR-UNKNOWN";
    when(assetLogisticsApi.findItemByIdOrQrOptional(null, qrCode)).thenReturn(Optional.empty());

    PermitCheckpointResultDto checkpoint = permitService.verifySecurityCheckpoint(qrCode);

    assertThat(checkpoint).isNotNull();
    assertThat(checkpoint.mayLeave()).isFalse();
    assertThat(checkpoint.decision()).isEqualTo("MAY_NOT_LEAVE");
    assertThat(checkpoint.message()).contains("Item not recognized");
  }
}
