package uz.ttpu.inventory.integration.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import uz.ttpu.inventory.integration.api.OneCAssetRecordDto;

@Component
public class OneCExternalClientStub {

  private boolean useCustomRecords = false;
  private final List<OneCAssetRecordDto> customRecords = new CopyOnWriteArrayList<>();

  public List<OneCAssetRecordDto> fetch1CAssetRegistry() {
    if (useCustomRecords) {
      return new ArrayList<>(customRecords);
    }

    return List.of(
        new OneCAssetRecordDto(
            "INV-2026-0001",
            "Dell OptiPlex 7090 Desktop",
            new BigDecimal("12500000.00"),
            "EMP-001",
            "1C-GUID-001"),
        new OneCAssetRecordDto(
            "INV-2026-0002",
            "HP LaserJet Pro M404dn",
            new BigDecimal("4200000.00"),
            "EMP-002",
            "1C-GUID-002"),
        new OneCAssetRecordDto(
            "INV-2026-0003",
            "Cisco Catalyst 2960-X Switch",
            new BigDecimal("8900000.00"),
            "EMP-001",
            "1C-GUID-003"));
  }

  public void setCustomRecords(List<OneCAssetRecordDto> records) {
    this.useCustomRecords = true;
    this.customRecords.clear();
    if (records != null) {
      this.customRecords.addAll(records);
    }
  }

  public void reset() {
    this.useCustomRecords = false;
    this.customRecords.clear();
  }
}
