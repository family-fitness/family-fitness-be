package kr.ac.kookmin.familyfitness.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("우리가족 체력키움 API")
                        .version("v1")
                        .description("성공은 payload 그대로, 실패는 `{\"error\": {\"code\", \"message\"}}` 한 형태다. "
                                + "`error.message` 는 개발자용이며 화면에 그대로 노출하지 않는다. "
                                + "인증은 `Authorization: Bearer <accessToken>`."))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
