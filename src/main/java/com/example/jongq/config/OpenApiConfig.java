package com.example.jongq.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Configuration
public class OpenApiConfig {
    @Bean
    SwaggerIndexTransformer csrfSwaggerTransformer(SwaggerUiConfigProperties config,
            SwaggerUiOAuthProperties oauth, SwaggerWelcomeCommon welcome, ObjectMapperProvider mapper) {
        return new SwaggerIndexPageTransformer(config, oauth, welcome, mapper) {
            @Override
            public Resource transform(HttpServletRequest request, Resource resource,
                    ResourceTransformerChain chain) throws IOException {
                Resource transformed = super.transform(request, resource, chain);
                if (!"swagger-initializer.js".equals(resource.getFilename())) return transformed;
                String script;
                try (var input = transformed.getInputStream()) {
                    script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                // Obtain a fresh session token on every mutation because login invalidates the old token.
                String interceptor = """
                    requestInterceptor: async function(request) {
                      if (/^(POST|PUT|PATCH|DELETE)$/i.test(request.method)) {
                        const response = await fetch('../api/auth/csrf', { credentials: 'same-origin' });
                        if (!response.ok) throw new Error('Unable to fetch CSRF token');
                        const csrf = await response.json();
                        request.headers[csrf.headerName] = csrf.token;
                      }
                      return request;
                    },
                    """;
                return new TransformedResource(transformed,
                    script.replace("SwaggerUIBundle({", "SwaggerUIBundle({\n" + interceptor)
                        .getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    @Bean
    OpenAPI bookingOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Jongq Booking API")
            .version("1.0")
            .description("Barber booking API. To access admin endpoints, execute POST /api/auth/login "
                + "first. Swagger UI uses the session cookie and fetches CSRF tokens automatically."));
    }
}
