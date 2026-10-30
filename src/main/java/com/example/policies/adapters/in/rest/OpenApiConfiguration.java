package com.example.policies.adapters.in.rest;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Rental Policy API",
            version = "v1",
            description = "Demo API for managing rental policies"))
public class OpenApiConfiguration {

  @Bean
  OpenAPI policyOpenApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "ApiKeyAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("x-api-key")))
        .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"));
  }
}
