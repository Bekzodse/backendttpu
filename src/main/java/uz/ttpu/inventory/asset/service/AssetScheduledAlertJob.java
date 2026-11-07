package uz.ttpu.inventory.asset.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.OverdueReturnAlertEvent;
import uz.ttpu.inventory.asset.api.WarrantyExpiringAlertEvent;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.repository.ItemRepository;

@Service
@RequiredArgsConstructor
public class AssetScheduledAlertJob {

  public static final int WARRANTY_WARNING_DAYS = 30;

  private final ItemRepository itemRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(cron = "${app.jobs.overdue-returns-cron:0 0 8 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "checkOverdueItemReturnsJob",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT10M")
  @Transactional(readOnly = true)
  public void runDailyOverdueReturnsJob() {
    checkAndPublishOverdueReturns();
  }

  @Transactional(readOnly = true)
  public int checkAndPublishOverdueReturns() {
    Instant now = Instant.now();
    List<Item> overdueItems =
        itemRepository.findAllByStateInAndExpectedReturnAtBefore(
            Set.of(ItemState.VAQTINCHA_TASHQARIDA, ItemState.TAMIRDA), now);

    for (Item item : overdueItems) {
      eventPublisher.publishEvent(
          new OverdueReturnAlertEvent(
              item.getId(),
              item.getInventoryNumber(),
              item.getName(),
              item.getState(),
              item.getExpectedReturnAt(),
              item.getCustodianEmployeeId(),
              now));
    }
    return overdueItems.size();
  }

  @Scheduled(cron = "${app.jobs.expiring-warranties-cron:0 30 8 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "checkExpiringWarrantiesJob",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT10M")
  @Transactional(readOnly = true)
  public void runDailyExpiringWarrantiesJob() {
    checkAndPublishExpiringWarranties();
  }

  @Transactional(readOnly = true)
  public int checkAndPublishExpiringWarranties() {
    Instant now = Instant.now();
    Instant windowEnd = now.plus(Duration.ofDays(WARRANTY_WARNING_DAYS));
    List<Item> expiringItems = itemRepository.findAllByWarrantyExpiresAtBetween(now, windowEnd);

    for (Item item : expiringItems) {
      long daysRemaining =
          Math.max(0L, Duration.between(now, item.getWarrantyExpiresAt()).toDays());
      eventPublisher.publishEvent(
          new WarrantyExpiringAlertEvent(
              item.getId(),
              item.getInventoryNumber(),
              item.getName(),
              item.getWarrantyExpiresAt(),
              daysRemaining,
              item.getCustodianEmployeeId(),
              now));
    }
    return expiringItems.size();
  }
}
