package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Band;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * AI 서비스가 죽었을 때의 대체 편성(보드 F3 「LLM 없이도 돈다」).
 * 임베딩·LLM 없이 영상 라벨(연령·요인)과 국민체력100 규준 백분위만으로 편성 대상의 하루 미션 하나를 만든다.
 * 결과는 AI 응답과 같은 모양({@link CoachRunResult})이라 저장·승인 경로가 갈라지지 않는다(참여자 규칙도 변환기가 같게 적용한다).
 * 근거 없이는 미션을 내지 않는다 — 인용은 규준 백분위 또는 영상 라벨로 항상 1개 이상이다.
 * 시연 중 외부 장애로 빈 화면이 뜨는 것을 막는 안전망이며, AI 편성을 대신하는 것이 아니다.
 */
@Component
public class LabelBasedProposalPlanner {
    /** AI 응답의 미션 kind · 세션 phase 값(AI 인터페이스-명세 4장). */
    static final String DAILY = "일간";

    static final String MAIN_PHASE = "본운동";

    private final FitnessQuery fitnessQuery;
    private final ExerciseVideoRepository videos;

    public LabelBasedProposalPlanner(FitnessQuery fitnessQuery, ExerciseVideoRepository videos) {
        this.fitnessQuery = fitnessQuery;
        this.videos = videos;
    }

    /** 고른 요인과, 규준으로 짚을 수 있으면 그 백분위. */
    private record Target(FitnessFactor factor, @Nullable FactorPoint point, String direction) {}

    /**
     * 편성 대상(subject)의 runDate 하루 미션 하나. 참여자는 대상 한 명(주행자)이고, 보호자 덧붙이기는 변환기가 한다.
     * 요인: 부모가 고른 힘(focusFactor)이 있으면 그것, 없으면 측정의 가장 약한 요인(백분위 75 이하), 아니면 가장 강한 요인.
     * 영상: 대상 연령대에 맞고 그 요인 라벨이 있는 것 중 연령 범위가 좁은 것, 같으면 짧은 것. 통째로 한 칸(본운동)에 넣는다.
     * 고를 요인이 없거나(측정도 고른 힘도 없음) 인용이 하나도 없으면 null (제안을 만들 근거가 없다 → FAILED).
     */
    public @Nullable CoachRunResult plan(
            ProfileDetails subject,
            LocalDate runDate,
            CoachRunConditions conditions,
            LocalDate today,
            String failureSummary) {
        LatestFitness latest = fitnessQuery.latestOf(subject.profileId());
        Target target = target(conditions.focusFactor(), latest);
        if (target == null) return null;
        FitnessFactor factor = target.factor();
        AgeGroup ageGroup = AgeGroup.of(subject.birthDate(), today);
        ExerciseVideo video = videos.findAllAfter(null).stream()
                .filter(it ->
                        it.getLabel().suitableFor(ageGroup) && it.getLabel().hasFactor(factor.getLabel()))
                // 연령 범위가 좁은(그 연령대를 겨냥한) 영상을 먼저, 같으면 짧은 것
                .sorted(Comparator.comparingInt(LabelBasedProposalPlanner::ageSpan)
                        .thenComparingInt(LabelBasedProposalPlanner::durationOrMax))
                .findFirst()
                .orElse(null);

        List<Citation> citations = new ArrayList<>();
        FactorPoint point = target.point();
        if (point != null) {
            citations.add(new Citation(
                    citations.size() + 1,
                    "국민체력100 규준 · " + ageGroup.getLabel() + " " + factor.getLabel() + " 백분위 " + point.percentile(),
                    "norm:" + ageGroup.getLabel() + "-" + point.itemCode(),
                    null));
        }
        if (video != null) {
            citations.add(new Citation(
                    citations.size() + 1,
                    "국민체력100 운동영상 · " + video.getTitle(),
                    "video:" + video.getVideoId(),
                    video.getUrl()));
        }
        if (citations.isEmpty()) return null;

        int minutes = conditions.minutes();
        List<Integer> evidence = citations.stream().map(Citation::index).toList();
        CoachRunResult.Session session = new CoachRunResult.Session(
                0,
                MAIN_PHASE,
                1,
                video == null ? factor.getLabel() + " 운동" : video.getTitle(),
                factor.getLabel(),
                video == null ? null : video.getDurationSec(),
                video == null ? null : new CoachRunResult.Video(video.getVideoId(), null, null),
                evidence);
        String day = runDate.toString();
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                DAILY,
                factor.getLabel() + " " + target.direction() + " " + minutes + "분",
                day,
                day,
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(subject.profileId()), CoachRoles.DRIVER)),
                minutes,
                null,
                List.of(session),
                "오늘은 " + factor.getLabel() + "을 키우는 동작을 해볼까요",
                point == null
                        ? "고르신 " + factor.getLabel() + "을 기르는 동작으로 " + minutes + "분을 짰습니다"
                        : factor.getLabel() + "은 "
                                + Band.ofPercentile(point.percentile()).getCopy() + " 입니다. 오늘 " + minutes + "분이면 충분합니다",
                "");
        return new CoachRunResult(
                "fallback:" + UUID.randomUUID(),
                "succeeded",
                List.of(
                        new CoachRunResult.Step(
                                1,
                                "assess",
                                "ok",
                                "측정 " + (latest == null ? "없음" : "있음") + " · 대상 요인 = " + factor.getLabel()
                                        + (conditions.focusFactor() == null ? "" : "(부모가 고름)")),
                        new CoachRunResult.Step(
                                2,
                                "retrieve",
                                "partial",
                                "AI 서비스 장애(" + failureSummary + ") → 영상 라벨 기반 편성 · 영상 " + (video == null ? 0 : 1)
                                        + "편"),
                        new CoachRunResult.Step(3, "compose", "ok", "하루 " + minutes + "분 · 칸 1개"),
                        new CoachRunResult.Step(4, "verify", "ok", "인용 " + citations.size() + "건 · 연령 필터 확인")),
                new CoachRunResult.Proposal(List.of(mission), List.copyOf(citations), List.of()),
                false,
                null);
    }

    /** 부모가 고른 힘이 먼저다. 그 요인의 백분위는 측정의 약점 · 강점과 같을 때만 인용한다. */
    private static @Nullable Target target(@Nullable FitnessFactor focus, @Nullable LatestFitness latest) {
        FactorPoint weakest = latest == null ? null : latest.weakest();
        FactorPoint strongest = latest == null ? null : latest.strongest();
        if (focus != null) {
            FactorPoint point = weakest != null && weakest.factor() == focus
                    ? weakest
                    : (strongest != null && strongest.factor() == focus ? strongest : null);
            return new Target(focus, point, "키우기");
        }
        FactorPoint point = weakest != null && weakest.percentile() <= Band.STRENGTH_FROM
                ? weakest
                : (strongest != null ? strongest : weakest);
        if (point == null) return null;
        return new Target(point.factor(), point, point == weakest ? "키우기" : "강점 강화");
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
