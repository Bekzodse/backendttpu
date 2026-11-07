package uz.ttpu.inventory.integration.service;

import java.util.List;
import org.springframework.stereotype.Component;
import uz.ttpu.inventory.integration.api.HemisDepartmentPayloadDto;
import uz.ttpu.inventory.integration.api.HemisEmployeePayloadDto;

@Component
public class HemisExternalClientStub {

    public List<HemisDepartmentPayloadDto> fetchDailyDepartmentUpdates() {
        return List.of(
                new HemisDepartmentPayloadDto("HEMIS-DEP-IT", "Information Technologies & AI Department"),
                new HemisDepartmentPayloadDto("HEMIS-DEP-MECH", "Mechanical & Aerospace Engineering Department")
        );
    }

    public List<HemisEmployeePayloadDto> fetchDailyEmployeeUpdates() {
        return List.of(
                new HemisEmployeePayloadDto(
                        "HEMIS-EMP-1001",
                        "TTPU-H1001",
                        "Azizbek",
                        "Tursunov",
                        "Rustamovich",
                        "azizbek.tursunov@ttpu.uz",
                        "+998901234501",
                        "Associate Professor",
                        "HEMIS-DEP-IT"
                ),
                new HemisEmployeePayloadDto(
                        "HEMIS-EMP-1002",
                        "TTPU-H1002",
                        "Malika",
                        "Yusupova",
                        "Anvarovna",
                        "malika.yusupova@ttpu.uz",
                        "+998901234502",
                        "Laboratory Engineer",
                        "HEMIS-DEP-MECH"
                )
        );
    }
}
