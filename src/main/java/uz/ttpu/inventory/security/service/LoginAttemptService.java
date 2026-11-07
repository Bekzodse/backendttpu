package uz.ttpu.inventory.security.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.security.domain.UserAccount;
import uz.ttpu.inventory.security.repository.UserAccountRepository;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserAccountRepository userAccountRepository;
    private final SecurityProperties securityProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserAccount recordFailedAttempt(UUID userId, Instant now) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= securityProperties.getMaxFailedAttempts()) {
            user.setLockedUntil(now.plus(securityProperties.getLockoutDuration()));
        }

        return userAccountRepository.saveAndFlush(user);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserAccount resetFailedAttemptsOnSuccess(UUID userId, Instant now) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return userAccountRepository.saveAndFlush(user);
    }
}
