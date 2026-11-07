package uz.ttpu.inventory.report.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;
import uz.ttpu.inventory.integration.api.EImzoSignatureLookupApi;
import uz.ttpu.inventory.integration.api.EImzoSignatureRecordDto;
import uz.ttpu.inventory.report.api.EImzoPdfMetadataDto;
import uz.ttpu.inventory.report.api.GeneratedPdfDocumentDto;
import uz.ttpu.inventory.report.api.HandoverActPrintRequest;
import uz.ttpu.inventory.report.api.InventorySessionPrintRequest;
import uz.ttpu.inventory.report.api.PrintFormApi;
import uz.ttpu.inventory.report.api.PrintTemplateDto;

@Service
public class PdfPrintFormService implements PrintFormApi {

  public static final String TEMPLATE_HANDOVER_ACT = "ft-12-9-handover-act";
  public static final String TEMPLATE_INVENTORY_OPIS = "ft-9-13-inventory-opis";
  public static final String TEMPLATE_COMPARISON_STATEMENT = "ft-9-13-comparison-statement";
  public static final String TEMPLATE_FINAL_ACT = "ft-9-13-final-act";

  private static final Map<String, TemplateDescriptor> DEFAULT_TEMPLATES =
      Map.of(
          TEMPLATE_HANDOVER_ACT,
              new TemplateDescriptor(
                  TEMPLATE_HANDOVER_ACT,
                  FORM_FT_12_9,
                  "FT-12.9 Official Handover Act (Topshirish-Qabul Qilish Dalolatnomasi) for Issues"
                      + " & Transfers",
                  "templates/print/ft-12-9-handover-act.html"),
          TEMPLATE_INVENTORY_OPIS,
              new TemplateDescriptor(
                  TEMPLATE_INVENTORY_OPIS,
                  FORM_FT_9_13,
                  "FT-9.13 Official Inventory List (Inventarizatsiya Ro'yxati / Opis)",
                  "templates/print/ft-9-13-inventory-opis.html"),
          TEMPLATE_COMPARISON_STATEMENT,
              new TemplateDescriptor(
                  TEMPLATE_COMPARISON_STATEMENT,
                  FORM_FT_9_13,
                  "FT-9.13 Official Comparison Statement (Solishtirma Vedomost / Сличительная"
                      + " ведомость)",
                  "templates/print/ft-9-13-comparison-statement.html"),
          TEMPLATE_FINAL_ACT,
              new TemplateDescriptor(
                  TEMPLATE_FINAL_ACT,
                  FORM_FT_9_13,
                  "FT-9.13 Official Final Inventory Act (Yakuniy Inventarizatsiya Dalolatnomasi)",
                  "templates/print/ft-9-13-final-act.html"));

  private final EImzoSignatureLookupApi eimzoSignatureLookupApi;
  private final TemplateEngine stringTemplateEngine;
  private final Map<String, CustomTemplateOverride> customTemplates = new ConcurrentHashMap<>();

  public PdfPrintFormService(EImzoSignatureLookupApi eimzoSignatureLookupApi) {
    this.eimzoSignatureLookupApi = eimzoSignatureLookupApi;
    this.stringTemplateEngine = createStringTemplateEngine();
  }

  @Override
  public GeneratedPdfDocumentDto generateHandoverActPdf(HandoverActPrintRequest request) {
    List<EImzoPdfMetadataDto> signatures = loadSignatures(request.operationId());
    Instant now = Instant.now();

    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("formCode", FORM_FT_12_9);
    variables.put("req", request);
    variables.put("signatures", signatures);
    variables.put("generatedAt", now);

    byte[] rawPdf = renderTemplateToPdf(TEMPLATE_HANDOVER_ACT, variables);
    byte[] enrichedPdf =
        embedMetadataIntoPdf(
            rawPdf,
            FORM_FT_12_9,
            DOC_TYPE_HANDOVER_ACT,
            request.operationId(),
            request.documentNumber(),
            signatures);

    return new GeneratedPdfDocumentDto(
        enrichedPdf,
        "ft-12-9-handover-act-" + sanitizeFileName(request.documentNumber()) + ".pdf",
        FORM_FT_12_9,
        DOC_TYPE_HANDOVER_ACT,
        request.operationId(),
        request.documentNumber(),
        computeSha256Hex(enrichedPdf),
        signatures,
        now);
  }

  @Override
  public GeneratedPdfDocumentDto generateInventoryOpisPdf(InventorySessionPrintRequest request) {
    return renderInventorySessionPdf(
        TEMPLATE_INVENTORY_OPIS,
        DOC_TYPE_INVENTORY_OPIS,
        "ft-9-13-opis-" + sanitizeFileName(request.sessionCode()) + ".pdf",
        request);
  }

  @Override
  public GeneratedPdfDocumentDto generateInventoryComparisonStatementPdf(
      InventorySessionPrintRequest request) {
    return renderInventorySessionPdf(
        TEMPLATE_COMPARISON_STATEMENT,
        DOC_TYPE_COMPARISON_STATEMENT,
        "ft-9-13-comparison-" + sanitizeFileName(request.sessionCode()) + ".pdf",
        request);
  }

