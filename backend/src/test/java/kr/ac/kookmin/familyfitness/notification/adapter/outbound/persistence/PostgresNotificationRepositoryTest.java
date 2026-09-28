package kr.ac.kookmin.familyfitness.notification.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.TestcontainersConfiguration;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationRepository;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 알림 저장소를 운영과 같은 PostgreSQL(Testcontainers)에서 돈다. Docker 가 없는 환경에서는 건너뛴다(CI 에서는 돈다).
 *
 * <p>넣기는 JDBC 로 빈 칸(null)을 타입 없이 넘기고 ON CONFLICT DO NOTHING 에 기댄다. H2 는 null 타입을 너그럽게 받아서 이 경로를
 * 대신 확인하지 못한다. 다시 재기 지우기는 받는 사람 목록(in)을 JPQL 로 넘기는 모양을 본다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PostgresNotificationRepositoryTest {
    private final LocalDate today = LocalDate.of(2026, 9, 29);
    private final Instant at = Instant.parse("2026-09-29T00:00:00Z"); // 09:00 KST

    @Autowired
    NotificationRepository notifications;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    private UUID familyId;
    private UUID mom;
    private UUID dad;
    private UUID kid;
    private UUID sibling;

    @BeforeEach
    void setUp() {
        familyId = rows.family("서준이네");
        mom = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "은영");
        dad = rows.profile(familyId, LocalDate.of(1986, 7, 1), Sex.M, ProfileRole.PARENT, "철수");
        kid = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        sibling = rows.profile(familyId, LocalDate.of(2018, 2, 1), Sex.F, ProfileRole.CHILD, "지우");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update(
                "delete from notifications where profile_id in (select id from profiles where family_id = ?)",
                familyId);
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
    }

    @Test
    @DisplayName("빈 칸이 많은 알림도 들어가고, 같은 (받는 사람, 멱등 키)를 다시 넣으면 예외 없이 false")
    void 넣기() {
        UUID mission = UUID.randomUUID();

        assertThat(notifications.insertIfAbsent(remeasure(mom, kid, 30))).isTrue();
        assertThat(notifications.insertIfAbsent(remeasure(mom, kid, 30))).isFalse();
        assertThat(notifications.insertIfAbsent(Notification.missionReady(kid, mission, "스쿼트", today, at)))
                .isTrue();

        List<Notification> momList = notifications.latest(mom, today, true, today.minusDays(14), 30);
        assertThat(momList).singleElement().satisfies(n -> {
            assertThat(n.aboutProfileId()).isEqualTo(kid);
            assertThat(n.fromProfileId()).isNull();
            assertThat(n.missionId()).isNull();
            assertThat(n.date()).isNull();
            assertThat(n.readAt()).isNull();
            assertThat(n.createdAt()).isEqualTo(at);
        });
        assertThat(notifications.latest(kid, today, true, today.minusDays(14), 30))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.missionId()).isEqualTo(mission);
                    assertThat(n.date()).isEqualTo(today);
                });
    }

    @Test
    @DisplayName("다시 재기 지우기 — 받는 사람 알림함에서 그 아이의 지난 회차만 지운다. 남길 키 · 형제 것은 그대로")
    void 다시_재기_지우기() {
        for (UUID parent : List.of(mom, dad)) {
            notifications.insertIfAbsent(remeasure(parent, kid, 40));
            notifications.insertIfAbsent(remeasure(parent, kid, 30));
            notifications.insertIfAbsent(remeasure(parent, sibling, 35));
        }
        String keep = Notification.remeasureKey(kid, today.minusDays(30));

        assertThat(notifications.deleteRemeasureAbout(List.of(mom, dad), kid, keep))
                .isEqualTo(2);
        assertThat(notifications.deleteRemeasureAbout(List.of(), kid, null)).isZero();
        assertThat(keysOf(mom))
                .containsExactlyInAnyOrder(keep, Notification.remeasureKey(sibling, today.minusDays(35)));

        assertThat(notifications.deleteRemeasureAbout(List.of(mom, dad), kid, null))
                .isEqualTo(2);
        assertThat(keysOf(dad)).containsExactly(Notification.remeasureKey(sibling, today.minusDays(35)));
    }

    private Notification remeasure(UUID parent, UUID about, int daysAgo) {
        String name = about.equals(kid) ? "서준" : "지우";
        return Notification.remeasure(parent, about, name, today.minusDays(daysAgo), today, at);
    }

    private List<String> keysOf(UUID profileId) {
        return jdbc.queryForList("select dedupe_key from notifications where profile_id = ?", String.class, profileId);
    }
}
