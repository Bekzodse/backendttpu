package uz.ttpu.inventory.repair;

import static org.assertj.core.api.Assertions.assertThat;
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
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.domain.ItemStateHistory;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.asset.service.AssetService;
import uz.ttpu.inventory.asset.service.ItemMapper;
import uz.ttpu.inventory.asset.service.ItemStateService;
import uz.ttpu.inventory.asset.service.QrIdService;
import uz.ttpu.inventory.repair.api.CompleteRepairRequest;
import uz.ttpu.inventory.repair.api.ItemMovedToVendorRepairEvent;
import uz.ttpu.inventory.repair.api.ItemRepairCompletedEvent;
import uz.ttpu.inventory.repair.api.RepairReplacementRecommendedEvent;
import uz.ttpu.inventory.repair.api.RepairTicketDto;
import uz.ttpu.inventory.repair.api.VendorRepairRequest;
import uz.ttpu.inventory.repair.domain.RepairTicket;
import uz.ttpu.inventory.repair.domain.RepairTicketState;
import uz.ttpu.inventory.repair.repository.RepairTicketRepository;
import uz.ttpu.inventory.repair.service.RepairMapper;
import uz.ttpu.inventory.repair.service.RepairService;

class Repair50PercentRuleTest {

  private final Map<UUID, Item> itemDb = new HashMap<>();
  private final Map<UUID, RepairTicket> ticketDb = new HashMap<>();
  private final List<Object> publishedEvents = new ArrayList<>();

  private AssetService assetService;
  private RepairService repairService;

  @BeforeEach
  void setUp() {
    itemDb.clear();
    ticketDb.clear();
    publishedEvents.clear();

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

    RepairTicketRepository repairTicketRepository = mock(RepairTicketRepository.class);
    when(repairTicketRepository.save(any(RepairTicket.class)))
        .thenAnswer(
            inv -> {
              RepairTicket ticket = inv.getArgument(0);
              if (ticket.getId() == null) {
                ticket.setId(UUID.randomUUID());
              }
              ticketDb.put(ticket.getId(), ticket);
              return ticket;
            });
    when(repairTicketRepository.findById(any(UUID.class)))
        .thenAnswer(inv -> Optional.ofNullable(ticketDb.get(inv.getArgument(0))));
    when(repairTicketRepository.findAllByReplacementRecommendedTrue())
        .thenAnswer(
            inv ->
                ticketDb.values().stream().filter(RepairTicket::isReplacementRecommended).toList());

    ApplicationEventPublisher eventPublisher =
        event -> {
          publishedEvents.add(event);
          if (event instanceof ItemMovedToVendorRepairEvent vendorEvent) {
            assetService.onItemMovedToVendorRepair(vendorEvent);
          } else if (event instanceof ItemRepairCompletedEvent completedEvent) {
            assetService.onItemRepairCompleted(completedEvent);
          } else if (event instanceof RepairReplacementRecommendedEvent replacementEvent) {
            repairService.onRepairReplacementRecommended(replacementEvent);
          }
        };

    ItemStateService itemStateService =
        new ItemStateService(itemRepository, historyRepository, eventPublisher);
    QrIdService qrIdService = new QrIdService(itemRepository);
    ItemMapper itemMapper = Mappers.getMapper(ItemMapper.class);

    assetService =
        new AssetService(
            itemRepository,
            historyRepository,
            itemStateService,
            qrIdService,
            itemMapper,
            null,
            null,
            eventPublisher);

    RepairMapper repairMapper = Mappers.getMapper(RepairMapper.class);
    repairService = new RepairService(repairTicketRepository, repairMapper, eventPublisher);
  }

