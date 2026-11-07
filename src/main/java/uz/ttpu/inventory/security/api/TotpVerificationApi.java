package uz.ttpu.inventory.security.api;

import java.util.UUID;

public interface TotpVerificationApi {

  boolean verifyRecipientTotp(UUID recipientEmployeeOrUserId, String totpCode);
}
