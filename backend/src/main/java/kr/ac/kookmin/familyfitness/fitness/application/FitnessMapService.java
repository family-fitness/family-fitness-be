package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈(`/home` 가족 체력 지도 ★메인) 한 번의 조회. 구성원 카드 = 프로필 요약 + 최신 측정 요약.
 * 가족 체력 지도는 비교·순위가 아니다 — 구성원 사이 정렬은 프로필 순서 그대로다.
 * 호출 계정이 CHILD 면 구성원마다 overallPercentile 만 남기고 부모만 볼 값(가장 낮은 · 높은 항목, 코치 방향,
 * 「유소년 상위 n%」 headline)을 비운다 — 규칙은 {@link FitnessTestService} 와 같다.
 */
@Service
public class FitnessMapService {
    private final FitnessTestRepository tests;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;

    public FitnessMapService(FitnessTestRepository tests, FamilyAccess familyAccess, ProfileQuery profileQuery) {
        this.tests = tests;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
    }

    @Transactional(readOnly = true)
    public FitnessMap of(UUID actorId, UUID familyId) {
        boolean parentScope = familyAccess.requireMember(actorId, familyId).isParent();
        List<FitnessMapMember> members = profileQuery.summariesOfFamily(familyId).stream()
                .map(profile -> {
                    FitnessTest latest = tests.findLatestByProfileId(profile.profileId());
                    return new FitnessMapMember(
                            profile,
                            latest == null || !parentScope ? null : headline(latest),
                            latest == null ? null : summarize(latest, parentScope));
                })
                .toList();
        return new FitnessMap(familyId, profileQuery.familyName(familyId), members);
    }

    private FitnessMapLatest summarize(FitnessTest test, boolean parentScope) {
        if (!parentScope) {
            return new FitnessMapLatest(
                    test.getId(), test.getTestedOn(), test.getOverallPercentile(), null, null, null);
        }
        return new FitnessMapLatest(
                test.getId(),
                test.getTestedOn(),
                test.getOverallPercentile(),
                test.getWeakest(),
                test.getStrongest(),
                test.getCoachDirection());
    }

    /**
     * 예: `유소년 상위 37%`. 백분위는 측정 당시 나이의 규준으로 굳힌 값이라, 연령대 이름도 그 회차의 연령대를 쓴다.
     * 오늘 나이(프로필 연령대)를 쓰면 생일이 연령대 경계(7·13·19·65세)를 넘은 뒤 다른 또래와 견준 것처럼 읽힌다.
     */
    private static @Nullable String headline(FitnessTest test) {
        Integer overall = test.getOverallPercentile();
        if (overall == null) return null;
        return test.getAgeGroup().getLabel() + " " + Copy.topPercentText(overall);
    }
}
