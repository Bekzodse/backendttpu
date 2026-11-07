package uz.ttpu.inventory.integration.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ttpu.inventory.integration.api.EImzoSignatureLookupApi;
import uz.ttpu.inventory.integration.api.EImzoSignatureRecordDto;
import uz.ttpu.inventory.integration.api.VerifySignedPdfRequest;
import uz.ttpu.inventory.integration.domain.EImzoSignatureRecord;
import uz.ttpu.inventory.integration.domain.ExternalSystemType;
import uz.ttpu.inventory.integration.repository.EImzoSignatureRecordRepository;
import uz.ttpu.inventory.integration.service.EImzoExternalVerificationStub.EImzoStubVerificationResult;

@Service
@RequiredArgsConstructor
public class EImzoService implements EImzoSignatureLookupApi {

  private final EImzoSignatureRecordRepository signatureRecordRepository;
  private final EImzoExternalVerificationStub verificationStub;
  private final RetryRegistry retryRegistry;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final IntegrationMapper integrationMapper;

  @Transactional
  public EImzoSignatureRecordDto verifyAndRecordSignedPdf(VerifySignedPdfRequest request) {
    byte[] pdfBytes = decodePdfBlob(request.signedPdfBase64());
    return verifyAndRecordSignedPdfBlob(
        request.documentType(),
        request.documentId(),
        request.documentNumber(),
        pdfBytes,
        request.signerPinfl(),
        request.signerTin(),
        request.signerFullName(),
        request.signerOrganization(),
        request.certificateSerialNumber());
  }

  @Transactional
  public EImzoSignatureRecordDto verifyAndRecordSignedPdfBlob(
      String documentType,
      UUID documentId,
      String documentNumber,
      byte[] signedPdfBlob,
      String signerPinfl,
      String signerTin,
      String signerFullName,
      String signerOrganization,
      String certificateSerialNumber) {
    if (signedPdfBlob == null || signedPdfBlob.length == 0) {
      throw new IllegalArgumentException("Signed PDF blob must not be empty");
    }

    String sha256Hash = computeSha256Hex(signedPdfBlob);
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(ExternalSystemType.E_IMZO.name());
    Retry retry = retryRegistry.retry(ExternalSystemType.E_IMZO.name());

    Supplier<EImzoStubVerificationResult> decoratedVerification =
        Retry.decorateSupplier(
            retry,
            CircuitBreaker.decorateSupplier(
                cb,
                () ->
                    verificationStub.verifyPdfSignature(
                        signedPdfBlob,
                        sha256Hash,
                        signerPinfl,
                        signerTin,
                        signerFullName,
                        signerOrganization,
                        certificateSerialNumber)));

    EImzoStubVerificationResult verificationResult = decoratedVerification.get();
    Instant now = Instant.now();

    EImzoSignatureRecord record =
        EImzoSignatureRecord.builder()
            .documentType(documentType)
            .documentId(documentId)
            .documentNumber(documentNumber)
            .pdfSha256Hash(sha256Hash)
            .pdfSizeBytes(signedPdfBlob.length)
            .pkcs7SignatureId(verificationResult.pkcs7SignatureId())
            .signerPinfl(verificationResult.signerPinfl())
            .signerTin(verificationResult.signerTin())
            .signerFullName(verificationResult.signerFullName())
            .signerOrganization(verificationResult.signerOrganization())
            .certificateSerialNumber(verificationResult.certificateSerialNumber())
            .verified(verificationResult.verified())
            .verificationMessage(verificationResult.verificationMessage())
            .signedAt(verificationResult.signedAt())
            .verifiedAt(now)
            .build();

    return integrationMapper.toEImzoDto(signatureRecordRepository.saveAndFlush(record));
  }

  @Transactional(readOnly = true)
  public List<EImzoSignatureRecordDto> listSignaturesForDocument(UUID documentId) {
    return signatureRecordRepository.findAllByDocumentIdOrderByVerifiedAtDesc(documentId).stream()
        .map(integrationMapper::toEImzoDto)
        .toList();
  }

  private byte[] decodePdfBlob(String rawOrBase64) {
    if (rawOrBase64 == null || rawOrBase64.isBlank()) {
      throw new IllegalArgumentException("Signed PDF payload is required");
    }
    try {
      return Base64.getDecoder().decode(rawOrBase64.trim());
    } catch (IllegalArgumentException ex) {
      return rawOrBase64.getBytes(StandardCharsets.UTF_8);
    }
  }

  private String computeSha256Hex(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(data));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 algorithm not available", ex);
    }
  }
}
