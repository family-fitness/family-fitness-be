package kr.ac.kookmin.familyfitness.shared.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import kr.ac.kookmin.familyfitness.FamilyfitnessApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

/** 개발용 기능(dev-login · 자동 로그인 · H2 콘솔)은 local · compose · test 프로필에서만 켤 수 있다. */
class DevFeatureGuardTest {
    private static MockEnvironment env(String... activeProfiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(activeProfiles);
        return env;
    }

    private static MockEnvironment allDevFeaturesOn(MockEnvironment env) {
        DevFeatureGuard.DEV_FEATURES.forEach(key -> env.setProperty(key, "true"));
        return env;
    }

    @Test
    @DisplayName("prod 에서 개발용 기능이 하나라도 켜지면 기동을 멈춘다")
    void prod_에서_개발용_기능이_하나라도_켜지면_기동을_멈춘다() {
        for (String key : DevFeatureGuard.DEV_FEATURES) {
            MockEnvironment prod = env("prod").withProperty(key, "true");
            assertThatThrownBy(() -> DevFeatureGuard.check(prod))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(key)
                    .hasMessageContaining("[prod]");
        }
    }

    @Test
    @DisplayName(
            "prod 가 활성이면 local · compose · test 가 함께 있어도 개발용 기능이 켜지면 기동을 멈춘다 — prod,compose 로 띄우면 시간 이동 · 자동 로그인이 운영에서 켜졌다")
    void prod_가_있으면_개발_프로필이_함께_있어도_멈춘다() {
        for (String dev : new String[] {"local", "compose", "test"}) {
            MockEnvironment mixed = allDevFeaturesOn(env("prod", dev));
            assertThatThrownBy(() -> DevFeatureGuard.check(mixed))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("prod")
                    .hasMessageContaining("app.dev.time-travel.enabled");
        }
        // 개발용 기능이 모두 꺼져 있으면 prod 와 개발 프로필이 함께 있어도 뜬다
        assertThatCode(() -> DevFeatureGuard.check(env("prod", "compose"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod 에서 시간 이동을 켜면 기동을 멈춘다")
    void prod_에서_시간_이동을_켜면_기동을_멈춘다() {
        MockEnvironment prod = env("prod").withProperty("app.dev.time-travel.enabled", "true");
        assertThatThrownBy(() -> DevFeatureGuard.check(prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.dev.time-travel.enabled");
    }

    @Test
    @DisplayName("프로필 없이 자동 로그인만 켜도 기동을 멈춘다")
    void 프로필_없이_자동_로그인만_켜도_기동을_멈춘다() {
        MockEnvironment none = env().withProperty("app.auth.dev-auto-login.enabled", "true");
        assertThatThrownBy(() -> DevFeatureGuard.check(none))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.dev-auto-login.enabled");
    }

    @Test
    @DisplayName("환경변수로 켠 자동 로그인도 잡는다")
    void 환경변수로_켠_자동_로그인도_잡는다() {
        MockEnvironment prod = env("prod");
        prod.getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource(
                        "systemEnvironment", Map.of("APP_AUTH_DEVAUTOLOGIN_ENABLED", "true")));
        assertThatThrownBy(() -> DevFeatureGuard.check(prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.dev-auto-login.enabled");
    }

    @Test
    @DisplayName("local · compose · test 에서는 개발용 기능을 모두 켜도 뜬다")
    void local_compose_test_에서는_개발용_기능을_모두_켜도_뜬다() {
        for (String profile : new String[] {"local", "compose", "test"}) {
            assertThatCode(() -> DevFeatureGuard.check(allDevFeaturesOn(env(profile))))
                    .doesNotThrowAnyException();
        }
        assertThatCode(() -> DevFeatureGuard.check(allDevFeaturesOn(env("compose", "extra"))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bootRun 처럼 기본 프로필이 local 이면 뜬다")
    void bootRun_처럼_기본_프로필이_local_이면_뜬다() {
        MockEnvironment bootRun = allDevFeaturesOn(env());
        bootRun.setDefaultProfiles("local");
        assertThatCode(() -> DevFeatureGuard.check(bootRun)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("개발용 기능이 모두 꺼져 있으면 prod 도 프로필 없음도 뜬다")
    void 개발용_기능이_모두_꺼져_있으면_prod_도_프로필_없음도_뜬다() {
        assertThatCode(() -> DevFeatureGuard.check(env("prod"))).doesNotThrowAnyException();
        MockEnvironment explicitOff = env();
        DevFeatureGuard.DEV_FEATURES.forEach(key -> explicitOff.setProperty(key, "false"));
        assertThatCode(() -> DevFeatureGuard.check(explicitOff)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("심사용 계정 로그인은 개발용 기능이 아니라 prod 에서 켜도 뜬다")
    void 심사용_계정_로그인은_prod_에서_켜도_뜬다() {
        MockEnvironment prod = env("prod").withProperty("app.auth.review-login.enabled", "true");
        assertThatCode(() -> DevFeatureGuard.check(prod)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("spring.factories 로 등록돼 실제 기동에서 컨텍스트를 올리기 전에 멈춘다")
    void spring_factories_로_등록돼_실제_기동에서_컨텍스트를_올리기_전에_멈춘다() {
        SpringApplication app = new SpringApplication(FamilyfitnessApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("prod");
        assertThatThrownBy(() -> app.run("--app.auth.dev-auto-login.enabled=true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.dev-auto-login.enabled");
    }
}
