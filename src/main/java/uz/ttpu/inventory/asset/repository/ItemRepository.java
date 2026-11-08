package uz.ttpu.inventory.asset.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.ttpu.inventory.asset.domain.Item;
import uz.ttpu.inventory.asset.domain.ItemState;

public interface ItemRepository extends JpaRepository<Item, UUID>, JpaSpecificationExecutor<Item> {
  Optional<Item> findByInventoryNumberIgnoreCase(String inventoryNumber);

  boolean existsByInventoryNumberIgnoreCase(String inventoryNumber);

  boolean existsBySerialNumberIgnoreCase(String serialNumber);

  Optional<Item> findByQrCode(String qrCode);

  Optional<Item> findByQrCodeIgnoreCase(String qrCode);

  boolean existsByQrCode(String qrCode);

  List<Item> findAllByDepartmentId(UUID departmentId);

  List<Item> findAllByBuildingId(UUID buildingId);

  List<Item> findAllByRoomIdInAndStateNot(Collection<UUID> roomIds, ItemState excludedState);

  List<Item> findAllByState(ItemState state);

  List<Item> findAllByCustodianEmployeeIdAndStateNot(
      UUID custodianEmployeeId, ItemState excludedState);

  long countByCustodianEmployeeIdAndStateNot(UUID custodianEmployeeId, ItemState excludedState);

  List<Item> findAllByStateInAndExpectedReturnAtBefore(Collection<ItemState> states, Instant now);

  List<Item> findAllByWarrantyExpiresAtBetween(Instant from, Instant to);

  List<Item> findAllByParentItemId(UUID parentItemId);
}
