package kr.ac.kookmin.familyfitness.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

/**
 * H2 메모리 DB 를 쓰는 프로필은 연결 풀이 연결을 스스로 닫지 않아야 한다.
 *
 * <p>H2 2.4 는 IN 목록 CHECK 제약(ck_profiles_sex, ck_users_status 등)이 그 제약을 만든 세션을 값 비교에 쓴다
 * (ConditionInConstantSet). Hikari 기본값은 연결을 30분쯤 쓰고 닫는다(maxLifetime). 그래서 서버를 띄우고 30분쯤 지나 Flyway 가
 * 쓰던 연결이 닫히면, 그 뒤 모든 insert 가 "Check constraint invalid" 와 "The database has been closed" 로 실패한다. 로컬 서버에서
 * 심사용 로그인이 500 을 돌려주던 까닭이다.
 */
class H2PoolLifetimeTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"application-local.properties", "application-test.properties"})
    @DisplayName("H2 프로필은 연결 풀이 연결을 오래됐다고 닫지 않는다")
    void H2_프로필은_연결을_닫지_않는다(String file) throws IOException {
        Properties props = load(file);

        assertThat(props.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
        assertThat(props.getProperty("spring.datasource.hikari.max-lifetime")).isEqualTo("0");
    }

    private static Properties load(String file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = new ClassPathResource(file).getInputStream()) {
            props.load(in);
        }
        return props;
    }
}
