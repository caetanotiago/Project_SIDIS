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
 * (in development: POST /auth/dev-token), allowing protected endpoints to be tested.
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
                        .title("AISafe - Airports Service API")
                        .description("Airports microservice of the distributed AISafe "
                                + "system (SIDIS 2026/27). Every GET returns the data of all replicas "
                                + "(peer-to-peer forwarding/aggregation); X-Partial-Response: true means a "
                                + "replica did not answer. Authorize with a JWT — in development obtain one "
                                + "with POST /auth/dev-token {\"username\":\"atcc1\",\"roles\":[\"ATCC\"]}.")
                        .version("v3"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
