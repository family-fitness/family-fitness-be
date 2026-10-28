package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.AccountDeletionService;
import kr.ac.kookmin.familyfitness.identity.application.AccountDeletionService.ReviewErasure;
import kr.ac.kookmin.familyfitness.identity.application.IdentityClock;
import kr.ac.kookmin.familyfitness.identity.application.ReviewAccountSweeper;
import kr.ac.kookmin.familyfitness.identity.application.port.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 심사용 계정 정리({@link ReviewAccountSweeper}). 체험 가족을 실제 API 로 만든 뒤 계정의 만든 시각을 40일 앞으로 옮기고 정리를 돌린다.
 * 지운 가족과 계정을 가리키는 행이 DB 어디에도 남지 않는지 탈퇴 시험과 같은 방법({@link
 * kr.ac.kookmin.familyfitness.support.Leftovers})으로 본다. 시험 DB 를 다른 시험과 나눠 쓰므로 만든 시각을 옮긴 계정만 지워진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewAccountCleanupApiTest extends FamilyApiTestBase {
    @Autowired
    ReviewAccountSweeper sweeper;

    @Autowired
    AccountDeletionService deletion;

    @Autowired
    UserRepository users;

    @Autowired
    IdentityClock clock;

    /** 계정의 만든 시각을 40일 앞으로 옮긴다(보관 기간 31일보다 오래된 계정으로 만든다). */
    private void age(Collection<UUID> userIds) {
        Timestamp longAgo = Timestamp.from(Instant.now().minus(Duration.ofDays(40)));
        userIds.forEach(id -> jdbc.update("update users set created_at = ? where id = ?", longAgo, id));
    }

    private List<UUID> accountsOf(UUID familyId) {
        return jdbc.queryForList(
                "select user_id from profiles where family_id = ? and user_id is not null", UUID.class, familyId);
    }

    private int countUsers(UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("만든 지 31일이 지난 심사용 계정은 체험 가족과 지난 2주 기록까지 지워지고, 그 가족을 가리키는 행이 남지 않는다")
    void 오래된_심사용_계정은_체험_가족째로_지워진다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        List<UUID> everyone = everyone(family, mom);
        assertThat(leftovers.referencing(everyone)).containsKeys("activity_daily.profile_id", "missions.family_id");
        age(List.of(mom.userId()));

        ReviewAccountSweeper.Run run = sweeper.sweep();

        assertThat(run.erased()).isGreaterThanOrEqualTo(1);
        assertThat(run.failed()).isZero();
        assertThat(leftovers.referencing(everyone)).isEmpty();
        assertThat(leftovers.mentioning(everyone)).isEmpty();
        mvc.perform(as(mom, get("/api/v1/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("INVITED 체험 가족은 가짜 보호자 계정이 오래되면 아빠 자리에 붙은 심사위원 계정까지 함께 지워진다")
    void INVITED_가족은_함께_붙은_심사용_계정까지_지워진다() throws Exception {
        Session dad = invitedDad();
        Family family = familyOf(dad);
        UUID owner = ownerUserOf(family.familyId());
        List<UUID> everyone = new ArrayList<>(everyone(family, dad));
        everyone.add(owner);
        age(List.of(owner));

        assertThat(deletion.eraseReviewAccount(owner)).isEqualTo(ReviewErasure.ERASED);

        assertThat(countUsers(owner)).isZero();
        assertThat(countUsers(dad.userId())).isZero();
        assertThat(leftovers.referencing(everyone)).isEmpty();
        assertThat(leftovers.mentioning(everyone)).isEmpty();
    }

    @Test
    @DisplayName("구글 · 개발용 계정이 들어온 체험 가족은 지우지 않는다. 오래되지 않은 심사용 계정과 오래된 개발용 계정도 그대로다")
    void 실제_사용자가_낀_가족과_다른_계정은_남는다() throws Exception {
        Session mom = reviewLogin("FAMILY");
        Family family = familyOf(mom);
        String code = createFamilyInvite(mom, family.familyId(), "PARENT");
        Session aunt = devLogin();
        joinByFamilyInvite(aunt, code, "이모", "1990-01-01");
        List<UUID> everyone = everyone(family, mom);
        Map<String, Long> before = leftovers.referencing(everyone);
        Session recent = reviewLogin("FRESH");
        Session oldDev = devLogin();
        age(List.of(mom.userId(), oldDev.userId()));

        assertThat(deletion.eraseReviewAccount(mom.userId())).isEqualTo(ReviewErasure.KEPT_REAL_MEMBER);
        sweeper.sweep();

        assertThat(accountsOf(family.familyId())).contains(mom.userId(), aunt.userId());
        assertThat(leftovers.referencing(everyone)).isEqualTo(before);
        mvc.perform(as(mom, get("/api/v1/me"))).andExpect(status().isOk());
        mvc.perform(as(recent, get("/api/v1/me"))).andExpect(status().isOk());
        mvc.perform(as(oldDev, get("/api/v1/me"))).andExpect(status().isOk());
        assertThat(deletion.eraseReviewAccount(oldDev.userId())).isEqualTo(ReviewErasure.NOT_REVIEW);
    }

    @Test
    @DisplayName("가족 없이 만든 심사용 계정(FRESH)은 계정만 지워진다")
    void 가족_없는_심사용_계정은_계정만_지워진다() throws Exception {
        Session fresh = reviewLogin("FRESH");
        age(List.of(fresh.userId()));

        assertThat(deletion.eraseReviewAccount(fresh.userId())).isEqualTo(ReviewErasure.ERASED);

        assertThat(leftovers.referencing(List.of(fresh.userId()))).isEmpty();
        mvc.perform(as(fresh, get("/api/v1/me"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("보관 기간이 리프레시 토큰 수명(30일) 이하이면 기동이 멈춘다 — 아직 쓰는 심사용 계정이 지워지지 않게")
    void 보관_기간이_토큰_수명_이하이면_멈춘다() {
        assertThatThrownBy(() -> new ReviewAccountSweeper(users, deletion, clock, Duration.ofDays(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("30일");
    }
}
