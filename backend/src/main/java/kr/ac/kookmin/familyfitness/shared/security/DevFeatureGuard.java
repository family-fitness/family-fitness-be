package kr.ac.kookmin.familyfitness.shared.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * 개발용 기능(dev-login · 자동 로그인 · H2 콘솔)은 local · compose · test 프로필에서만 켤 수 있다.
 * 다른 프로필(prod, 프로필 없음 등)에서 하나라도 켜져 있으면 컨텍스트를 올리기 전에 기동을 멈춘다.
 * 자동 로그인이 켜지면 토큰 없는 요청이 시드 데모 부모가 되고, X-Dev-User-Id 헤더로 아무 계정이나 될 수 있다.
 *
 * <p>`META-INF/spring.factories` 로 등록한다. 프로필 파일을 읽는 ConfigDataEnvironmentPostProcessor 뒤에 돌도록
 * 가장 낮은 우선순위를 준다. DataSource · Flyway 보다 먼저 돌아서, 멈출 때는 DB 에 아무것도 쓰지 않는다.
 */
public class DevFeatureGuard implements EnvironmentPostProcessor, Ordered {
    static final Profiles DEV_PROFILES = Profiles.of("local", "compose", "test");

    static final List<String> DEV_FEATURES =
            List.of("app.auth.dev-login.enabled", "app.auth.dev-auto-login.enabled", "spring.h2.console.enabled");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        check(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** 켜진 개발용 기능이 없거나 개발 프로필이면 통과한다. 기본 프로필(spring.profiles.default)도 활성으로 본다. */
    static void check(ConfigurableEnvironment environment) {
        Binder binder = Binder.get(environment);
        List<String> enabled = DEV_FEATURES.stream()
                .filter(key -> binder.bind(key, Boolean.class).orElse(false))
                .toList();
        if (enabled.isEmpty() || environment.acceptsProfiles(DEV_PROFILES)) return;
        throw new IllegalStateException("개발용 기능은 local · compose · test 프로필에서만 켤 수 있다. 켜진 설정: " + enabled + ", 활성 프로필: "
                + Arrays.toString(environment.getActiveProfiles()));
    }
}
