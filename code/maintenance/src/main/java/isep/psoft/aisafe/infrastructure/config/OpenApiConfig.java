package isep.psoft.aisafe.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI aisafeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AISafe - Maintenance Service API")
                        .description("Maintenance microservice of the distributed AISafe "
                                + "system (SIDIS 2026/27): templates and records (WP#4). Every GET returns the "
                                + "data of all replicas (peer-to-peer forwarding/aggregation); "
                                + "X-Partial-Response: true means a replica did not answer. Authorize with a JWT — "
                                + "in development obtain one with POST /auth/dev-token, e.g. "
                                + "{\"username\":\"tech1\",\"roles\":[\"MAINTENANCE_TECHNICIAN\"]}, "
                                + "MAINTENANCE_SUPERVISOR or ATCC.")
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
