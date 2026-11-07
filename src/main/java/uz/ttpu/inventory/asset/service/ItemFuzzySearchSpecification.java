package uz.ttpu.inventory.asset.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import uz.ttpu.inventory.asset.api.AssetSearchFilterRequest;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;

/**
 * Spring Data JPA {@link Specification} builders for high-performance PostgreSQL {@code pg_trgm}
 * trigram-indexed full-text & fuzzy asset search (FT-8.2, FT-8.3).
 */
public final class ItemFuzzySearchSpecification {

  public static final double DEFAULT_TRIGRAM_SIMILARITY_THRESHOLD = 0.35d;

  private ItemFuzzySearchSpecification() {}

  public static Specification<Item> withFilter(AssetSearchFilterRequest filter) {
    if (filter == null) {
      return (root, query, cb) -> cb.conjunction();
    }
    return Specification.where(matchesGlobalQuery(filter.query(), filter.fuzzySimilarity()))
        .and(hasNameLike(filter.name(), filter.fuzzySimilarity()))
        .and(hasInventoryNumberLike(filter.inventoryNumber(), filter.fuzzySimilarity()))
        .and(hasSerialNumberLike(filter.serial(), filter.fuzzySimilarity()))
        .and(hasModelLike(filter.model(), filter.fuzzySimilarity()))
        .and(hasResponsiblePersonLike(filter.responsiblePerson(), filter.fuzzySimilarity()))
        .and(hasRoomLike(filter.room(), filter.fuzzySimilarity()))
        .and(hasState(filter.state()))
        .and(hasCategoryId(filter.categoryId()))
        .and(hasDepartmentId(filter.departmentId()))
        .and(hasBuildingId(filter.buildingId()))
        .and(hasRoomId(filter.roomId()))
        .and(hasCustodianEmployeeId(filter.custodianEmployeeId()));
  }

  public static Specification<Item> matchesGlobalQuery(String rawQuery, boolean fuzzySimilarity) {
    String normalized = normalize(rawQuery);
    if (normalized == null) {
      return null;
    }
    return (root, query, cb) -> {
      List<Predicate> ors = new ArrayList<>(6);
      ors.add(buildTrigramPredicate(root, cb, "name", normalized, fuzzySimilarity));
      ors.add(buildTrigramPredicate(root, cb, "inventoryNumber", normalized, fuzzySimilarity));
      ors.add(buildTrigramPredicate(root, cb, "serialNumber", normalized, fuzzySimilarity));
      ors.add(buildTrigramPredicate(root, cb, "model", normalized, fuzzySimilarity));
      ors.add(buildTrigramPredicate(root, cb, "responsiblePerson", normalized, fuzzySimilarity));
      ors.add(buildTrigramPredicate(root, cb, "room", normalized, fuzzySimilarity));
      return cb.or(ors.toArray(Predicate[]::new));
    };
  }

  public static Specification<Item> hasNameLike(String name, boolean fuzzySimilarity) {
    String normalized = normalize(name);
    if (normalized == null) {
      return null;
    }
    return (root, query, cb) ->
        buildTrigramPredicate(root, cb, "name", normalized, fuzzySimilarity);
  }

  public static Specification<Item> hasInventoryNumberLike(
      String inventoryNumber, boolean fuzzySimilarity) {
    String normalized = normalize(inventoryNumber);
    if (normalized == null) {
      return null;
    }
    return (root, query, cb) ->
        buildTrigramPredicate(root, cb, "inventoryNumber", normalized, fuzzySimilarity);
  }

  public static Specification<Item> hasSerialNumberLike(String serial, boolean fuzzySimilarity) {
    String normalized = normalize(serial);
    if (normalized == null) {
      return null;
    }
    return (root, query, cb) ->
        buildTrigramPredicate(root, cb, "serialNumber", normalized, fuzzySimilarity);
  }

  public static Specification<Item> hasModelLike(String model, boolean fuzzySimilarity) {
    String normalized = normalize(model);
    if (normalized == null) {
      return null;
    }
    return (root, query, cb) ->
        buildTrigramPredicate(root, cb, "model", normalized, fuzzySimilarity);
  }

  public static Specification<Item> hasResponsiblePersonLike(
      String responsiblePerson, boolean fuzzySimilarity) {
    String normalized = normalize(responsiblePerson);
    if (normalized == null) {
      return null;
    }
    return (root, query, cb) ->
        buildTrigramPredicate(root, cb, "responsiblePerson", normalized, fuzzySimilarity);
  }

  public static Specification<Item> hasRoomLike(String room, boolean fuzzySimilarity) {
    String normalized = normalize(room);
    if (normalized == null) {
      return null;
    }
    return (root, query, cb) ->
        buildTrigramPredicate(root, cb, "room", normalized, fuzzySimilarity);
  }

  public static Specification<Item> hasState(ItemState state) {
    if (state == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("state"), state);
  }

  public static Specification<Item> hasCategoryId(UUID categoryId) {
    if (categoryId == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
  }

  public static Specification<Item> hasDepartmentId(UUID departmentId) {
    if (departmentId == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("departmentId"), departmentId);
  }

  public static Specification<Item> hasBuildingId(UUID buildingId) {
    if (buildingId == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("buildingId"), buildingId);
  }

  public static Specification<Item> hasRoomId(UUID roomId) {
    if (roomId == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("roomId"), roomId);
  }

  public static Specification<Item> hasCustodianEmployeeId(UUID custodianEmployeeId) {
    if (custodianEmployeeId == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("custodianEmployeeId"), custodianEmployeeId);
  }

  private static Predicate buildTrigramPredicate(
      Root<Item> root,
      CriteriaBuilder cb,
      String attributeName,
      String normalizedTerm,
      boolean fuzzySimilarity) {
    Expression<String> columnExpr = root.get(attributeName);
    // PostgreSQL GIN (gin_trgm_ops) index accelerates both ILIKE / LOWER(col) LIKE '%term%' and
    // word_similarity()
    Predicate ilikePredicate = cb.like(cb.lower(columnExpr), "%" + normalizedTerm + "%");
    if (!fuzzySimilarity) {
      return ilikePredicate;
    }
    Expression<Double> similarityExpr =
        cb.function("word_similarity", Double.class, cb.literal(normalizedTerm), columnExpr);
    Predicate trigramSimilarityPredicate =
        cb.greaterThanOrEqualTo(similarityExpr, DEFAULT_TRIGRAM_SIMILARITY_THRESHOLD);
    return cb.or(ilikePredicate, trigramSimilarityPredicate);
  }

  private static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String trimmed = raw.trim().toLowerCase(Locale.ROOT);
    return trimmed.isEmpty() ? null : trimmed;
  }
}
