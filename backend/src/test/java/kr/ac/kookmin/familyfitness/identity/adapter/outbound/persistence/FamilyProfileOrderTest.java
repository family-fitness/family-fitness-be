package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가족의 식구 차례는 더한 차례다(만든 시각 · id). 시계가 같은 시각을 두 번 주면(한 트랜잭션에서 잇달아 더하거나 시계 해상도가 낮을 때)
 * 만든 시각이 같아져 id(무작위)로 갈렸고, 심사용 체험 가족의 식구 차례가 부를 때마다 바뀌었다.
 * 저장소가 새 식구의 만든 시각을 그 가족의 마지막 식구보다 늘 뒤로 둬서, 시계가 멈춰 있어도 더한 차례가 지켜지는지 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class FamilyProfileOrderTest {
    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Autowired
    FamilyJpaRepository familyJpa;

    @Autowired
    ProfileJpaRepository profileJpa;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID userId = UUID.randomUUID();
    private UUID familyId;

    @AfterEach
    void tearDown() {
        jdbc.update(
                "delete from consent_events where profile_id in (select id from profiles where family_id = ?)",
                familyId);
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
        jdbc.update("delete from users where id = ?", userId);
    }

    @Test
    @DisplayName("시계가 멈춰 있어도(같은 시각) 식구는 더한 차례대로 선다")
    void 같은_시각에_더한_식구도_더한_차례대로_선다() {
        jdbc.update(
                "insert into users (id, provider, provider_user_id, email, status, created_at, updated_at)"
                        + " values (?, 'DEV', ?, null, 'ACTIVE', ?, ?)",
                userId,
                "order-" + userId,
                NOW,
                NOW);
        FamilyRepositoryAdapter repository =
                new FamilyRepositoryAdapter(familyJpa, profileJpa, em, Clock.fixed(NOW, ZoneOffset.UTC));
        familyId = Objects.requireNonNull(tx.execute(status -> {
            Family family = Family.createWithParent(userId, "차례", "엄마", LocalDate.of(1988, 3, 1), Sex.F, TODAY);
            repository.save(family);
            return family.getId();
        }));
        List<String> added = List.of("아빠", "하윤", "서준", "하늘", "바다", "구름");
        for (String name : added) {
            tx.executeWithoutResult(status -> {
                Family family = Objects.requireNonNull(repository.findById(familyId));
                boolean parent = name.equals("아빠");
                family.addMember(
                        userId,
                        name,
                        parent ? LocalDate.of(1986, 1, 1) : LocalDate.of(2016, 1, 1),
                        parent ? Sex.M : Sex.F,
                        parent ? ProfileRole.PARENT : ProfileRole.CHILD,
                        null,
                        null,
                        parent ? null : new GuardianConsent(true, true),
                        NOW,
                        TODAY);
                repository.save(family);
            });
        }

        List<String> names =
                tx.execute(status -> Objects.requireNonNull(repository.findById(familyId)).getProfiles().stream()
                        .map(Profile::getDisplayName)
                        .toList());

        assertThat(names).containsExactly("엄마", "아빠", "하윤", "서준", "하늘", "바다", "구름");
    }
}
