package uz.ttpu.inventory.asset.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uz.ttpu.inventory.asset.service.AssetService;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "04. Public QR Item Card",
        description = "Publicly accessible QR scan endpoints (/a/{id} and /api/v1/a/{id}) returning ownership information ('TTPU mulki'), inventory number, and custodian contact phone."
)
public class PublicItemCardController {

    private final AssetService assetService;

    @GetMapping({"/a/{id}", "/api/v1/a/{id}"})
    @SecurityRequirements
    @Operation(
            summary = "Get public item card by 10-character Base32 QR ID",
            description = "Public endpoint for QR code stickers. Returns item name, inventory number, 'TTPU mulki' ownership label, and contact phone number, while recording a READ entry in audit_record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public QR item card retrieved"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Item not found for QR ID (ERR_RESOURCE_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<PublicItemCardDto> getPublicItemCard(@PathVariable("id") String qrId) {
        return ResponseEntity.ok(assetService.getPublicItemCard(qrId));
    }
}
