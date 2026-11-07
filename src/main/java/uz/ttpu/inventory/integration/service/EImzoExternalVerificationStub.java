package uz.ttpu.inventory.integration.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EImzoExternalVerificationStub {

  public record EImzoStubVerificationResult(
      boolean verified,
      String pkcs7SignatureId,
      String signerPinfl,
      String signerTin,
      String signerFullName,
      String signerOrganization,
      String certificateSerialNumber,
      String verificationMessage,
      Instant signedAt) {}

  public EImzoStubVerificationResult verifyPdfSignature(
      byte[] pdfBytes,
      String pdfSha256Hash,
      String requestedPinfl,
      String requestedTin,
      String requestedFullName,
      String requestedOrganization,
      String requestedCertSerial) {
    if (pdfBytes == null || pdfBytes.length == 0) {
      throw new IllegalArgumentException("Signed PDF blob must not be empty");
    }

    String preview =
        new String(pdfBytes, 0, Math.min(pdfBytes.length, 256), StandardCharsets.UTF_8);
    boolean isValid = !preview.contains("INVALID_SIGNATURE");

    String pinfl =
        (requestedPinfl != null && !requestedPinfl.isBlank()) ? requestedPinfl : "31504881230045";
    String tin = (requestedTin != null && !requestedTin.isBlank()) ? requestedTin : "201122334";
    String fullName =
        (requestedFullName != null && !requestedFullName.isBlank())
            ? requestedFullName
            : "KARIMOV SARDOR RUSTAMOVICH";
    String organization =
        (requestedOrganization != null && !requestedOrganization.isBlank())
            ? requestedOrganization
            : "Turin Polytechnic University in Tashkent (TTPU)";
    String certSerial =
        (requestedCertSerial != null && !requestedCertSerial.isBlank())
            ? requestedCertSerial
            : "EIMZO-CERT-" + pdfSha256Hash.substring(0, 12).toUpperCase();
    String pkcs7Id =
        "PKCS7-" + UUID.nameUUIDFromBytes(pdfBytes).toString().substring(0, 18).toUpperCase();

    return new EImzoStubVerificationResult(
        isValid,
        pkcs7Id,
        pinfl,
        tin,
        fullName,
        organization,
        certSerial,
        isValid
            ? "E-IMZO PKCS#7 digital signature verified successfully"
            : "E-IMZO signature verification failed",
        Instant.now());
  }
}
