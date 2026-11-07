package uz.ttpu.inventory.common.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.ttpu.inventory.common.domain.ErrorCode;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI raqamliInventarOpenAPI() {
        List<String> errorCodes = Arrays.stream(ErrorCode.values())
                .map(Enum::name)
                .toList();

        StringSchema errorCodeSchema = new StringSchema();
        errorCodeSchema.setEnum(errorCodes);
        errorCodeSchema.setDescription("Stable machine-readable error code");
        errorCodeSchema.setExample(ErrorCode.ERR_RESOURCE_NOT_FOUND.name());

        ObjectSchema localizedMessagesSchema = new ObjectSchema();
        localizedMessagesSchema.setDescription("Localized error messages in Uzbek (uz), Russian (ru), and English (en)");
        localizedMessagesSchema.addProperty("uz", new StringSchema().example(ErrorCode.ERR_RESOURCE_NOT_FOUND.getMessageUz()));
        localizedMessagesSchema.addProperty("ru", new StringSchema().example(ErrorCode.ERR_RESOURCE_NOT_FOUND.getMessageRu()));
        localizedMessagesSchema.addProperty("en", new StringSchema().example(ErrorCode.ERR_RESOURCE_NOT_FOUND.getMessageEn()));

        ObjectSchema problemDetailSchema = new ObjectSchema();
        problemDetailSchema.setDescription("RFC 7807 Problem Details (application/problem+json) with stable errorCode and uz/ru/en localized messages");
        problemDetailSchema.addProperty("type", new StringSchema().format("uri").example("https://api.ttpu.uz/problems/err-resource-not-found"));
        problemDetailSchema.addProperty("title", new StringSchema().example("Resource Not Found"));
        problemDetailSchema.addProperty("status", new IntegerSchema().example(404));
        problemDetailSchema.addProperty("detail", new StringSchema().example("Resource not found"));
        problemDetailSchema.addProperty("instance", new StringSchema().format("uri").example("/api/v1/assets/00000000-0000-0000-0000-000000000201"));
        problemDetailSchema.addProperty("errorCode", errorCodeSchema);
        problemDetailSchema.addProperty("localizedMessage", new StringSchema().example(ErrorCode.ERR_RESOURCE_NOT_FOUND.getMessageUz()));
        problemDetailSchema.addProperty("messages", localizedMessagesSchema);
        problemDetailSchema.addProperty("timestamp", new StringSchema().format("date-time").example("2026-11-07T10:00:00Z"));

        return new OpenAPI()
                .info(new Info()
                        .title("TTPU \"Raqamli Inventar\" — Digital Inventory ERP API")
                        .version("v1.0.0")
                        .description("""
                                Unified Modular Monolith Backend API for Turin Polytechnic University in Tashkent (TTPU).
                                Serves the Web ERP portal (Next.js) and Mobile apps (Kotlin Multiplatform - Android & iOS)
                                across 16 strict Spring Modulith domains, following an OpenAPI 3.0 Contract-First architecture
                                with RFC 7807 (application/problem+json) errors and uz/ru/en localization.
                                """)
                        .contact(new Contact()
                                .name("TTPU IT & Digital Inventory Center")
                                .email("inventory.admin@ttpu.uz")
                                .url("https://ttpu.uz"))
                        .license(new License()
                                .name("TTPU Internal Enterprise License")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local / Docker Backend API Server"),
                        new Server().url("http://localhost:4010").description("Stoplight Prism Mock Server (for Next.js & KMP Clients)")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .name(BEARER_AUTH_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("RSA-2048 signed OAuth2 JWT access token obtained from POST /api/v1/auth/login"))
                        .addSchemas("ProblemDetail", problemDetailSchema));
    }

    @Bean
    public OpenApiCustomizer rfc7807ProblemDetailCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            Content problemContent = new Content().addMediaType(
                    org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))
            );

            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                ApiResponses responses = operation.getResponses();
                if (responses == null) {
                    responses = new ApiResponses();
                    operation.setResponses(responses);
                }
                responses.computeIfAbsent("400", code -> new ApiResponse()
                        .description("Bad Request / Validation Error (ERR_VALIDATION_FAILED / ERR_BAD_REQUEST)")
                        .content(problemContent));
                responses.computeIfAbsent("401", code -> new ApiResponse()
                        .description("Unauthorized (ERR_AUTH_FAILED)")
                        .content(problemContent));
                responses.computeIfAbsent("403", code -> new ApiResponse()
                        .description("Forbidden / Insufficient Scoped Permission (ERR_ACCESS_DENIED)")
                        .content(problemContent));
                responses.computeIfAbsent("404", code -> new ApiResponse()
                        .description("Resource Not Found (ERR_RESOURCE_NOT_FOUND)")
                        .content(problemContent));
                responses.computeIfAbsent("409", code -> new ApiResponse()
                        .description("State Conflict / Duplicate Serial Number (ERR_STATE_CONFLICT)")
                        .content(problemContent));
            }));
        };
    }
}