  @Test
  @DisplayName(
      "B3.5: If repair cost reaches 50% of item's residual value, auto-flag replacement recommended"
          + " on ticket and item")
  void testRepair50PercentRuleTriggered() {
    UUID itemId = UUID.randomUUID();
    Item item =
        Item.builder()
            .inventoryNumber("INV-SRV-01")
            .name("Database Server Dell PowerEdge")
            .state(ItemState.FOYDALANISHDA)
            .purchasePrice(new BigDecimal("20000000.00"))
            .residualValue(new BigDecimal("10000000.00")) // 50% = 5,000,000.00
            .replacementRecommended(false)
            .build();
    item.setId(itemId);
    itemDb.put(itemId, item);

    RepairTicket ticket =
        RepairTicket.builder()
            .ticketNumber("REP-2026-101")
            .itemId(itemId)
            .state(RepairTicketState.NEW)
            .issueDescription("Motherboard failure")
            .replacementRecommended(false)
            .build();
    ticket.setId(UUID.randomUUID());
    ticketDb.put(ticket.getId(), ticket);

    // Send to vendor repair with estimated cost = 5,500,000 (which is 55% of residual value
    // 10,000,000)
    RepairTicketDto updated =
        repairService.sendToVendorRepair(
            ticket.getId(),
            new VendorRepairRequest(
                "Dell Official Service Center",
                "CNTR-2026-DELL",
                new BigDecimal("5500000.00"),
                Instant.now().plusSeconds(86400 * 5)));

    assertThat(updated.state()).isEqualTo(RepairTicketState.IN_SERVICE);
    assertThat(item.getState()).isEqualTo(ItemState.TAMIRDA);

    // Verify replacement recommended is flagged on the item
    assertThat(item.isReplacementRecommended()).isTrue();

    // Verify replacement recommended is flagged on the ticket
    RepairTicket savedTicket = ticketDb.get(ticket.getId());
    assertThat(savedTicket.isReplacementRecommended()).isTrue();
    assertThat(savedTicket.getReplacementRecommendationReason()).contains("50%");

    // Verify event was dispatched
    assertThat(
            publishedEvents.stream().anyMatch(e -> e instanceof RepairReplacementRecommendedEvent))
        .isTrue();
  }

  @Test
  @DisplayName(
      "B3.5: If repair cost is below 50% of residual value, replacement recommendation is NOT"
          + " triggered")
  void testRepairBelow50PercentRuleNotTriggered() {
    UUID itemId = UUID.randomUUID();
    Item item =
        Item.builder()
            .inventoryNumber("INV-SRV-02")
            .name("Switch Cisco Catalyst")
            .state(ItemState.FOYDALANISHDA)
            .purchasePrice(new BigDecimal("20000000.00"))
            .residualValue(new BigDecimal("10000000.00")) // 50% = 5,000,000.00
            .replacementRecommended(false)
            .build();
    item.setId(itemId);
    itemDb.put(itemId, item);

    RepairTicket ticket =
        RepairTicket.builder()
            .ticketNumber("REP-2026-102")
            .itemId(itemId)
            .state(RepairTicketState.NEW)
            .issueDescription("Fan replacement")
            .replacementRecommended(false)
            .build();
    ticket.setId(UUID.randomUUID());
    ticketDb.put(ticket.getId(), ticket);

    // Estimated cost = 2,000,000 (20% < 50%)
    repairService.sendToVendorRepair(
        ticket.getId(),
        new VendorRepairRequest(
            "Network Fix LLC",
            "CNTR-NET-1",
            new BigDecimal("2000000.00"),
            Instant.now().plusSeconds(86400 * 3)));

    assertThat(item.isReplacementRecommended()).isFalse();
    RepairTicket savedTicket = ticketDb.get(ticket.getId());
    assertThat(savedTicket.isReplacementRecommended()).isFalse();
  }

  @Test
  @DisplayName(
      "B3.5: Fully depreciated asset (residual value = 0) flags replacement recommendation upon any"
          + " repair cost")
  void testZeroResidualValueFlagsReplacement() {
    UUID itemId = UUID.randomUUID();
    Item item =
        Item.builder()
            .inventoryNumber("INV-OLD-01")
            .name("Legacy Laptop ThinkPad T420")
            .state(ItemState.TAMIRDA)
            .purchasePrice(new BigDecimal("8000000.00"))
            .residualValue(BigDecimal.ZERO)
            .replacementRecommended(false)
            .build();
    item.setId(itemId);
    itemDb.put(itemId, item);

    RepairTicket ticket =
        RepairTicket.builder()
            .ticketNumber("REP-2026-103")
            .itemId(itemId)
            .state(RepairTicketState.IN_SERVICE)
            .issueDescription("Screen flickering")
            .replacementRecommended(false)
            .build();
    ticket.setId(UUID.randomUUID());
    ticketDb.put(ticket.getId(), ticket);

    repairService.completeRepair(
        ticket.getId(),
        new CompleteRepairRequest(new BigDecimal("800000.00"), "Replaced LCD inverter"));

    assertThat(item.isReplacementRecommended()).isTrue();
    RepairTicket savedTicket = ticketDb.get(ticket.getId());
    assertThat(savedTicket.isReplacementRecommended()).isTrue();
    assertThat(savedTicket.getReplacementRecommendationReason()).contains("fully depreciated");
  }
}
