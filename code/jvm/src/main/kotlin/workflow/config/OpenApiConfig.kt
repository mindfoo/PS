package org.workflow.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
/** Configures Swagger/OpenAPI metadata and cookie-based security scheme. */
class OpenApiConfig {

    @Bean
    fun workflowOpenApi(): OpenAPI {
        val cookieSchemeName = "cookieAuth"

        return OpenAPI()
            .info(
                Info()
                    .title("Workflow Platform API")
                    .version("v1")
                    .description(
                        "REST API for workflow orchestration. " +
                        "Authentication uses an HttpOnly cookie named **token** " +
                        "obtained via `POST /api/auth/login`."
                    )
            )
            .components(
                Components().addSecuritySchemes(
                    cookieSchemeName,
                    SecurityScheme()
                        .name("token")                          // must match COOKIE_NAME constant
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.COOKIE)
                )
            )
            .addSecurityItem(SecurityRequirement().addList(cookieSchemeName))
    }
}
