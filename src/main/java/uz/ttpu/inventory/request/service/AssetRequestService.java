package uz.ttpu.inventory.request.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.request.api.AssetRequestDto;
import uz.ttpu.inventory.request.api.CreateAssetRequestDto;
import uz.ttpu.inventory.request.domain.AssetRequest;
import uz.ttpu.inventory.request.domain.AssetRequestStatus;
import uz.ttpu.inventory.request.repository.AssetRequestRepository;
import uz.ttpu.inventory.workflow.api.WorkflowCompletedEvent;

@Service
@RequiredArgsConstructor
public class AssetRequestService {

    private final AssetRequestRepository assetRequestRepository;
    private final AssetRequestMapper assetRequestMapper;

    @Transactional
    public AssetRequestDto createRequest(CreateAssetRequestDto dto) {
        if (assetRequestRepository.findByRequestNumber(dto.requestNumber()).isPresent()) {
            throw new IllegalStateException("Asset request with number " + dto.requestNumber() + " already exists");
        }
        if (dto.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        BigDecimal unitCost = dto.estimatedUnitCost().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(dto.quantity())).setScale(2, RoundingMode.HALF_UP);

        AssetRequest entity = AssetRequest.builder()
                .requestNumber(dto.requestNumber())
                .categoryId(dto.categoryId())
                .requestedItemName(dto.requestedItemName())
                .quantity(dto.quantity())
                .estimatedUnitCost(unitCost)
                .totalEstimatedCost(totalCost)
                .departmentId(dto.departmentId())
                .buildingId(dto.buildingId())
                .requesterEmployeeId(dto.requesterEmployeeId())
                .justification(dto.justification())
                .status(AssetRequestStatus.DRAFT)
                .build();

        return assetRequestMapper.toDto(assetRequestRepository.saveAndFlush(entity));
    }

    @Transactional
    public AssetRequestDto submitRequest(UUID requestId) {
        AssetRequest entity = getEntity(requestId);
        if (entity.getStatus() != AssetRequestStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT asset requests can be submitted");
        }
        entity.setStatus(AssetRequestStatus.SUBMITTED);
        entity.setSubmittedAt(Instant.now());
        return assetRequestMapper.toDto(assetRequestRepository.saveAndFlush(entity));
    }

    @Transactional
    public AssetRequestDto approveRequest(UUID requestId, String actorUsername) {
        AssetRequest entity = getEntity(requestId);
        if (entity.getStatus() != AssetRequestStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED asset requests can be approved");
        }
        entity.setStatus(AssetRequestStatus.APPROVED);
        entity.setResolvedAt(Instant.now());
        entity.setResolvedByUsername(actorUsername != null && !actorUsername.isBlank() ? actorUsername : "SYSTEM");
        return assetRequestMapper.toDto(assetRequestRepository.saveAndFlush(entity));
    }

    @Transactional
    public AssetRequestDto rejectRequest(UUID requestId, String actorUsername) {
        AssetRequest entity = getEntity(requestId);
        if (entity.getStatus() != AssetRequestStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED asset requests can be rejected");
        }
        entity.setStatus(AssetRequestStatus.REJECTED);
        entity.setResolvedAt(Instant.now());
        entity.setResolvedByUsername(actorUsername != null && !actorUsername.isBlank() ? actorUsername : "SYSTEM");
        return assetRequestMapper.toDto(assetRequestRepository.saveAndFlush(entity));
    }

    @Transactional
    public AssetRequestDto fulfillRequest(UUID requestId, UUID fulfilledItemId, String actorUsername) {
        AssetRequest entity = getEntity(requestId);
        if (entity.getStatus() != AssetRequestStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED asset requests can be fulfilled");
        }
        entity.setStatus(AssetRequestStatus.FULFILLED);
        entity.setFulfilledItemId(fulfilledItemId);
        entity.setResolvedAt(Instant.now());
        entity.setResolvedByUsername(actorUsername != null && !actorUsername.isBlank() ? actorUsername : "SYSTEM");
        return assetRequestMapper.toDto(assetRequestRepository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public AssetRequestDto getById(UUID requestId) {
        return assetRequestMapper.toDto(getEntity(requestId));
    }

    @Transactional(readOnly = true)
    public List<AssetRequestDto> listAll() {
        return assetRequestRepository.findAll()
                .stream()
                .map(assetRequestMapper::toDto)
                .toList();
    }

    @EventListener
    @Transactional
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (event.isServiceRequestDocument() && event.documentId() != null) {
            assetRequestRepository.findById(event.documentId()).ifPresent(entity -> {
                if (entity.getStatus() == AssetRequestStatus.SUBMITTED
                        || entity.getStatus() == AssetRequestStatus.DRAFT) {
                    if (event.isApproved()) {
                        entity.setStatus(AssetRequestStatus.APPROVED);
                        entity.setResolvedAt(event.completedAt() != null ? event.completedAt() : Instant.now());
                        entity.setResolvedByUsername("WORKFLOW");
                        assetRequestRepository.saveAndFlush(entity);
                    } else if (event.isRejected()) {
                        entity.setStatus(AssetRequestStatus.REJECTED);
                        entity.setResolvedAt(event.completedAt() != null ? event.completedAt() : Instant.now());
                        entity.setResolvedByUsername("WORKFLOW");
                        assetRequestRepository.saveAndFlush(entity);
                    }
                }
            });
        }
    }

    private AssetRequest getEntity(UUID requestId) {
        return assetRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("AssetRequest", requestId));
    }
}
