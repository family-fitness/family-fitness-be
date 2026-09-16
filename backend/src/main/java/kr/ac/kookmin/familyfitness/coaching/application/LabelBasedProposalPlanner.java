package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * AI 서비스가 죽었을 때의 대체 편성(보드 F3 「LLM 없이도 돈다」).
 * 임베딩·LLM 없이 영상 라벨(연령·요인)과 국민체력100 규준 백분위만으로 한 주 미션을 만든다.
 * 결과는 AI 응답과 같은 모양({@link CoachRunResult})이라 저장·승인 경로가 갈라지지 않는다.
 * 근거 없이는 미션을 내지 않는다 — 인용은 영상 라벨 또는 규준 백분위로 항상 1개 이상이다.
 * 시연 중 외부 장애로 빈 화면이 뜨는 것을 막는 안전망이며, AI 편성을 대신하는 것이 아니다.
 */
@Component
public class LabelBasedProposalPlanner {
    private final ProfileQuery profileQuery;
    private final FitnessQuery fitnessQuery;
    private final ExerciseVideoRepository videos;

    public LabelBasedProposalPlanner(
            ProfileQuery profileQuery, FitnessQuery fitnessQuery, ExerciseVideoRepository videos) {
        this.profileQuery = profileQuery;
        this.fitnessQuery = fitnessQuery;
        this.videos = videos;
    }

    private record Measured(ProfileDetails details, LatestFitness latest) {}

    /** 측정된 구성원이 없으면 null (제안을 만들 근거가 없다). */
    public @Nullable CoachRunResult plan(
            UUID familyId,
            LocalDate weekStart,
            int daysPerWeek,
            int minutesPerSession,
            LocalDate today,
            String failureSummary) {
        List<ProfileDetails> members = profileQuery.detailsOfFamily(familyId);
        List<Measured> measured = new ArrayList<>();
        for (ProfileDetails member : members) {
            LatestFitness latest = fitnessQuery.latestOf(member.profileId());
            if (latest != null) measured.add(new Measured(member, latest));
        }
        Measured first = measured.stream()
                .sorted(Comparator.comparingInt(it -> it.details().role() == ProfileRole.CHILD ? 0 : 1))
                .findFirst()
                .orElse(null);
        if (first == null) return null;
        ProfileDetails driver = first.details();
        LatestFitness latest = first.latest();

        FactorPoint weakest = latest.weakest();
        FactorPoint target = weakest != null && weakest.percentile() <= Band.STRENGTH_FROM
                ? weakest
                : (latest.strongest() != null ? latest.strongest() : weakest);
        if (target == null) return null;
        String direction = weakest != null && target == weakest ? "키우기" : "강점 강화";
        FitnessFactor factor = target.factor();
        AgeGroup ageGroup = AgeGroup.of(driver.birthDate(), today);
        ExerciseVideo video = videos.findAllAfter(null).stream()
                .filter(it ->
                        it.getLabel().suitableFor(ageGroup) && it.getLabel().hasFactor(factor.getLabel()))
                // 연령 범위가 좁은(그 연령대를 겨냥한) 영상을 먼저, 같으면 짧은 것
                .sorted(Comparator.comparingInt(LabelBasedProposalPlanner::ageSpan)
                        .thenComparingInt(LabelBasedProposalPlanner::durationOrMax))
                .findFirst()
                .orElse(null);

        List<Citation> citations = new ArrayList<>();
        citations.add(new Citation(
                1,
                "국민체력100 규준 · " + ageGroup.getLabel() + " " + factor.getLabel() + " 백분위 " + target.percentile(),
                "norm:" + ageGroup.getLabel() + "-" + target.itemCode(),
                null));
        if (video != null) {
            citations.add(new Citation(
                    2, "국민체력100 운동영상 · " + video.getTitle(), "video:" + video.getVideoId(), video.getUrl()));
        }
        // 응원만 하는 부모는 참여자로 자동 배정하지 않는다(보드 v2 원칙 · AI 계약 §5.1)
        String driverRef = ProfileRef.of(driver.profileId());
        List<CoachRunResult.ParticipantRef> participants = members.stream()
                .map(it -> new CoachRunResult.ParticipantRef(
                        ProfileRef.of(it.profileId()), CoachRoles.of(it.role(), it.supportMode())))
                .filter(it -> !it.role().equals(CoachRoles.CHEER))
                .sorted(Comparator.comparingInt(it -> it.ref().equals(driverRef) ? 0 : 1))
                .toList();
        List<Integer> evidence = citations.stream().map(Citation::index).toList();
        List<CoachRunResult.Session> sessions = new ArrayList<>();
        for (int i = 0; i < daysPerWeek; i++) {
            sessions.add(new CoachRunResult.Session(
                    (i * 7) / daysPerWeek,
                    video == null ? factor.getLabel() + " 운동" : video.getTitle(),
                    factor.getLabel(),
                    minutesPerSession,
                    video == null ? null : new CoachRunResult.Video(video.getVideoId(), null),
                    evidence));
        }
        String bandCopy = Band.ofPercentile(target.percentile()).getCopy();
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                factor.getLabel() + " " + direction + " · 한 주",
                weekStart.toString(),
                weekStart.plusDays(6).toString(),
                participants,
                List.copyOf(sessions),
                "이번 주는 " + factor.getLabel() + "을 키우는 동작을 가족과 함께 해볼까요",
                factor.getLabel() + "은 " + bandCopy + " 입니다. 주 " + daysPerWeek + "회 " + minutesPerSession
                        + "분이면 충분합니다");
        return new CoachRunResult(
                "fallback:" + UUID.randomUUID(),
                "succeeded",
                List.of(
                        new CoachRunResult.Step(
                                1,
                                "assess",
                                "ok",
                                "가족 " + members.size() + "명 중 측정값 있는 구성원 " + measured.size() + "명 · 대상 요인 = "
                                        + factor.getLabel()),
                        new CoachRunResult.Step(
                                2,
                                "retrieve",
                                "partial",
                                "AI 서비스 장애(" + failureSummary + ") → 영상 라벨 기반 편성 · 영상 " + (video == null ? 0 : 1)
                                        + "편"),
                        new CoachRunResult.Step(
                                3,
                                "compose",
                                "ok",
                                "주 " + daysPerWeek + "회 " + minutesPerSession + "분 · 세션 " + sessions.size() + "건"),
                        new CoachRunResult.Step(4, "verify", "ok", "인용 " + citations.size() + "건 · 연령 필터 확인")),
                new CoachRunResult.Proposal(List.of(mission), List.copyOf(citations)),
                false,
                null);
    }

    private static int ageSpan(ExerciseVideo video) {
        Integer ageTo = video.getLabel().ageTo();
        Integer ageFrom = video.getLabel().ageFrom();
        return (ageTo == null ? 120 : ageTo) - (ageFrom == null ? 0 : ageFrom);
    }

    private static int durationOrMax(ExerciseVideo video) {
        Integer durationSec = video.getDurationSec();
        return durationSec == null ? Integer.MAX_VALUE : durationSec;
    }
}
