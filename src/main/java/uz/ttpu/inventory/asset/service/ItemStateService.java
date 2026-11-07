package uz.ttpu.inventory.asset.service;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.asset.api.ItemStateChangedEvent;
import uz.ttpu.inventory.asset.domain.IllegalStateTransitionException;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemEvent;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.domain.ItemStateHistory;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;

@Service
@RequiredArgsConstructor
public class ItemStateService {

  private static final Map<ItemState, Map<ItemEvent, ItemState>> TRANSITION_MATRIX =
      buildTransitionMatrix();

  private final ItemRepository itemRepository;
  private final ItemStateHistoryRepository itemStateHistoryRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public Item transition(Item item, ItemEvent event, Object document) {
    if (item == null) {
      throw new IllegalStateTransitionException("Item must not be null");
    }
    if (event == null) {
      throw new IllegalStateTransitionException("ItemEvent must not be null");
    }
    if (document == null || (document instanceof CharSequence cs && cs.toString().isBlank())) {
      throw new IllegalStateTransitionException(
          "Supporting document is required for state transition");
    }

    ItemState currentState = item.getState();
    Map<ItemEvent, ItemState> allowedForState =
        TRANSITION_MATRIX.getOrDefault(currentState, Map.of());
    ItemState targetState = allowedForState.get(event);

    if (targetState == null) {
      throw new IllegalStateTransitionException(currentState, event);
    }

    Instant now = Instant.now();
    item.applyStateTransition(targetState);
    if (targetState == ItemState.FOYDALANISHDA && item.getCommissionedAt() == null) {
      item.setCommissionedAt(now);
    }

    Item savedItem = itemRepository.save(item);

    String documentType = document.getClass().getSimpleName();
    String documentRef = document.toString();

    ItemStateHistory history =
        ItemStateHistory.builder()
            .itemId(savedItem.getId())
            .fromState(currentState)
            .toState(targetState)
            .event(event)
            .documentType(documentType)
            .documentRef(documentRef)
            .transitionedAt(now)
            .build();
    itemStateHistoryRepository.save(history);

    eventPublisher.publishEvent(
        new ItemStateChangedEvent(
            savedItem.getId(),
            savedItem.getInventoryNumber(),
            currentState,
            targetState,
            event,
            documentType,
            documentRef,
            now));

    return savedItem;
  }

  public boolean isTransitionAllowed(ItemState currentState, ItemEvent event) {
    if (currentState == null || event == null) {
      return false;
    }
    return TRANSITION_MATRIX.getOrDefault(currentState, Map.of()).containsKey(event);
  }

  public Map<ItemEvent, ItemState> getAllowedTransitions(ItemState currentState) {
    if (currentState == null) {
      return Map.of();
    }
    return TRANSITION_MATRIX.getOrDefault(currentState, Map.of());
  }

  private static Map<ItemState, Map<ItemEvent, ItemState>> buildTransitionMatrix() {
    Map<ItemState, Map<ItemEvent, ItemState>> matrix = new EnumMap<>(ItemState.class);

    // 1. OMBORDA (In Warehouse)
    matrix.put(
        ItemState.OMBORDA,
        Map.of(
            ItemEvent.DISPATCH, ItemState.YOLDA,
            ItemEvent.PUT_INTO_USE, ItemState.FOYDALANISHDA,
            ItemEvent.TAKE_OUTSIDE, ItemState.VAQTINCHA_TASHQARIDA,
            ItemEvent.SEND_TO_REPAIR, ItemState.TAMIRDA,
            ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA,
            ItemEvent.RECOMMEND_WRITE_OFF, ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN));

    // 2. YOLDA (In Transit)
    matrix.put(
        ItemState.YOLDA,
        Map.of(
            ItemEvent.PUT_INTO_USE, ItemState.FOYDALANISHDA,
            ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA,
            ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA));

    // 3. FOYDALANISHDA (In Use)
    matrix.put(
        ItemState.FOYDALANISHDA,
        Map.of(
            ItemEvent.DISPATCH, ItemState.YOLDA,
            ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA,
            ItemEvent.TAKE_OUTSIDE, ItemState.VAQTINCHA_TASHQARIDA,
            ItemEvent.SEND_TO_REPAIR, ItemState.TAMIRDA,
            ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA,
            ItemEvent.RECOMMEND_WRITE_OFF, ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN));

    // 4. VAQTINCHA_TASHQARIDA (Temporarily Outside)
    matrix.put(
        ItemState.VAQTINCHA_TASHQARIDA,
        Map.of(
            ItemEvent.RETURN_FROM_OUTSIDE, ItemState.FOYDALANISHDA,
            ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA,
            ItemEvent.SEND_TO_REPAIR, ItemState.TAMIRDA,
            ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA));

    // 5. TAMIRDA (Under Repair)
    matrix.put(
        ItemState.TAMIRDA,
        Map.of(
            ItemEvent.COMPLETE_REPAIR, ItemState.FOYDALANISHDA,
            ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA,
            ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA,
            ItemEvent.RECOMMEND_WRITE_OFF, ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN));

    // 6. QIDIRUVDA (Missing / In Search)
    matrix.put(
        ItemState.QIDIRUVDA,
        Map.of(
            ItemEvent.FOUND_IN_USE, ItemState.FOYDALANISHDA,
            ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA,
            ItemEvent.RECOMMEND_WRITE_OFF, ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN));

    // 7. HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN (Recommended for Write-off)
    matrix.put(
        ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN,
        Map.of(
            ItemEvent.APPROVE_WRITE_OFF, ItemState.HISOBDAN_CHIQARILGAN,
            ItemEvent.REJECT_WRITE_OFF, ItemState.FOYDALANISHDA,
            ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA));

    // 8. HISOBDAN_CHIQARILGAN (Written Off - Terminal State)
    matrix.put(ItemState.HISOBDAN_CHIQARILGAN, Map.of());

    return Collections.unmodifiableMap(matrix);
  }
}
