package uz.ttpu.inventory.common.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI raqamliInventarOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TTPU \"Raqamli Inventar\" — Digital Inventory ERP API")
                        .version("v1.0.0")
                        .description("""
                                Unified Modular Monolith Backend API for Turin Polytechnic University in Tashkent (TTPU).
                                Serves the Web ERP portal, Android app, and iOS app across 16 strict Spring Modulith domains
                                and acts as the sole gateway to 1C Accounting, HEMIS, E-IMZO PKCS#7 digital signatures,
                                SMTP Email, Telegram Bot API, and Firebase Cloud Messaging (FCM).
                                """)
                        .contact(new Contact()
                                .name("TTPU IT & Digital Inventory Center")
                                .email("inventory.admin@ttpu.uz")
                                .url("https://ttpu.uz"))
                        .license(new License()
                                .name("TTPU Internal Enterprise License")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .name(BEARER_AUTH_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("RSA-2048 signed OAuth2 JWT access token obtained from POST /api/v1/auth/login")));
    }
}
