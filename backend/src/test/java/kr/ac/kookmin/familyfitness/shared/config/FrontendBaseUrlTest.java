package kr.ac.kookmin.familyfitness.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 초대 링크의 앞머리(app.frontend-base-url). 운영에서 빠뜨리면 localhost 링크가 나가지 않고 기동이 멈춘다. */
class FrontendBaseUrlTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Props {}

    /** application.properties + application-{profile}.properties 를 실제로 읽는다. */
    private static ApplicationContextRunner withProfile(String profile) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .withUserConfiguration(Props.class);
    }

    @Test
    @DisplayName("prod 에서 APP_FRONTEND_BASE_URL 이 없으면 기동이 멈춘다 — 기본값(localhost)으로 초대 링크가 나가지 않는다")
    void prod_에서_APP_FRONTEND_BASE_URL_이_없으면_기동이_멈춘다() {
        withProfile("prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("APP_FRONTEND_BASE_URL");
        });
    }

    @Test
    @DisplayName("prod 에서 APP_FRONTEND_BASE_URL 을 주면 그 주소를 쓴다")
    void prod_에서_APP_FRONTEND_BASE_URL_을_주면_그_주소를_쓴다() {
        withProfile("prod")
                .withPropertyValues("APP_FRONTEND_BASE_URL=https://fit.example.com")
                .run(context -> assertThat(context.getBean(AppProperties.class).frontendBaseUrl())
                        .isEqualTo("https://fit.example.com"));
    }

    @Test
    @DisplayName("local 은 FE 개발 서버 주소가 기본값이다")
    void local_은_FE_개발_서버_주소가_기본값이다() {
        withProfile("local")
                .run(context -> assertThat(context.getBean(AppProperties.class).frontendBaseUrl())
                        .isEqualTo("http://localhost:3000"));
    }

    @Test
    @DisplayName("http(s):// 로 시작하지 않는 값은 받지 않는다")
    void http_로_시작하지_않는_값은_받지_않는다() {
        for (String bad : new String[] {"", "  ", "fit.example.com", "${APP_FRONTEND_BASE_URL}"}) {
            assertThatThrownBy(() -> new AppProperties(
                            "Asia/Seoul",
                            bad,
                            new AppProperties.Cors(),
                            new AppProperties.Auth(),
                            new AppProperties.Ai()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("APP_FRONTEND_BASE_URL");
        }
    }
}
