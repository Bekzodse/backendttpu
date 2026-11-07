package uz.ttpu.inventory.references.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import uz.ttpu.inventory.common.domain.BaseReferenceEntity;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
@Entity
@Table(
    name = "rooms",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_rooms_floor_room_number",
          columnNames = {"floor_id", "room_number"})
    })
public class Room extends BaseReferenceEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "floor_id", nullable = false)
  private Floor floor;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private Department department;

  @Column(name = "room_number", nullable = false, length = 50)
  private String roomNumber;

  @Column(name = "name", length = 255)
  private String name;

  @Column(name = "capacity")
  private Integer capacity;
}
