package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.ProfileEdit;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.web.ApiErrorHandler;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 가족을 두 요청이 겹쳐 고칠 때 늦게 저장하는 쪽이 먼저 커밋한 값을 덮어쓰지 않는지 본다. test 프로필의 H2(READ COMMITTED)에서 돈다.
 *
 * <p>바깥 트랜잭션(늦은 요청)이 가족을 먼저 읽는다. 그 사이 새 트랜잭션(REQUIRES_NEW, 먼저 끝난 요청)이 같은 프로필을 고치고 커밋한다.
 * 그 뒤 바깥 트랜잭션이 옛 상태를 고쳐 저장한다. 저장소는 가족을 저장할 때 프로필 행의 모든 칸을 다시 쓰므로, 행 버전을 보지 않으면
 * 먼저 커밋한 값(이름 · 붙은 계정)이 옛 값으로 돌아간다. 한 스레드에서 차례대로 돌아 잠금을 기다리지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class FamilyConcurrentSaveTest {
    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Autowired
    FamilyRepository repository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ProfileRows rows;

    @Autowired
    JdbcTemplate jdbc;

    private TransactionTemplate otherRequest;
    private UUID familyId;
    private UUID kidId;
    private UUID momUserId;
    private UUID kidUserId;

    @BeforeEach
    void setUp() {
        otherRequest = new TransactionTemplate(transactionManager);
        otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        familyId = rows.family();
        UUID momId = rows.profile(familyId, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        kidId = rows.profile(familyId, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        momUserId = user("concurrent-mom-");
        kidUserId = user("concurrent-kid-");
        jdbc.update("update profiles set user_id = ? where id = ?", momUserId, momId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update(
                "delete from consent_events where profile_id in (select id from profiles where family_id = ?)",
                familyId);
        jdbc.update("delete from profiles where family_id = ?", familyId);
        jdbc.update("delete from families where id = ?", familyId);
        jdbc.update("delete from users where id in (?, ?)", momUserId, kidUserId);
    }

    @Test
    @DisplayName("먼저 읽고 늦게 저장한 요청은 409 CONFLICT 로 끝나고, 먼저 커밋한 이름이 남고 늦은 요청의 동의 철회 · 이력은 남지 않는다")
    void staleSaveIsRejected() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    Family stale = Objects.requireNonNull(repository.findById(familyId));
                    otherRequest.executeWithoutResult(inner -> {
                        Family fresh = Objects.requireNonNull(repository.findById(familyId));
                        fresh.editProfile(momUserId, kidId, new ProfileEdit("서윤", null, null), TODAY);
                        repository.save(fresh);
                    });
                    stale.updateConsent(momUserId, kidId, new GuardianConsent(false, false), NOW, TODAY);
                    repository.save(stale);
                }))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("CONFLICT");
                    assertThat(ApiErrorHandler.statusOf(e.getKind())).isEqualTo(HttpStatus.CONFLICT);
                });

        assertThat(column("display_name", String.class)).isEqualTo("서윤");
        assertThat(column("consent_revoked_at", Instant.class)).isNull();
        assertThat(consentEvents()).isZero();
    }

    @Test
    @DisplayName("다른 계정이 초대 코드로 먼저 붙은 프로필을 옛 상태로 저장하면 409 CONFLICT 이고, 붙은 계정이 그대로 남는다")
    void staleSaveDoesNotDetachClaimedAccount() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    Family stale = Objects.requireNonNull(repository.findById(familyId));
                    otherRequest.executeWithoutResult(
                            inner -> assertThat(repository.attachUserIfUnclaimed(kidId, kidUserId, NOW))
                                    .isTrue());
                    // 옛 상태에서는 계정 없는 아이라 보호자가 이름을 고칠 수 있어 보인다.
                    stale.editProfile(momUserId, kidId, new ProfileEdit("서윤", null, null), TODAY);
                    repository.save(stale);
                }))
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.getCode()).isEqualTo("CONFLICT"));

        assertThat(column("user_id", UUID.class)).isEqualTo(kidUserId);
        assertThat(column("display_name", String.class)).isEqualTo("서준");
    }

    @Test
    @DisplayName("겹치지 않고 차례대로 온 두 요청은 둘 다 저장된다 — 이름을 고친 뒤 동의를 거두면 이름 · 철회 · 이력이 모두 남는다")
    void sequentialSavesBothApply() {
        tx.executeWithoutResult(status -> {
            Family family = Objects.requireNonNull(repository.findById(familyId));
            family.editProfile(momUserId, kidId, new ProfileEdit("서윤", null, null), TODAY);
            repository.save(family);
        });
        tx.executeWithoutResult(status -> {
            Family family = Objects.requireNonNull(repository.findById(familyId));
            family.updateConsent(momUserId, kidId, new GuardianConsent(false, false), NOW, TODAY);
            repository.save(family);
        });

        assertThat(column("display_name", String.class)).isEqualTo("서윤");
        assertThat(column("consent_revoked_at", Instant.class)).isEqualTo(NOW);
        assertThat(consentEvents()).isEqualTo(1);
    }

    private UUID user(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into users (id, provider, provider_user_id, email, status, created_at, updated_at)"
                        + " values (?, 'DEV', ?, null, 'ACTIVE', ?, ?)",
                id,
                prefix + id,
                NOW,
                NOW);
        return id;
    }

    private <T> T column(String name, Class<T> type) {
        return jdbc.queryForObject("select " + name + " from profiles where id = ?", type, kidId);
    }

    private int consentEvents() {
        return Objects.requireNonNull(
                jdbc.queryForObject("select count(*) from consent_events where profile_id = ?", Integer.class, kidId));
    }
}
