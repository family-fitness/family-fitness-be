package kr.ac.kookmin.familyfitness.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 심사용 계정 로그인을 받는 날: 켜져 있고, 끝나는 날(until)이 있으면 그날까지(그날 포함). */
class ReviewLoginPropertiesTest {
    private static final LocalDate UNTIL = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("끝나는 날까지는 받고, 다음 날부터는 받지 않는다")
    void 끝나는_날까지_받는다() {
        AppProperties.ReviewLogin login = new AppProperties.ReviewLogin(true, UNTIL);

        assertThat(login.openOn(UNTIL.minusDays(1))).isTrue();
        assertThat(login.openOn(UNTIL)).isTrue();
        assertThat(login.openOn(UNTIL.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("끝나는 날이 없으면 켜져 있는 동안 늘 받고, 꺼져 있으면 끝나는 날과 상관없이 받지 않는다")
    void 끝나는_날이_없으면_늘_받는다() {
        assertThat(new AppProperties.ReviewLogin(true, null).openOn(LocalDate.of(2099, 1, 1)))
                .isTrue();
        assertThat(new AppProperties.ReviewLogin(false, UNTIL).openOn(UNTIL.minusDays(1)))
                .isFalse();
        assertThat(new AppProperties.ReviewLogin().openOn(UNTIL)).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Props {}

    @Test
    @DisplayName("설정값(문자열)을 날짜로 읽는다 — YYYY-MM-DD 는 그날, 빈 값은 끝나는 날 없음")
    void 설정값을_날짜로_읽는다() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withPropertyValues("app.frontend-base-url=https://fit.example.com")
                .withUserConfiguration(Props.class);

        runner.withPropertyValues("app.auth.review-login.until=2026-10-31")
                .run(context -> assertThat(context.getBean(AppProperties.class)
                                .auth()
                                .reviewLogin()
                                .until())
                        .isEqualTo(UNTIL));
        runner.withPropertyValues("app.auth.review-login.until=")
                .run(context -> assertThat(context.getBean(AppProperties.class)
                                .auth()
                                .reviewLogin()
                                .until())
                        .isNull());
    }
}
