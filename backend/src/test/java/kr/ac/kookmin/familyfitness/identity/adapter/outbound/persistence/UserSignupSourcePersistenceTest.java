package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.UserRegistrationService;
import kr.ac.kookmin.familyfitness.identity.domain.SignupSource;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 처음 가입한 계정의 유입 경로(V174 users.signup_utm_*)가 H2 의 실제 행에 쓰이고, 다시 로그인해도 바뀌지 않는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserSignupSourcePersistenceTest {
    @Autowired
    UserRegistrationService registration;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    /** JPA 가 아직 내보내지 않은 쓰기를 내보낸 뒤 행을 SQL 로 읽는다. */
    private Map<String, Object> row(UUID userId) {
        em.flush();
        return jdbc.queryForMap(
                "select signup_utm_source, signup_utm_medium, signup_utm_campaign from users where id = ?", userId);
    }

    @Test
    @DisplayName("처음 가입할 때 실려 온 utm 값을 계정 행에 남기고, 같은 계정으로 다시 들어오면 첫 값을 그대로 둔다")
    void 처음_가입한_유입_경로가_남는다() {
        String subject = "google-" + UUID.randomUUID();
        User user = registration.registerOrGet(
                "GOOGLE", subject, null, SignupSource.of("instagram", "paid", "fitness-age"));
        registration.registerOrGet("GOOGLE", subject, null, SignupSource.of("cafe", "post", null));

        Map<String, Object> saved = row(user.id());
        assertThat(saved.get("signup_utm_source")).isEqualTo("instagram");
        assertThat(saved.get("signup_utm_medium")).isEqualTo("paid");
        assertThat(saved.get("signup_utm_campaign")).isEqualTo("fitness-age");
    }

    @Test
    @DisplayName("utm 없이 가입한 계정은 세 칸이 비어 있다")
    void 유입_경로가_없으면_비어_있다() {
        User user = registration.registerOrGet("GOOGLE", "google-" + UUID.randomUUID(), null);

        Map<String, Object> saved = row(user.id());
        assertThat(saved.get("signup_utm_source")).isNull();
        assertThat(saved.get("signup_utm_medium")).isNull();
        assertThat(saved.get("signup_utm_campaign")).isNull();
    }
}
