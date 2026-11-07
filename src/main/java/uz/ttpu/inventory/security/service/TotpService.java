package uz.ttpu.inventory.security.service;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.ttpu.inventory.security.api.TotpSetupResponse;

@Service
@RequiredArgsConstructor
public class TotpService {

  private final SecretGenerator secretGenerator;
  private final CodeVerifier codeVerifier;

  public String generateSecret() {
    return secretGenerator.generate();
  }

  public boolean verifyCode(String secret, String code) {
    if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
      return false;
    }
    return codeVerifier.isValidCode(secret, code);
  }

  public TotpSetupResponse buildSetupData(String username, String secret) {
    QrData qrData =
        new QrData.Builder()
            .label(username)
            .secret(secret)
            .issuer("Raqamli Inventar (TTPU)")
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build();
    return new TotpSetupResponse(secret, qrData.getUri());
  }
}
