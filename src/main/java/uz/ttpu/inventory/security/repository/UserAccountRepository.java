package uz.ttpu.inventory.security.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.ttpu.inventory.security.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByUsernameIgnoreCase(String username);

  Optional<UserAccount> findByEmailIgnoreCase(String email);

  Optional<UserAccount> findByEmployeeId(UUID employeeId);
}
