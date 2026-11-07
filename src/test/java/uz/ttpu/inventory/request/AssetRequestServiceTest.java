package uz.ttpu.inventory.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uz.ttpu.inventory.request.api.AssetRequestDto;
import uz.ttpu.inventory.request.api.CreateAssetRequestDto;
import uz.ttpu.inventory.request.domain.AssetRequest;
import uz.ttpu.inventory.request.domain.AssetRequestStatus;
import uz.ttpu.inventory.request.repository.AssetRequestRepository;
import uz.ttpu.inventory.request.service.AssetRequestMapper;
import uz.ttpu.inventory.request.service.AssetRequestService;
import uz.ttpu.inventory.workflow.api.WorkflowCompletedEvent;
import uz.ttpu.inventory.workflow.domain.WorkflowDocumentType;
import uz.ttpu.inventory.workflow.domain.WorkflowState;

class AssetRequestServiceTest {

    private final Map<UUID, AssetRequest> store = new HashMap<>();
    private AssetRequestRepository repository;
    private AssetRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(AssetRequestRepository.class);
        store.clear();

        when(repository.saveAndFlush(any(AssetRequest.class))).thenAnswer(inv -> {
            AssetRequest entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            store.put(entity.getId(), entity);
            return entity;
        });
        when(repository.findById(any(UUID.class))).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.<UUID>getArgument(0))));
        when(repository.findByRequestNumber(any(String.class))).thenAnswer(inv -> {
            String num = inv.getArgument(0);
            return store.values().stream()
                    .filter(r -> num.equalsIgnoreCase(r.getRequestNumber()))
                    .findFirst();
        });

        AssetRequestMapper mapper = Mappers.getMapper(AssetRequestMapper.class);
        service = new AssetRequestService(repository, mapper);
    }

    @Test
    void lifecycleTransitionsFromDraftToSubmittedApprovedAndFulfilledAndEnforcesGuards() {
        CreateAssetRequestDto createDto = new CreateAssetRequestDto(
                "REQ-2026-001",
                UUID.randomUUID(),
                "Oscilloscope Tektronix TBS2000B",
                3,
                new BigDecimal("14500000.00"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Needed for Electronics Lab"
        );

        AssetRequestDto created = service.createRequest(createDto);
        assertThat(created.status()).isEqualTo(AssetRequestStatus.DRAFT);
        assertThat(created.totalEstimatedCost()).isEqualByComparingTo("43500000.00");

        // Duplicate requestNumber must be rejected
        assertThatThrownBy(() -> service.createRequest(createDto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        // Cannot approve while still in DRAFT
        assertThatThrownBy(() -> service.approveRequest(created.id(), "dept.head"))
                .isInstanceOf(IllegalStateException.class);

        AssetRequestDto submitted = service.submitRequest(created.id());
        assertThat(submitted.status()).isEqualTo(AssetRequestStatus.SUBMITTED);
        assertThat(submitted.submittedAt()).isNotNull();

        AssetRequestDto approved = service.approveRequest(created.id(), "rector");
        assertThat(approved.status()).isEqualTo(AssetRequestStatus.APPROVED);
        assertThat(approved.resolvedByUsername()).isEqualTo("rector");

        UUID fulfilledItemId = UUID.randomUUID();
        AssetRequestDto fulfilled = service.fulfillRequest(created.id(), fulfilledItemId, "warehouse.mgr");
        assertThat(fulfilled.status()).isEqualTo(AssetRequestStatus.FULFILLED);
        assertThat(fulfilled.fulfilledItemId()).isEqualTo(fulfilledItemId);
    }

    @Test
    void workflowCompletedEventUpdatesServiceRequestStatusToApprovedOrRejected() {
        AssetRequestDto draft = service.createRequest(new CreateAssetRequestDto(
                "REQ-2026-002",
                UUID.randomUUID(),
                "Laser Printer HP",
                2,
                new BigDecimal("4000000.00"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Office use"
        ));
        service.submitRequest(draft.id());

        service.onWorkflowCompleted(new WorkflowCompletedEvent(
                UUID.randomUUID(),
                WorkflowDocumentType.SERVICE_REQUEST,
                draft.id(),
                "REQ-2026-002",
                WorkflowState.REJECTED,
                "Budget deferred to next quarter",
                Instant.now()
        ));

        AssetRequestDto updated = service.getById(draft.id());
        assertThat(updated.status()).isEqualTo(AssetRequestStatus.REJECTED);
        assertThat(updated.resolvedByUsername()).isEqualTo("WORKFLOW");
    }
}
