package uz.ttpu.inventory.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import uz.ttpu.inventory.asset.domain.DataWipeRecord;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.domain.ItemStateHistory;
import uz.ttpu.inventory.asset.repository.DataWipeRecordRepository;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.asset.service.AssetService;
import uz.ttpu.inventory.asset.service.ItemMapper;
import uz.ttpu.inventory.asset.service.ItemStateService;
import uz.ttpu.inventory.asset.service.QrIdService;
import uz.ttpu.inventory.common.domain.DataWipeRequiredException;
import uz.ttpu.inventory.finance.api.WriteOffCompletionRequest;
import uz.ttpu.inventory.finance.api.WriteOffResultDto;
import uz.ttpu.inventory.finance.service.FinanceWriteOffService;
import uz.ttpu.inventory.references.api.CategoryDto;
import uz.ttpu.inventory.references.api.ReferenceSyncApi;

class FinanceWriteOffDataWipeTest {

  private final Map<UUID, Item> itemDb = new HashMap<>();
  private final List<DataWipeRecord> dataWipeDb = new ArrayList<>();
  private final Map<UUID, CategoryDto> categoryDb = new HashMap<>();

  private FinanceWriteOffService writeOffService;

  @BeforeEach
  void setUp() {
    itemDb.clear();
    dataWipeDb.clear();
    categoryDb.clear();

    ItemRepository itemRepository = mock(ItemRepository.class);
    when(itemRepository.save(any(Item.class)))
        .thenAnswer(
            inv -> {
              Item item = inv.getArgument(0);
              if (item.getId() == null) {
                item.setId(UUID.randomUUID());
              }
              itemDb.put(item.getId(), item);
              return item;
            });
    when(itemRepository.findById(any(UUID.class)))
        .thenAnswer(inv -> Optional.ofNullable(itemDb.get(inv.getArgument(0))));

    ItemStateHistoryRepository historyRepository = mock(ItemStateHistoryRepository.class);
    when(historyRepository.save(any(ItemStateHistory.class))).thenAnswer(inv -> inv.getArgument(0));

    DataWipeRecordRepository dataWipeRepository = mock(DataWipeRecordRepository.class);
    when(dataWipeRepository.save(any(DataWipeRecord.class)))
        .thenAnswer(
            inv -> {
              DataWipeRecord record = inv.getArgument(0);
              if (record.getId() == null) {
                record.setId(UUID.randomUUID());
              }
              if (record.getCreatedAt() == null) {
                record.setCreatedAt(Instant.now());
              }
              dataWipeDb.add(record);
              return record;
            });
    when(dataWipeRepository.existsByItemId(any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID id = inv.getArgument(0);
              return dataWipeDb.stream().anyMatch(r -> r.getItemId().equals(id));
            });
    when(dataWipeRepository.findTopByItemIdOrderByWipedAtDesc(any(UUID.class)))
        .thenAnswer(
            inv -> {
              UUID id = inv.getArgument(0);
              return dataWipeDb.stream().filter(r -> r.getItemId().equals(id)).findFirst();
            });

    ReferenceSyncApi referenceSyncApi = mock(ReferenceSyncApi.class);
    when(referenceSyncApi.findCategoryById(any(UUID.class)))
        .thenAnswer(inv -> Optional.ofNullable(categoryDb.get(inv.getArgument(0))));

    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    ItemStateService itemStateService =
        new ItemStateService(itemRepository, historyRepository, eventPublisher);
    QrIdService qrIdService = new QrIdService(itemRepository);
    ItemMapper itemMapper = Mappers.getMapper(ItemMapper.class);

    AssetService assetService =
        new AssetService(
            itemRepository,
            historyRepository,
            itemStateService,
            qrIdService,
            itemMapper,
            dataWipeRepository,
            referenceSyncApi,
            eventPublisher);

    writeOffService = new FinanceWriteOffService(assetService);
  }

