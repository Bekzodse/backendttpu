package uz.ttpu.inventory.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uz.ttpu.inventory.asset.api.AssetSearchFilterRequest;
import uz.ttpu.inventory.asset.api.AssetSearchResultDto;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;
import uz.ttpu.inventory.asset.repository.ItemRepository;
import uz.ttpu.inventory.asset.repository.ItemStateHistoryRepository;
import uz.ttpu.inventory.asset.service.AssetService;
import uz.ttpu.inventory.asset.service.ItemFuzzySearchSpecification;
import uz.ttpu.inventory.asset.service.ItemMapper;
import uz.ttpu.inventory.asset.service.ItemStateService;
import uz.ttpu.inventory.asset.service.QrIdService;

class AssetFuzzySearchSpecificationTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void specificationBuildsDynamicTrigramAndWordSimilarityPredicatesForAllSixFields() {
        Root<Item> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        Expression lowerExpr = mock(Expression.class);
        Expression similarityExpr = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get(any(String.class))).thenReturn(path);
        when(cb.lower(any())).thenReturn(lowerExpr);
        when(cb.like(any(Expression.class), any(String.class))).thenReturn(predicate);
        when(cb.equal(any(), any())).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.or(any(Predicate[].class))).thenReturn(predicate);
        when(cb.conjunction()).thenReturn(predicate);
        when(cb.literal(any())).thenReturn(mock(Expression.class));
        when(cb.function(any(String.class), any(Class.class), any(), any())).thenReturn(similarityExpr);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(Double.class))).thenReturn(predicate);

        AssetSearchFilterRequest filter = new AssetSearchFilterRequest(
                "OptiPlex",
                "Dell OptiPlex",
                "TTPU-INV-001",
                "SN-7010",
                "OptiPlex 7010",
                "Karimov",
                "204",
                ItemState.FOYDALANISHDA,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                true
        );

        Specification<Item> spec = ItemFuzzySearchSpecification.withFilter(filter);
        Predicate built = spec.toPredicate(root, query, cb);

        assertThat(built).isNotNull();
        verify(root, org.mockito.Mockito.atLeastOnce()).get("name");
        verify(root, org.mockito.Mockito.atLeastOnce()).get("inventoryNumber");
        verify(root, org.mockito.Mockito.atLeastOnce()).get("serialNumber");
        verify(root, org.mockito.Mockito.atLeastOnce()).get("model");
        verify(root, org.mockito.Mockito.atLeastOnce()).get("responsiblePerson");
        verify(root, org.mockito.Mockito.atLeastOnce()).get("room");
        verify(cb, org.mockito.Mockito.atLeastOnce()).function(
                org.mockito.ArgumentMatchers.eq("word_similarity"),
                org.mockito.ArgumentMatchers.eq(Double.class),
                any(),
                any()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchItemsReturnsPaginatedResultsAndExecutionTimeMs() {
        ItemRepository itemRepository = mock(ItemRepository.class);
        ItemStateHistoryRepository historyRepository = mock(ItemStateHistoryRepository.class);
        ItemStateService stateService = mock(ItemStateService.class);
        QrIdService qrIdService = new QrIdService(itemRepository);
        ItemMapper mapper = Mappers.getMapper(ItemMapper.class);

        Item sampleItem = Item.builder()
                .inventoryNumber("TTPU-SEARCH-001")
                .serialNumber("SN-SEARCH-001")
                .qrCode("JBSWY3DPEB")
                .name("HP Z4 G5 Workstation")
                .model("Z4 G5")
                .responsiblePerson("Sardor Karimov (EMP-001)")
                .room("204 CAD Lab")
                .categoryId(UUID.randomUUID())
                .purchasePrice(new BigDecimal("25000000.00"))
                .residualValue(new BigDecimal("22000000.00"))
                .build();
        sampleItem.setId(UUID.randomUUID());

        when(itemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleItem)));

        AssetService assetService = new AssetService(
                itemRepository,
                historyRepository,
                stateService,
                qrIdService,
                mapper
        );

        AssetSearchFilterRequest filter = new AssetSearchFilterRequest(
                "Z4 G5", null, null, null, "Z4 G5", "Karimov", "204",
                ItemState.OMBORDA, null, null, null, null, null, false
        );

        AssetSearchResultDto result = assetService.searchItems(filter, 0, 25);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).inventoryNumber()).isEqualTo("TTPU-SEARCH-001");
        assertThat(result.queryTimeMs()).isGreaterThanOrEqualTo(1L).isLessThan(3_000L);
    }
}
