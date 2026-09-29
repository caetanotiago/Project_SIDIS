package isep.psoft.aisafe.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration. Declares the API metadata and a JWT Bearer
 * security scheme so the Swagger UI "Authorize" button can attach the token
 * obtained from POST /auth, allowing protected endpoints to be tested.
 *
 * Swagger UI: /swagger-ui.html  |  OpenAPI spec: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI aisafeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AISafe - Flight Routes Service API")
                        .description("REST API for the AISafe system (PSOFT 2025/26). "
                                + "Use a JWT issued by the auth service via the Authorize button.")
                        .version("v2"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
