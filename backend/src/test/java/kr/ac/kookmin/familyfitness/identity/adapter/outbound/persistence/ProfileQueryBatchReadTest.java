package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.support.ProfileRows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 가족을 한 번에 읽는 {@link ProfileQuery#summariesOfFamilies} · {@link ProfileQuery#familyNames} 가 H2 에서
 * 가족마다 따로 읽은 값({@link ProfileQuery#summariesOfFamily} · {@link ProfileQuery#familyName})과 같은지 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileQueryBatchReadTest {
    @Autowired
    ProfileQuery profiles;

    @Autowired
    ProfileRows rows;

    @Test
    @DisplayName("여러 가족의 식구 — 가족마다 따로 읽은 것과 같은 값 · 같은 차례, 없는 가족은 빈 목록")
    void 여러_가족의_식구() {
        UUID seojun = rows.family("서준이네");
        rows.profile(seojun, LocalDate.of(1988, 3, 1), Sex.F, ProfileRole.PARENT, "엄마");
        rows.profile(seojun, LocalDate.of(2016, 5, 1), Sex.M, ProfileRole.CHILD, "서준");
        rows.profile(seojun, LocalDate.of(2018, 7, 1), Sex.F, ProfileRole.CHILD, "서아");
        UUID hayun = rows.family("하윤이네");
        rows.profile(hayun, LocalDate.of(1985, 1, 1), Sex.M, ProfileRole.PARENT, "아빠");
        UUID gone = UUID.randomUUID();

        Map<UUID, List<ProfileSummary>> members = profiles.summariesOfFamilies(List.of(seojun, hayun, gone));

        assertThat(members).containsOnlyKeys(seojun, hayun, gone);
        assertThat(members.get(seojun)).hasSize(3).containsExactlyElementsOf(profiles.summariesOfFamily(seojun));
        assertThat(members.get(hayun)).containsExactlyElementsOf(profiles.summariesOfFamily(hayun));
        assertThat(members.get(gone)).isEmpty();
        assertThat(profiles.summariesOfFamilies(List.of())).isEmpty();
    }

    @Test
    @DisplayName("여러 가족의 이름 — 없는 가족은 빠진다")
    void 여러_가족의_이름() {
        UUID seojun = rows.family("서준이네");
        UUID hayun = rows.family("하윤이네");

        assertThat(profiles.familyNames(List.of(seojun, hayun, UUID.randomUUID())))
                .isEqualTo(Map.of(seojun, "서준이네", hayun, "하윤이네"));
        assertThat(profiles.familyNames(List.of())).isEmpty();
    }
}
