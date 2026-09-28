package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessMap;
import kr.ac.kookmin.familyfitness.fitness.domain.CoachDirection;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Copy;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

/** 홈 화면 응답. 구성원 카드마다 프로필 요약 필드 + headline + latest(없으면 null · 404 아님). */
public record FitnessMapResponse(UUID familyId, @Nullable String familyName, List<Member> members, String disclaimer) {
    public record Member(
            UUID profileId,
            String name,
            ProfileRole role,
            AgeGroup ageGroup,
            Sex sex,
            boolean hasAccount,
            @Nullable SupportMode supportMode,
            boolean measurable,
            boolean consentRequired,
            boolean consentGiven,
            /** 예: `유소년 상위 37%`. 측정이 없으면 null → "첫 측정을 등록하면 지도가 그려져요" */
            @Nullable String headline,
            @Nullable Latest latest) {}

    public record Latest(
            UUID fitnessTestId,
            LocalDate testedOn,
            @Nullable Integer overallPercentile,
            @Nullable FactorPoint weakest,
            @Nullable FactorPoint strongest,
            CoachDirection coachDirection) {}

    public static FitnessMapResponse of(FitnessMap map) {
        return new FitnessMapResponse(
                map.familyId(),
                map.familyName(),
                map.members().stream()
                        .map(m -> new Member(
                                m.profile().profileId(),
                                m.profile().name(),
                                m.profile().role(),
                                m.profile().ageGroup(),
                                m.profile().sex(),
                                m.profile().hasAccount(),
                                m.profile().supportMode(),
                                m.profile().measurable(),
                                m.profile().consentRequired(),
                                m.profile().consentGiven(),
                                m.headline(),
                                m.latest() == null
                                        ? null
                                        : new Latest(
                                                m.latest().fitnessTestId(),
                                                m.latest().testedOn(),
                                                m.latest().overallPercentile(),
                                                m.latest().weakest(),
                                                m.latest().strongest(),
                                                m.latest().coachDirection())))
                        .toList(),
                Copy.FITNESS_DISCLAIMER);
    }
}
