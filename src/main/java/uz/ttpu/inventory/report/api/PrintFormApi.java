package uz.ttpu.inventory.report.api;

import java.util.List;

public interface PrintFormApi {

  String FORM_FT_12_9 = "FT-12.9";
  String FORM_FT_9_13 = "FT-9.13";

  String DOC_TYPE_HANDOVER_ACT = "HANDOVER_ACT_FT_12_9";
  String DOC_TYPE_INVENTORY_OPIS = "INVENTORY_OPIS_FT_9_13";
  String DOC_TYPE_COMPARISON_STATEMENT = "INVENTORY_COMPARISON_FT_9_13";
  String DOC_TYPE_FINAL_ACT = "INVENTORY_FINAL_ACT_FT_9_13";

  GeneratedPdfDocumentDto generateHandoverActPdf(HandoverActPrintRequest request);

  GeneratedPdfDocumentDto generateInventoryOpisPdf(InventorySessionPrintRequest request);

  GeneratedPdfDocumentDto generateInventoryComparisonStatementPdf(
      InventorySessionPrintRequest request);

  GeneratedPdfDocumentDto generateInventoryFinalActPdf(InventorySessionPrintRequest request);

  GeneratedPdfDocumentDto attachEImzoSignatureToPdf(
      GeneratedPdfDocumentDto document,
      String signerPinfl,
      String signerTin,
      String signerFullName,
      String signerOrganization,
      String certificateSerialNumber);

  List<PrintTemplateDto> listTemplates();

  PrintTemplateDto updateTemplate(String templateKey, String xhtmlContent);
}