  @Override
  public GeneratedPdfDocumentDto generateInventoryFinalActPdf(
      InventorySessionPrintRequest request) {
    return renderInventorySessionPdf(
        TEMPLATE_FINAL_ACT,
        DOC_TYPE_FINAL_ACT,
        "ft-9-13-final-act-" + sanitizeFileName(request.sessionCode()) + ".pdf",
        request);
  }

  @Override
  public GeneratedPdfDocumentDto attachEImzoSignatureToPdf(
      GeneratedPdfDocumentDto document,
      String signerPinfl,
      String signerTin,
      String signerFullName,
      String signerOrganization,
      String certificateSerialNumber) {
    EImzoSignatureRecordDto verifiedRecord =
        eimzoSignatureLookupApi.verifyAndRecordSignedPdfBlob(
            document.documentType(),
            document.documentId(),
            document.documentNumber(),
            document.pdfBytes(),
            signerPinfl,
            signerTin,
            signerFullName,
            signerOrganization,
            certificateSerialNumber);

    List<EImzoPdfMetadataDto> updatedSignatures = new ArrayList<>(document.eimzoSignatures());
    updatedSignatures.add(0, toMetadataDto(verifiedRecord));

    byte[] signedPdfBytes =
        embedMetadataIntoPdf(
            document.pdfBytes(),
            document.formCode(),
            document.documentType(),
            document.documentId(),
            document.documentNumber(),
            updatedSignatures);

    return new GeneratedPdfDocumentDto(
        signedPdfBytes,
        document.fileName(),
        document.formCode(),
        document.documentType(),
        document.documentId(),
        document.documentNumber(),
        computeSha256Hex(signedPdfBytes),
        updatedSignatures,
        Instant.now());
  }

  @Override
  public List<PrintTemplateDto> listTemplates() {
    List<String> orderedKeys =
        List.of(
            TEMPLATE_HANDOVER_ACT,
            TEMPLATE_INVENTORY_OPIS,
            TEMPLATE_COMPARISON_STATEMENT,
            TEMPLATE_FINAL_ACT);
    List<PrintTemplateDto> result = new ArrayList<>();
    for (String key : orderedKeys) {
      TemplateDescriptor descriptor = DEFAULT_TEMPLATES.get(key);
      CustomTemplateOverride override = customTemplates.get(key);
      String content =
          override != null
              ? override.xhtmlContent()
              : loadClasspathTemplate(descriptor.classpathLocation());
      Instant updatedAt = override != null ? override.updatedAt() : Instant.EPOCH;
      result.add(
          new PrintTemplateDto(
              descriptor.templateKey(),
              descriptor.formCode(),
              descriptor.description(),
              content,
              override != null,
              updatedAt));
    }
    return result;
  }

  @Override
  public PrintTemplateDto updateTemplate(String templateKey, String xhtmlContent) {
    TemplateDescriptor descriptor = DEFAULT_TEMPLATES.get(templateKey);
    if (descriptor == null) {
      throw new ResourceNotFoundException("Print template not found: " + templateKey);
    }
    if (xhtmlContent == null || xhtmlContent.isBlank()) {
      throw new IllegalArgumentException("XHTML content must not be blank");
    }
    validateXhtml(xhtmlContent);
    Instant now = Instant.now();
    customTemplates.put(templateKey, new CustomTemplateOverride(xhtmlContent, now));
    return new PrintTemplateDto(
        descriptor.templateKey(),
        descriptor.formCode(),
        descriptor.description(),
        xhtmlContent,
        true,
        now);
  }

