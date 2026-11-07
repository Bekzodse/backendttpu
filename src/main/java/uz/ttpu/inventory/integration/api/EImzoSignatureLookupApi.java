package uz.ttpu.inventory.integration.api;

import java.util.List;
import java.util.UUID;

public interface EImzoSignatureLookupApi {

  List<EImzoSignatureRecordDto> listSignaturesForDocument(UUID documentId);

  EImzoSignatureRecordDto verifyAndRecordSignedPdf(VerifySignedPdfRequest request);

  EImzoSignatureRecordDto verifyAndRecordSignedPdfBlob(
      String documentType,
      UUID documentId,
      String documentNumber,
      byte[] signedPdfBlob,
      String signerPinfl,
      String signerTin,
      String signerFullName,
      String signerOrganization,
      String certificateSerialNumber);
}