  @Test
  @DisplayName(
      "B5.6: IT hardware (laptop) hard-blocks write-off completion if no data wipe was performed")
  void testItItemHardBlocksWriteOffWithoutDataWipe() {
    UUID categoryId = UUID.randomUUID();
    categoryDb.put(
        categoryId,
        new CategoryDto(
            categoryId,
            "CAT-LT",
            "Laptop Computers",
            null,
            5,
            java.util.Map.of(),
            true,
            Instant.now(),
            Instant.now()));

    UUID itemId = UUID.randomUUID();
    Item laptop =
        Item.builder()
            .inventoryNumber("INV-LT-001")
            .name("MacBook Pro M2")
            .categoryId(categoryId)
            .state(ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN)
            .purchasePrice(new BigDecimal("25000000.00"))
            .residualValue(BigDecimal.ZERO)
            .build();
    laptop.setId(itemId);
    itemDb.put(itemId, laptop);

    // Attempt write-off completion with no prior data wipe and no inline data wipe fields
    assertThatThrownBy(
            () ->
                writeOffService.completeWriteOff(
                    itemId,
                    new WriteOffCompletionRequest(
                        "ACT-DISPOSAL-01", "Hardware decommissioned", null, null)))
        .isInstanceOf(DataWipeRequiredException.class)
        .hasMessageContaining("Mandatory IT data-wipe step required");

    assertThat(laptop.getState()).isEqualTo(ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN);
  }

  @Test
  @DisplayName(
      "B5.6: IT hardware write-off succeeds when data-wipe details are provided in completion"
          + " request")
  void testItItemCompletesWriteOffWithInlineDataWipe() {
    UUID categoryId = UUID.randomUUID();
    categoryDb.put(
        categoryId,
        new CategoryDto(
            categoryId,
            "CAT-SRV",
            "Enterprise Servers",
            null,
            5,
            java.util.Map.of(),
            true,
            Instant.now(),
            Instant.now()));

    UUID itemId = UUID.randomUUID();
    Item server =
        Item.builder()
            .inventoryNumber("INV-SRV-99")
            .name("Core Infrastructure Server")
            .categoryId(categoryId)
            .state(ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN)
            .purchasePrice(new BigDecimal("50000000.00"))
            .residualValue(BigDecimal.ZERO)
            .build();
    server.setId(itemId);
    itemDb.put(itemId, server);

    WriteOffResultDto result =
        writeOffService.completeWriteOff(
            itemId,
            new WriteOffCompletionRequest(
                "ACT-WO-2026-99",
                "Replaced with cluster nodes",
                "Sherzod IT Specialist",
                "NIST SP 800-88 Rev 1 Cryptographic Erase"));

    assertThat(result).isNotNull();
    assertThat(result.dataWipeVerified()).isTrue();
    assertThat(server.getState()).isEqualTo(ItemState.HISOBDAN_CHIQARILGAN);
    assertThat(dataWipeDb).hasSize(1);
    assertThat(dataWipeDb.get(0).getPerformedByName()).isEqualTo("Sherzod IT Specialist");
    assertThat(dataWipeDb.get(0).getWipeMethod()).contains("NIST SP 800-88");
  }

  @Test
  @DisplayName("B5.6: Non-IT asset (office chair) completes write-off without requiring data wipe")
  void testNonItAssetCompletesWriteOffDirectly() {
    UUID categoryId = UUID.randomUUID();
    categoryDb.put(
        categoryId,
        new CategoryDto(
            categoryId,
            "CAT-FURN",
            "Office Furniture",
            null,
            5,
            java.util.Map.of(),
            true,
            Instant.now(),
            Instant.now()));

    UUID itemId = UUID.randomUUID();
    Item chair =
        Item.builder()
            .inventoryNumber("INV-CHR-404")
            .name("Ergonomic Mesh Chair")
            .categoryId(categoryId)
            .state(ItemState.FOYDALANISHDA)
            .purchasePrice(new BigDecimal("1500000.00"))
            .residualValue(BigDecimal.ZERO)
            .build();
    chair.setId(itemId);
    itemDb.put(itemId, chair);

    WriteOffResultDto result =
        writeOffService.completeWriteOff(
            itemId,
            new WriteOffCompletionRequest(
                "ACT-WO-FURN-01", "Broken frame beyond repair", null, null));

    assertThat(result).isNotNull();
    assertThat(chair.getState()).isEqualTo(ItemState.HISOBDAN_CHIQARILGAN);
    assertThat(dataWipeDb).isEmpty();
  }
}