  private static void validateXhtml(String xhtml) {
    try {
      javax.xml.parsers.DocumentBuilderFactory factory =
          javax.xml.parsers.DocumentBuilderFactory.newInstance();
      factory.setValidating(false);
      factory.setNamespaceAware(false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
      builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xhtml)));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid XHTML template syntax: " + ex.getMessage(), ex);
    }
  }

  private GeneratedPdfDocumentDto renderInventorySessionPdf(
      String templateKey,
      String documentType,
      String fileName,
      InventorySessionPrintRequest request) {
    List<EImzoPdfMetadataDto> signatures = loadSignatures(request.sessionId());
    Instant now = Instant.now();

    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("formCode", FORM_FT_9_13);
    variables.put("req", request);
    variables.put("signatures", signatures);
    variables.put("generatedAt", now);

    byte[] rawPdf = renderTemplateToPdf(templateKey, variables);
    byte[] enrichedPdf =
        embedMetadataIntoPdf(
            rawPdf,
            FORM_FT_9_13,
            documentType,
            request.sessionId(),
            request.sessionCode(),
            signatures);

    return new GeneratedPdfDocumentDto(
        enrichedPdf,
        fileName,
        FORM_FT_9_13,
        documentType,
        request.sessionId(),
        request.sessionCode(),
        computeSha256Hex(enrichedPdf),
        signatures,
        now);
  }

  private byte[] renderTemplateToPdf(String templateKey, Map<String, Object> variables) {
    String templateSource = resolveTemplateSource(templateKey);
    Context context = new Context(Locale.ROOT, variables);
    String renderedXhtml = stringTemplateEngine.process(templateSource, context);

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withHtmlContent(renderedXhtml, null);
      builder.toStream(outputStream);
      builder.run();
      return outputStream.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to generate PDF from template " + templateKey, ex);
    }
  }

  private byte[] embedMetadataIntoPdf(
      byte[] pdfBytes,
      String formCode,
      String documentType,
      UUID documentId,
      String documentNumber,
      List<EImzoPdfMetadataDto> signatures) {
    try (PDDocument document = PDDocument.load(pdfBytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDDocumentInformation info = document.getDocumentInformation();
      if (info == null) {
        info = new PDDocumentInformation();
      }
      info.setTitle(formCode + " - " + documentType + " (" + documentNumber + ")");
      info.setAuthor("TTPU Raqamli Inventar ERP");
      info.setCreator("TTPU OpenHTMLtoPDF + PDFBox Document Engine");
      info.setSubject("Official Print Form " + formCode + " | Document " + documentNumber);

      info.setCustomMetadataValue("TTPU-Form-Code", formCode);
      info.setCustomMetadataValue("TTPU-Document-Type", documentType);
      info.setCustomMetadataValue(
          "TTPU-Document-Id", documentId != null ? documentId.toString() : "");
      info.setCustomMetadataValue(
          "TTPU-Document-Number", documentNumber != null ? documentNumber : "");

      boolean signed = signatures != null && !signatures.isEmpty();
      info.setCustomMetadataValue("EIMZO-Signed", String.valueOf(signed));
      info.setCustomMetadataValue(
          "EIMZO-Signature-Count", String.valueOf(signed ? signatures.size() : 0));

      if (signed) {
        info.setCustomMetadataValue(
            "EIMZO-PKCS7-Ids",
            signatures.stream()
                .map(EImzoPdfMetadataDto::pkcs7SignatureId)
                .collect(Collectors.joining(",")));
        info.setCustomMetadataValue(
            "EIMZO-Signer-PINFLs",
            signatures.stream()
                .map(EImzoPdfMetadataDto::signerPinfl)
                .collect(Collectors.joining(",")));
        info.setCustomMetadataValue(
            "EIMZO-Signers",
            signatures.stream()
                .map(EImzoPdfMetadataDto::signerFullName)
                .collect(Collectors.joining(",")));
        info.setCustomMetadataValue(
            "EIMZO-Certificate-Serials",
            signatures.stream()
                .map(EImzoPdfMetadataDto::certificateSerialNumber)
                .collect(Collectors.joining(",")));
        String keywords =
            "TTPU,"
                + formCode
                + ","
                + documentType
                + ",E-IMZO,"
                + signatures.stream()
                    .map(EImzoPdfMetadataDto::pkcs7SignatureId)
                    .collect(Collectors.joining(","));
        info.setKeywords(keywords);
      } else {
        info.setKeywords("TTPU," + formCode + "," + documentType + ",UNSIGNED");
      }

      document.setDocumentInformation(info);
      document.save(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to embed E-IMZO metadata into PDF document", ex);
    }
  }

  private List<EImzoPdfMetadataDto> loadSignatures(UUID documentId) {
    if (documentId == null || eimzoSignatureLookupApi == null) {
      return List.of();
    }
    return eimzoSignatureLookupApi.listSignaturesForDocument(documentId).stream()
        .map(this::toMetadataDto)
        .toList();
  }

  private EImzoPdfMetadataDto toMetadataDto(EImzoSignatureRecordDto dto) {
    return new EImzoPdfMetadataDto(
        dto.id(),
        dto.pkcs7SignatureId(),
        dto.signerPinfl(),
        dto.signerTin(),
        dto.signerFullName(),
        dto.signerOrganization(),
        dto.certificateSerialNumber(),
        dto.pdfSha256Hash(),
        dto.verified(),
        dto.signedAt(),
        dto.verifiedAt());
  }

  private String resolveTemplateSource(String templateKey) {
    CustomTemplateOverride override = customTemplates.get(templateKey);
    if (override != null) {
      return override.xhtmlContent();
    }
    TemplateDescriptor descriptor = DEFAULT_TEMPLATES.get(templateKey);
    if (descriptor == null) {
      throw new ResourceNotFoundException("Print template not found: " + templateKey);
    }
    return loadClasspathTemplate(descriptor.classpathLocation());
  }

  private String loadClasspathTemplate(String location) {
    try (InputStream in = new ClassPathResource(location).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load classpath print template: " + location, ex);
    }
  }

  private static TemplateEngine createStringTemplateEngine() {
    StringTemplateResolver resolver = new StringTemplateResolver();
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCacheable(false);
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setEnableSpringELCompiler(true);
    engine.setTemplateResolver(resolver);
    return engine;
  }

  private static String sanitizeFileName(String raw) {
    if (raw == null || raw.isBlank()) {
      return "document";
    }
    return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static String computeSha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  private record TemplateDescriptor(
      String templateKey, String formCode, String description, String classpathLocation) {}

  private record CustomTemplateOverride(String xhtmlContent, Instant updatedAt) {}
}
