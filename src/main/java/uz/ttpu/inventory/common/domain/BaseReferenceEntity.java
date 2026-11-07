package uz.ttpu.inventory.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Getter
@Setter
@NoArgsConstructor
@Audited
@MappedSuperclass
public abstract class BaseReferenceEntity extends BaseEntity {

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
