package uz.ttpu.inventory.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import uz.ttpu.inventory.asset.api.ItemStateChangedEvent;
import uz.ttpu.inventory.asset.domain.IllegalStateTransitionException;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemEvent;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.domain.ItemStateHistory;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.asset.service.ItemStateService;

class ItemStateServiceTest {

  private ItemRepository itemRepository;
  private ItemStateHistoryRepository historyRepository;
  private ApplicationEventPublisher eventPublisher;
  private ItemStateService itemStateService;

  @BeforeEach
  void setUp() {
    itemRepository = mock(ItemRepository.class);
    historyRepository = mock(ItemStateHistoryRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
    when(historyRepository.save(any(ItemStateHistory.class))).thenAnswer(inv -> inv.getArgument(0));
    itemStateService = new ItemStateService(itemRepository, historyRepository, eventPublisher);
  }

  static Stream<Arguments> validTransitionsProvider() {
    return Stream.of(
        // 1. From OMBORDA (6 valid transitions)
        arguments(ItemState.OMBORDA, ItemEvent.DISPATCH, ItemState.YOLDA),
        arguments(ItemState.OMBORDA, ItemEvent.PUT_INTO_USE, ItemState.FOYDALANISHDA),
        arguments(ItemState.OMBORDA, ItemEvent.TAKE_OUTSIDE, ItemState.VAQTINCHA_TASHQARIDA),
        arguments(ItemState.OMBORDA, ItemEvent.SEND_TO_REPAIR, ItemState.TAMIRDA),
        arguments(ItemState.OMBORDA, ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA),
        arguments(
            ItemState.OMBORDA,
            ItemEvent.RECOMMEND_WRITE_OFF,
            ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN),

        // 2. From YOLDA (3 valid transitions)
        arguments(ItemState.YOLDA, ItemEvent.PUT_INTO_USE, ItemState.FOYDALANISHDA),
        arguments(ItemState.YOLDA, ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA),
        arguments(ItemState.YOLDA, ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA),

        // 3. From FOYDALANISHDA (6 valid transitions)
        arguments(ItemState.FOYDALANISHDA, ItemEvent.DISPATCH, ItemState.YOLDA),
        arguments(ItemState.FOYDALANISHDA, ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA),
        arguments(ItemState.FOYDALANISHDA, ItemEvent.TAKE_OUTSIDE, ItemState.VAQTINCHA_TASHQARIDA),
        arguments(ItemState.FOYDALANISHDA, ItemEvent.SEND_TO_REPAIR, ItemState.TAMIRDA),
        arguments(ItemState.FOYDALANISHDA, ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA),
        arguments(
            ItemState.FOYDALANISHDA,
            ItemEvent.RECOMMEND_WRITE_OFF,
            ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN),

        // 4. From VAQTINCHA_TASHQARIDA (4 valid transitions)
        arguments(
            ItemState.VAQTINCHA_TASHQARIDA, ItemEvent.RETURN_FROM_OUTSIDE, ItemState.FOYDALANISHDA),
        arguments(ItemState.VAQTINCHA_TASHQARIDA, ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA),
        arguments(ItemState.VAQTINCHA_TASHQARIDA, ItemEvent.SEND_TO_REPAIR, ItemState.TAMIRDA),
        arguments(ItemState.VAQTINCHA_TASHQARIDA, ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA),

        // 5. From TAMIRDA (4 valid transitions)
        arguments(ItemState.TAMIRDA, ItemEvent.COMPLETE_REPAIR, ItemState.FOYDALANISHDA),
        arguments(ItemState.TAMIRDA, ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA),
        arguments(ItemState.TAMIRDA, ItemEvent.MARK_MISSING, ItemState.QIDIRUVDA),
        arguments(
            ItemState.TAMIRDA,
            ItemEvent.RECOMMEND_WRITE_OFF,
            ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN),

        // 6. From QIDIRUVDA (3 valid transitions)
        arguments(ItemState.QIDIRUVDA, ItemEvent.FOUND_IN_USE, ItemState.FOYDALANISHDA),
        arguments(ItemState.QIDIRUVDA, ItemEvent.RETURN_TO_WAREHOUSE, ItemState.OMBORDA),
        arguments(
            ItemState.QIDIRUVDA,
            ItemEvent.RECOMMEND_WRITE_OFF,
            ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN),

        // 7. From HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN (3 valid transitions)
        arguments(
            ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN,
            ItemEvent.APPROVE_WRITE_OFF,
            ItemState.HISOBDAN_CHIQARILGAN),
        arguments(
            ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN,
            ItemEvent.REJECT_WRITE_OFF,
            ItemState.FOYDALANISHDA),
        arguments(
            ItemState.HISOBDAN_CHIQARISHGA_TAVSIYA_ETILGAN,
            ItemEvent.RETURN_TO_WAREHOUSE,
            ItemState.OMBORDA));
  }

  static Stream<Arguments> invalidTransitionsProvider() {
    Set<String> validPairs =
        validTransitionsProvider()
            .map(arg -> arg.get()[0] + ":" + arg.get()[1])
            .collect(Collectors.toSet());

    List<Arguments> invalidArguments = new ArrayList<>();
    for (ItemState state : ItemState.values()) {
      for (ItemEvent event : ItemEvent.values()) {
        if (!validPairs.contains(state + ":" + event)) {
          invalidArguments.add(arguments(state, event));
        }
      }
    }
    return invalidArguments.stream();
  }

  @DisplayName("Valid state transitions succeed, record history, and publish domain event")
  @ParameterizedTest(name = "[{index}] VALID: {0} --({1})--> {2}")
  @MethodSource("validTransitionsProvider")
  void validTransitionUpdatesStateRecordsHistoryAndPublishesEvent(
      ItemState fromState, ItemEvent event, ItemState expectedTargetState) {
    Item item = createItemInState(fromState);
    String docRef = "DOC-" + fromState.name() + "-" + event.name();

    assertThat(itemStateService.isTransitionAllowed(fromState, event)).isTrue();

    Item result = itemStateService.transition(item, event, docRef);

    assertThat(result.getState()).isEqualTo(expectedTargetState);
    if (expectedTargetState == ItemState.FOYDALANISHDA) {
      assertThat(result.getCommissionedAt()).isNotNull();
    }

    ArgumentCaptor<ItemStateHistory> historyCaptor =
        ArgumentCaptor.forClass(ItemStateHistory.class);
    verify(historyRepository).save(historyCaptor.capture());
    ItemStateHistory savedHistory = historyCaptor.getValue();
    assertThat(savedHistory.getItemId()).isEqualTo(item.getId());
    assertThat(savedHistory.getFromState()).isEqualTo(fromState);
    assertThat(savedHistory.getToState()).isEqualTo(expectedTargetState);
    assertThat(savedHistory.getEvent()).isEqualTo(event);
    assertThat(savedHistory.getDocumentRef()).isEqualTo(docRef);

    ArgumentCaptor<ItemStateChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(ItemStateChangedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    ItemStateChangedEvent publishedEvent = eventCaptor.getValue();
    assertThat(publishedEvent.itemId()).isEqualTo(item.getId());
    assertThat(publishedEvent.fromState()).isEqualTo(fromState);
    assertThat(publishedEvent.toState()).isEqualTo(expectedTargetState);
    assertThat(publishedEvent.event()).isEqualTo(event);
    assertThat(publishedEvent.documentRef()).isEqualTo(docRef);
  }

  @DisplayName("Invalid state transitions are rejected and leave item state unchanged")
  @ParameterizedTest(
      name = "[{index}] INVALID: {0} --({1})--> throws IllegalStateTransitionException")
  @MethodSource("invalidTransitionsProvider")
  void invalidTransitionThrowsExceptionAndDoesNotMutateState(ItemState fromState, ItemEvent event) {
    Item item = createItemInState(fromState);

    assertThat(itemStateService.isTransitionAllowed(fromState, event)).isFalse();

    assertThatThrownBy(() -> itemStateService.transition(item, event, "DOC-INVALID-TEST"))
        .isInstanceOf(IllegalStateTransitionException.class)
        .hasMessageContaining(fromState.name())
        .hasMessageContaining(event.name());

    assertThat(item.getState()).isEqualTo(fromState);
    verify(itemRepository, never()).save(any());
    verify(historyRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @DisplayName("Missing or blank supporting document is rejected even for a valid transition")
  @ParameterizedTest(
      name = "[{index}] Invalid document=''{0}'' throws IllegalStateTransitionException")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n"})
  void rejectsNullOrBlankSupportingDocument(String invalidDocument) {
    Item item = createItemInState(ItemState.OMBORDA);

    assertThatThrownBy(() -> itemStateService.transition(item, ItemEvent.DISPATCH, invalidDocument))
        .isInstanceOf(IllegalStateTransitionException.class)
        .hasMessageContaining("Supporting document is required");

    assertThat(item.getState()).isEqualTo(ItemState.OMBORDA);
    verify(itemRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "Valid + Invalid parameterized providers exhaustively cover all 96 (State x Event) pairs")
  void parameterizedProvidersCoverEntireCartesianProduct() {
    long validCount = validTransitionsProvider().count();
    long invalidCount = invalidTransitionsProvider().count();
    long totalPossible = (long) ItemState.values().length * ItemEvent.values().length;

    assertThat(validCount).isEqualTo(29L);
    assertThat(invalidCount).isEqualTo(67L);
    assertThat(validCount + invalidCount).isEqualTo(totalPossible);

    // Verify terminal state HISOBDAN_CHIQARILGAN has zero allowed transitions
    Map<ItemEvent, ItemState> terminalAllowed =
        itemStateService.getAllowedTransitions(ItemState.HISOBDAN_CHIQARILGAN);
    assertThat(terminalAllowed).isEmpty();
  }

  @Test
  @DisplayName("Direct state mutation outside ItemStateService is blocked at runtime")
  void directStateMutationOutsideItemStateServiceIsBlocked() {
    Item item = createItemInState(ItemState.OMBORDA);
    assertThatThrownBy(() -> UnauthorizedStateMutator.mutate(item, ItemState.FOYDALANISHDA))
        .isInstanceOf(IllegalStateTransitionException.class)
        .hasMessageContaining("Item state transitions must only happen via ItemStateService");
  }

  static final class UnauthorizedStateMutator {
    static void mutate(Item item, ItemState targetState) {
      item.applyStateTransition(targetState);
    }
  }

  private Item createItemInState(ItemState initialState) {
    Item item =
        Item.builder()
            .inventoryNumber("INV-" + UUID.randomUUID())
            .name("Test Asset")
            .categoryId(UUID.randomUUID())
            .purchasePrice(new BigDecimal("15000000.00"))
            .residualValue(new BigDecimal("15000000.00"))
            .build();
    item.setId(UUID.randomUUID());
    item.applyStateTransition(initialState);
    return item;
  }
}
