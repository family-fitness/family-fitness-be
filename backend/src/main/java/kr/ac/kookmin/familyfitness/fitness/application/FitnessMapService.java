package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈(`/home` 가족 체력 지도 ★메인) 한 번의 조회. 구성원 카드 = 프로필 요약 + 최신 측정 요약.
 * 가족 체력 지도는 비교·순위가 아니다 — 구성원 사이 정렬은 프로필 순서 그대로다.
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
        familyAccess.requireMember(actorId, familyId);
        List<FitnessMapMember> members = profileQuery.summariesOfFamily(familyId).stream()
                .map(profile -> {
                    FitnessTest latest = tests.findLatestByProfileId(profile.profileId());
                    return new FitnessMapMember(
                            profile,
                            latest == null ? null : headline(profile, latest),
                            latest == null ? null : summarize(latest));
                })
                .toList();
        return new FitnessMap(familyId, profileQuery.familyName(familyId), members);
    }

    private FitnessMapLatest summarize(FitnessTest test) {
        return new FitnessMapLatest(
                test.getId(),
                test.getTestedOn(),
                test.getOverallPercentile(),
                test.getWeakest(),
                test.getStrongest(),
                test.getCoachDirection());
    }

    private @Nullable String headline(ProfileSummary profile, FitnessTest test) {
        Integer overall = test.getOverallPercentile();
        if (overall == null) return null;
        return profile.ageGroup().getLabel() + " " + Copy.topPercentText(overall);
    }
}
