package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachPlace;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoMedia;
import kr.ac.kookmin.familyfitness.fitness.api.FactorPoint;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.fitness.api.LatestFitness;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.shared.ai.Citation;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.ai.SessionClipCounts;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * AI 서비스가 죽었을 때의 대체 편성(보드 F3 「LLM 없이도 돈다」).
 * 임베딩·LLM 없이 클립 라벨(연령대 · 요인 · 단계)과 국민체력100 규준 백분위만으로 편성 대상의 하루 미션 하나를 만든다.
 * 결과는 AI 응답과 같은 모양({@link CoachRunResult})이라 저장·승인 경로가 갈라지지 않는다(참여자 규칙 · 칸 분 배분도 변환기가 같게 적용한다).
 * 근거 없이는 미션을 내지 않는다 — 인용은 규준 백분위 또는 영상 라벨로 항상 1개 이상이다.
 * 시연 중 외부 장애로 빈 화면이 뜨는 것을 막는 안전망이며, AI 편성을 대신하는 것이 아니다.
 */
@Component
public class LabelBasedProposalPlanner {
    /** AI 응답의 미션 kind · 세션 phase 값(AI 인터페이스-명세 4장). */
    static final String DAILY = "일간";

    static final String MAIN_PHASE = "본운동";

    /** 고를 요인이 없을 때 미션 이름에 쓰는 말(ai:coach/compose.py {@code read.factor or '전신'}). */
    static final String WHOLE_BODY = "전신";

    /** AI 를 부르지 않고 짤 때의 까닭(심사용 계정 모두의 오늘 AI 몫이 끝남, {@link ReviewRunQuota}). 장애가 아니라 단계 요약을 달리 적는다. */
    public static final String AI_LIMIT_REACHED = "심사용 계정의 오늘 AI 편성 몫이 끝남";

    /** AI 가 같은 아이의 다른 편성을 짜느라 409 로 계속 거절했을 때의 까닭. AI 가 고장 난 것이 아니라 단계 요약에 「장애」 를 적지 않는다. */
    public static final String AI_BUSY = "AI 사용 중";

    /** 편성이 키울 요인을 부르는 말. 가장 낮은 요인을 골랐을 때(FE 결과 화면과 같은 말)와 보호자가 골랐을 때. */
    static final String WEAKEST_FACTOR = "지금 키우기 좋은 영역";

    static final String FOCUS_FACTOR = "보호자가 키워 주고 싶은 역량";

    /** 한 세트로 삼기 좋은 클립 길이(ai:video/catalog.py SET_SECONDS). 같은 순위면 이 길이에 가까운 것부터 고른다. */
    static final int SET_SECONDS = 60;

    /** 공단 오픈API 영상 인용 이름이 없을 때의 앞머리(chunk_id 는 AI 와 같은 "kspo:<video_id>"). 코퍼스 색인에 없는 영상이라 표에서 세운다. */
    static final String KSPO_CITATION = "국민체력100 동영상 정보";

    static final String KSPO_CHUNK_PREFIX = "kspo:";

    static final String VIDEO_CHUNK_PREFIX = "video:";

    private static final Map<SessionPhase, String> PHASE_NAME = phaseNames();

    private final FitnessQuery fitnessQuery;
    private final ExerciseVideoRepository videos;
    private final ExerciseClipRepository clips;

    public LabelBasedProposalPlanner(
            FitnessQuery fitnessQuery, ExerciseVideoRepository videos, ExerciseClipRepository clips) {
        this.fitnessQuery = fitnessQuery;
        this.videos = videos;
        this.clips = clips;
    }

    /** 고른 요인과, 규준으로 짚을 수 있으면 그 백분위. */
    private record Target(FitnessFactor factor, @Nullable FactorPoint point) {}

    /** 인용과 그 인용을 가리키는 세션들. */
    private record Plan(
            List<Citation> citations,
            List<CoachRunResult.Session> sessions,
            @Nullable Integer videoSec) {}

    /**
     * 편성 대상(subject)의 runDate 하루 미션 하나. 참여자는 대상 한 명(주행자)이고, 보호자 덧붙이기는 변환기가 한다.
     * 요인: 보호자가 키워 주고 싶은 역량(focusFactor)이 있으면 그것, 없으면 측정에서 백분위가 가장 낮은 요인이다.
     * 모든 요인이 높아도 가장 낮은 요인을 고른다(ai:coach/compose.py {@code target_factor} 와 같다).
     * 칸: 클립 표(video_exercises)에서 대상 연령대 · 조건에 맞는 운동 클립을 AI 가짓수 규칙({@link SessionClipCounts})대로
     * 준비 → 본 → 정리 차례로 고른다({@link #routine}). 본운동 클립이 하나도 없으면 예전처럼 영상 한 편을 본운동 한 칸에 넣는다.
     * 고를 요인이 없으면(측정 전이거나 백분위가 없는 만 7~10세이고, 보호자가 키워 주고 싶은 역량도 없음) {@link #wholeBody} 로 짠다.
     * 인용이 하나도 없으면 null (제안을 만들 근거가 없다 → FAILED).
     * recentVideoIds 는 대상이 최근 14일 동안 미션으로 받은 영상 id(최근 것부터, AI 요청의 recent_video_ids 와 같다) — 클립 · 영상을
     * 고를 때 뒤로 미룬다.
     */
    public @Nullable CoachRunResult plan(
            ProfileDetails subject,
            LocalDate runDate,
            CoachRunConditions conditions,
            LocalDate today,
            String failureSummary,
            List<String> recentVideoIds) {
        Recent recent = new Recent(recentVideoIds, runDate);
        LatestFitness latest = fitnessQuery.latestOf(subject.profileId());
        Target target = target(conditions.focusFactor(), latest);
        AgeGroup ageGroup = AgeGroup.of(subject.birthDate(), today);
        if (target == null) return wholeBody(subject, runDate, conditions, latest, ageGroup, failureSummary, recent);
        FitnessFactor factor = target.factor();

        List<Citation> base = new ArrayList<>();
        FactorPoint point = target.point();
        if (point != null) {
            base.add(new Citation(
                    1,
                    "국민체력100 규준, " + ageGroup.getLabel() + " " + factor.getLabel() + " 백분위 " + point.percentile(),
                    "norm:" + ageGroup.getLabel() + "-" + point.itemCode(),
                    null));
        }
        List<ExerciseClip> routine = routine(ageGroup, factor, conditions, recent);
        Plan plan =
                routine.isEmpty() ? wholeVideoPlan(base, ageGroup, factor, recent) : clipPlan(base, routine, factor);
        if (plan.citations().isEmpty()) return null;

        int minutes = conditions.minutes();
        return result(
                subject,
                runDate,
                minutes,
                factor.getLabel() + " 키우기 " + minutes + "분",
                "오늘은 " + factor.getLabel() + "을 키우는 동작을 해볼까요",
                // 키울 요인은 고른 까닭으로 부른다. 백분위 구간 문구(「꾸준히 하고 있는 영역」 등)는 수준을 말할 때만 쓴다
                whyFactor(conditions) + "인 " + factor.getLabel() + "을 기르는 동작으로 " + minutes + "분을 짰습니다",
                plan,
                steps(latest, factor, conditions, failureSummary, routine, plan));
    }

    /**
     * 고를 요인이 없을 때의 편성. AI 규칙 편성(ai:coach/compose.py {@code _by_rule} 을 요인 빈 채로 부른 것)처럼 그 연령대 클립을
     * 요인 가산점 없이 가짓수만큼 골라 「전신 기르기」 미션 하나를 낸다. 칸 요인은 클립 라벨 그대로(없으면 빈 값), 인용은 클립이 나온 영상마다
     * 하나다. 본운동 클립이 없으면 null — 요인도 없이 영상 한 편을 통째로 고를 기준이 없다.
     */
    private @Nullable CoachRunResult wholeBody(
            ProfileDetails subject,
            LocalDate runDate,
            CoachRunConditions conditions,
            @Nullable LatestFitness latest,
            AgeGroup ageGroup,
            String failureSummary,
            Recent recent) {
        List<ExerciseClip> routine = routine(ageGroup, null, conditions, recent);
        if (routine.isEmpty()) return null;
        Plan plan = clipPlan(List.of(), routine, null);
        int minutes = conditions.minutes();
        return result(
                subject,
                runDate,
                minutes,
                WHOLE_BODY + " 기르기 " + minutes + "분",
                "오늘도 몸을 움직여 볼까요",
                latest == null
                        ? "측정 전이라 온몸을 고루 쓰는 동작으로 " + minutes + "분을 짰습니다. 측정을 하면 요인을 짚어 드릴 수 있습니다"
                        : "측정값으로 짚을 요인이 없어 온몸을 고루 쓰는 동작으로 " + minutes + "분을 짰습니다",
                plan,
                steps(latest, null, conditions, failureSummary, routine, plan));
    }

    /** 대상 한 명(주행자)의 runDate 하루 미션 하나로 감싼다. */
    private static CoachRunResult result(
            ProfileDetails subject,
            LocalDate runDate,
            int minutes,
            String title,
            String copyChild,
            String copyParent,
            Plan plan,
            List<CoachRunResult.Step> steps) {
        String day = runDate.toString();
        CoachRunResult.Mission mission = new CoachRunResult.Mission(
                DAILY,
                title,
                day,
                day,
                List.of(new CoachRunResult.ParticipantRef(ProfileRef.of(subject.profileId()), CoachRoles.DRIVER)),
                minutes,
                plan.videoSec(),
                plan.sessions(),
                copyChild,
                copyParent,
                "");
        return new CoachRunResult(
                "fallback:" + UUID.randomUUID(),
                "succeeded",
                steps,
                new CoachRunResult.Proposal(List.of(mission), plan.citations(), List.of()),
                false,
                null);
    }

    /**
     * 한 회분 클립(ai:video/catalog.py {@code routine} 을 옮김, 처방 동작 가산점은 뺐다 — 대체 편성은 처방 검색을 하지 않는다).
     * 후보: 지금 판의 운동 클립(유튜브 구간 + 공단 영상 한 편 클립) 중 대상 연령대에 맞고({@link ExerciseClip#suits}, 어르신은 성인 클립도) 조건에 맞는 것(조용히 → quiet,
     * 집 → home_ok, 늘 도구 없음 — AI 요청과 같다). 단계마다 점수(요인이 같으면 −2, 처방 어휘 이름이 있으면 −1)가 작은 것 →
     * 같은 점수면 대상과 연령대가 같은 것 → 한 세트 길이(60초)에 가까운 것 차례로 가짓수만큼(AI {@code _age_rank} 와 같다 — 연령대를
     * 요인보다 앞에 두면 어르신은 어르신 영상만 보다가 요인을 놓친다). 같은 이름은 두 번 넣지 않는다
     * (그 단계 후보가 모두 앞에서 쓴 이름이면 그 단계만 다시 허용). 본운동이 하나도 없으면 빈 목록 — 준비 · 정리만으로는 짜지 않는다.
     * 날마다 같은 묶음이 나오지 않게(AI 요청 recent_video_ids 와 같은 규칙): 같은 점수 · 연령대 안에서 최근 받은 영상의 클립은 뒤로
     * 미루고(최근일수록 더 뒤라, 최근 영상끼리는 오래전에 받은 것부터 앞이다), 순위가 모두 같으면 편성 날짜를 시드로 삼아 섞는다.
     * 한 회 안에서는 같은 영상을 두 번 쓰지 않는다(다른 영상이 있으면, {@link #take}). 본운동은 요인이 같은 클립이 앞이라 첫 본운동 칸
     * (= 미션 대표 영상, {@link ProposalConverter})이 키울 요인의 클립이다.
     * 보호자가 키워 주고 싶은 역량(focusFactor)을 골랐으면 본운동 칸의 4분의 3 이상(올림, {@link #focusShare})을 그 역량 클립으로
     * 먼저 채운다. 최근 받은 영상의 클립이라도 그 역량이면 다른 요인보다 앞이고, 그래도 모자라면 남은 칸을 다른 요인이 채운다.
     * 준비 · 정리운동과 고르지 않았을 때는 그대로다.
     */
    List<ExerciseClip> routine(
            AgeGroup ageGroup, @Nullable FitnessFactor factor, CoachRunConditions conditions, Recent recent) {
        List<ExerciseClip> pool = clips.findAllActive().stream()
                .filter(ExerciseClip::isExercise)
                .filter(it -> it.suits(ageGroup))
                .filter(it -> fits(it, conditions))
                .toList();
        SessionClipCounts want = SessionClipCounts.of(conditions.minutes());
        // ai:video/catalog.py _age_rank 와 같은 차례: 점수(요인 · 처방 어휘) → 제 연령대 → 최근 받은 영상 → 세트 길이 → 날짜 섞기.
        // 연령대는 같은 점수 안에서만 가른다. 최근 받은 영상은 같은 요인 · 단계 · 연령대 후보가 모자랄 때만 다시 나온다.
        Comparator<ExerciseClip> rank = Comparator.comparingInt((ExerciseClip it) -> score(it, factor))
                .thenComparing(it -> it.ageGroup() != ageGroup)
                .thenComparingInt(it -> recent.rankOf(it.videoId()))
                .thenComparingInt(it -> Math.abs(it.endSec() - it.startSec() - SET_SECONDS))
                .thenComparingLong(it -> recent.shuffleKey(it.clipId()));
        Set<String> used = new HashSet<>();
        // 이번 회에 이미 쓴 영상. 단계를 넘어 한 회 전체에서 센다(준비운동에 쓴 영상을 정리운동에서 다시 쓰지 않게)
        Set<String> usedVideos = new HashSet<>();
        List<ExerciseClip> picked = new ArrayList<>();
        FitnessFactor focus = conditions.focusFactor();
        for (SessionPhase phase : PHASE_NAME.keySet()) {
            List<ExerciseClip> candidates =
                    pool.stream().filter(it -> it.phase() == phase).sorted(rank).toList();
            int wanted = countOf(want, phase);
            if (phase == SessionPhase.MAIN && focus != null) {
                // 보호자가 키워 주고 싶은 역량: 본운동 칸의 4분의 3(올림) 이상을 그 역량 클립으로 먼저 채운다.
                // 최근 받은 영상이라도 그 역량이면 다른 요인보다 먼저다. 모자라면 남은 칸은 아래 순위대로 다른 요인이 채운다.
                // 이미 쓴 이름을 다시 허용할지는 그 단계 후보 전체로 한 번만 정한다(역량 · 나머지를 따로 정하면 같은 동작이 두 번 들 수 있다)
                allowAgainIfAllUsed(candidates, used);
                List<ExerciseClip> focusFirst = take(
                        candidates.stream().filter(it -> it.factor() == focus).toList(),
                        focusShare(wanted),
                        used,
                        usedVideos,
                        focus);
                picked.addAll(focusFirst);
                List<ExerciseClip> rest = candidates.stream()
                        .filter(it -> !focusFirst.contains(it))
                        .toList();
                picked.addAll(take(rest, wanted - focusFirst.size(), used, usedVideos, focus));
                continue;
            }
            picked.addAll(pick(candidates, wanted, used, usedVideos, factor));
        }
        return picked.stream().anyMatch(it -> it.phase() == SessionPhase.MAIN) ? List.copyOf(picked) : List.of();
    }

    /** 본운동 칸 가운데 보호자가 키워 주고 싶은 역량으로 채울 최소 칸 수: 4분의 3 올림(네 칸이면 세 칸, 여섯 칸이면 다섯 칸). */
    static int focusShare(int mainCount) {
        return (3 * mainCount + 3) / 4;
    }

    /** 순위대로 앞에서 wanted 개({@link #take}). 후보가 모두 쓴 이름이면 그 단계만 다시 허용한다(AI 와 같다). */
    private static List<ExerciseClip> pick(
            List<ExerciseClip> ranked,
            int wanted,
            Set<String> used,
            Set<String> usedVideos,
            @Nullable FitnessFactor factor) {
        allowAgainIfAllUsed(ranked, used);
        return take(ranked, wanted, used, usedVideos, factor);
    }

    /** 후보가 모두 앞 단계에서 쓴 이름이면 그 이름들을 다시 쓸 수 있게 한다. */
    private static void allowAgainIfAllUsed(List<ExerciseClip> ranked, Set<String> used) {
        if (!ranked.isEmpty() && ranked.stream().allMatch(it -> used.contains(it.title()))) {
            ranked.forEach(it -> used.remove(it.title()));
        }
    }

    /**
     * 순위대로 앞에서 wanted 개. 이미 쓴 이름은 건너뛰고, 고른 이름 · 영상은 used · usedVideos 에 더한다.
     * 한 회 안에서 같은 영상은 한 번만 쓴다. 이번 회에 쓴 영상의 클립은 순위가 앞서도 다른 영상의 클립 뒤로 미루고, 다른 영상이
     * 없을 때만 다시 쓴다(칸을 비우지 않는다). 다만 키울 요인(factor)이 맞는지가 먼저다. 그 요인 클립이 모두 쓴 영상이어도
     * 다른 요인의 새 영상보다 앞이다. 처방 어휘 가산점 · 연령대 · 최근 받은 영상 · 세트 길이는 같은 영상 피하기보다 뒤다.
     */
    private static List<ExerciseClip> take(
            List<ExerciseClip> ranked,
            int wanted,
            Set<String> used,
            Set<String> usedVideos,
            @Nullable FitnessFactor factor) {
        List<ExerciseClip> picked = new ArrayList<>();
        while (picked.size() < wanted) {
            ExerciseClip best = null;
            int bestKey = Integer.MAX_VALUE;
            for (ExerciseClip clip : ranked) {
                if (used.contains(clip.title())) continue;
                int key = (factor != null && clip.factor() != factor ? 2 : 0)
                        + (usedVideos.contains(clip.videoId()) ? 1 : 0);
                if (key < bestKey) {
                    best = clip;
                    bestKey = key;
                }
            }
            if (best == null) break;
            used.add(best.title());
            usedVideos.add(best.videoId());
            picked.add(best);
        }
        return picked;
    }

    /**
     * 클립마다 칸 하나. 인용은 규준(있으면) + 클립이 나온 영상마다 하나, 칸은 규준과 제 영상 인용을 가리킨다.
     * 칸 요인은 클립 라벨, 없으면 미션 요인, 그것도 없으면(전신) 빈 값이다(AI 와 같다).
     */
    private Plan clipPlan(List<Citation> base, List<ExerciseClip> routine, @Nullable FitnessFactor factor) {
        List<Citation> citations = new ArrayList<>(base);
        Map<String, ExerciseVideo> videoById =
                videos
                        .findAllByIds(routine.stream()
                                .map(ExerciseClip::videoId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(ExerciseVideo::getVideoId, Function.identity(), (a, b) -> a));
        Map<String, Integer> citationOfVideo = new LinkedHashMap<>();
        List<CoachRunResult.Session> sessions = new ArrayList<>();
        int videoSec = 0;
        for (ExerciseClip clip : routine) {
            Integer index = citationOfVideo.computeIfAbsent(clip.videoId(), videoId -> {
                citations.add(videoCitation(citations.size() + 1, videoId, videoById.get(videoId), clip.title()));
                return citations.size();
            });
            List<Integer> evidence = new ArrayList<>();
            base.forEach(it -> evidence.add(it.index()));
            evidence.add(index);
            FitnessFactor clipFactor = clip.factor();
            sessions.add(new CoachRunResult.Session(
                    0,
                    PHASE_NAME.get(clip.phase()),
                    sessions.size() + 1,
                    clip.title(),
                    clipFactor != null ? clipFactor.getLabel() : (factor != null ? factor.getLabel() : ""),
                    clip.endSec() - clip.startSec(),
                    videoOf(clip.videoId(), clip.startSec(), clip.endSec(), clip.media()),
                    List.copyOf(evidence)));
            videoSec += clip.endSec() - clip.startSec();
        }
        return new Plan(List.copyOf(citations), List.copyOf(sessions), videoSec);
    }

    /**
     * 맞는 클립이 없을 때(클립 표가 비었거나 조건에 걸려 본운동이 없을 때)의 예전 편성: 대상 연령대에 맞고 그 요인 라벨이 있는 영상 중
     * 제 연령대를 겨냥한 것(어르신이 받는 성인 영상은 뒤로), 연령 범위가 좁은 것, 같으면 짧은 것을 통째로 본운동 한 칸에 넣는다.
     * 그런 영상도 없으면 영상 없는 본운동 한 칸.
     */
    private Plan wholeVideoPlan(List<Citation> base, AgeGroup ageGroup, FitnessFactor factor, Recent recent) {
        ExerciseVideo video = videos.findAllAfter(null).stream()
                .filter(it ->
                        it.getLabel().suitableFor(ageGroup) && it.getLabel().hasFactor(factor.getLabel()))
                // 제 연령대를 겨냥한 영상 → 최근 받지 않은 영상 → 연령 범위가 좁은 영상 → 짧은 영상
                .sorted(Comparator.comparing(
                                (ExerciseVideo it) -> !it.getLabel().aimsAt(ageGroup))
                        .thenComparingInt(it -> recent.rankOf(it.getVideoId()))
                        .thenComparingInt(LabelBasedProposalPlanner::ageSpan)
                        .thenComparingInt(LabelBasedProposalPlanner::durationOrMax))
                .findFirst()
                .orElse(null);
        List<Citation> citations = new ArrayList<>(base);
        if (video != null) {
            citations.add(videoCitation(citations.size() + 1, video.getVideoId(), video, video.getTitle()));
        }
        CoachRunResult.Session session = new CoachRunResult.Session(
                0,
                MAIN_PHASE,
                1,
                video == null ? factor.getLabel() + " 운동" : video.getTitle(),
                factor.getLabel(),
                video == null ? null : video.getDurationSec(),
                video == null ? null : videoOf(video.getVideoId(), null, null, video.getMedia()),
                citations.stream().map(Citation::index).toList());
        return new Plan(List.copyOf(citations), List.of(session), null);
    }

    /** AI 편성 응답의 video 칸과 같은 모양. 공단 영상이면 source=kspo 와 mp4 주소를 싣는다. */
    private static CoachRunResult.Video videoOf(
            String videoId, @Nullable Integer startSec, @Nullable Integer endSec, VideoMedia media) {
        String mediaUrl = media.mediaUrl();
        return mediaUrl == null
                ? new CoachRunResult.Video(videoId, startSec, endSec)
                : new CoachRunResult.Video(videoId, startSec, endSec, CoachRunResult.Video.SOURCE_KSPO, mediaUrl);
    }

    /**
     * 영상 인용. 유튜브 영상은 코퍼스와 같은 "video:&lt;id&gt;" · 「국민체력100 운동영상 · 제목」 · 보기 주소, 공단 영상은 AI 와 같은
     * "kspo:&lt;id&gt;" · AI 표의 인용 이름(citation_label, 예: 「국민체력100 운동처방동영상 · 걷기」) · mp4 주소다. 인용 이름이 없는
     * 영상(V165 앞에 실린 채 꺼진 영상)은 「국민체력100 동영상 정보 · 제목」 이다.
     */
    private static Citation videoCitation(
            int index, String videoId, @Nullable ExerciseVideo video, String fallbackTitle) {
        String title = video == null ? fallbackTitle : video.getTitle();
        String mediaUrl = VideoMedia.of(video).mediaUrl();
        if (mediaUrl != null) {
            String label = video == null ? null : video.getCitationLabel();
            return new Citation(
                    index, label != null ? label : KSPO_CITATION + ", " + title, KSPO_CHUNK_PREFIX + videoId, mediaUrl);
        }
        return new Citation(
                index,
                "국민체력100 운동영상, " + title,
                VIDEO_CHUNK_PREFIX + videoId,
                video == null ? ExerciseVideo.youtubeUrl(videoId) : video.getUrl());
    }

    private static List<CoachRunResult.Step> steps(
            @Nullable LatestFitness latest,
            @Nullable FitnessFactor factor,
            CoachRunConditions conditions,
            String failureSummary,
            List<ExerciseClip> routine,
            Plan plan) {
        long videoCount = plan.citations().stream()
                .filter(it -> it.chunkId().startsWith(VIDEO_CHUNK_PREFIX)
                        || it.chunkId().startsWith(KSPO_CHUNK_PREFIX))
                .count();
        String retrieved =
                routine.isEmpty() ? "영상 라벨로 편성, 영상 " + videoCount + "편" : "클립 라벨로 편성, 클립 " + routine.size() + "개";
        String composed = routine.isEmpty()
                ? "하루 " + conditions.minutes() + "분, 칸 1개"
                : "하루 " + conditions.minutes() + "분, 준비 " + count(routine, SessionPhase.WARMUP) + ", 본 "
                        + count(routine, SessionPhase.MAIN) + ", 정리 " + count(routine, SessionPhase.COOLDOWN);
        return List.of(
                new CoachRunResult.Step(
                        1,
                        "assess",
                        "ok",
                        "측정 " + (latest == null ? "없음" : "있음")
                                + (factor == null
                                        ? ", 짚을 요인 없음 → " + WHOLE_BODY
                                        : ", 대상 요인 = " + factor.getLabel() + "(" + whyFactor(conditions) + ")")),
                new CoachRunResult.Step(
                        2, "retrieve", "partial", retrieveCause(failureSummary) + failureSummary + ") → " + retrieved),
                new CoachRunResult.Step(3, "compose", "ok", composed),
                new CoachRunResult.Step(
                        4, "verify", "ok", "인용 " + plan.citations().size() + "건, 연령 필터 확인"));
    }

    /** 두 번째 단계 요약의 앞머리. 심사용 한도 · AI 사용 중은 장애가 아니라 따로 적는다. */
    private static String retrieveCause(String failureSummary) {
        if (AI_LIMIT_REACHED.equals(failureSummary)) return "AI 를 부르지 않음(";
        if (AI_BUSY.equals(failureSummary)) return "AI 가 다른 편성을 짜는 중(";
        return "AI 서비스 장애(";
    }

    /** 키울 요인을 고른 까닭:보호자가 골랐으면 {@link #FOCUS_FACTOR}, 아니면(측정에서 가장 낮은 요인) {@link #WEAKEST_FACTOR}. */
    private static String whyFactor(CoachRunConditions conditions) {
        return conditions.focusFactor() == null ? WEAKEST_FACTOR : FOCUS_FACTOR;
    }

    /**
     * 최근 받은 영상과 섞을 때 쓰는 시드. rankOf 는 받은 적 없으면 0, 받았으면 최근일수록 크다(= 더 뒤로).
     * shuffleKey 는 편성 날짜와 클립 id 로 만든 값이라 같은 날 다시 짜면 같고, 날이 바뀌면 같은 순위 안의 차례가 바뀐다.
     */
    record Recent(List<String> videoIds, LocalDate runDate) {
        Recent {
            videoIds = List.copyOf(videoIds);
        }

        int rankOf(String videoId) {
            int index = videoIds.indexOf(videoId);
            return index < 0 ? 0 : videoIds.size() - index;
        }

        long shuffleKey(String clipId) {
            long z = runDate.toEpochDay() * 0x9E3779B97F4A7C15L + clipId.hashCode();
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
    }

    /** AI 요청과 같은 조건(CoachRunPipeline.prepare): 조용히면 조용한 것, 집이면 좁은 곳에서 되는 것, 늘 도구 없는 것. */
    private static boolean fits(ExerciseClip clip, CoachRunConditions conditions) {
        if (conditions.quiet() && !clip.quiet()) return false;
        if (conditions.place() == CoachPlace.HOME && !clip.homeOk()) return false;
        return !clip.needsProps();
    }

    /**
     * 작을수록 앞. 요인이 같으면 −2, 처방 어휘 이름이 있으면 −1(ai:video/catalog.py _rank 에서 처방 동작 −4 를 뺀 것).
     * 요인이 없으면(전신) 요인 가산점도 없다.
     */
    private static int score(ExerciseClip clip, @Nullable FitnessFactor factor) {
        int score = 0;
        if (factor != null && clip.factor() == factor) score -= 2;
        if (clip.exerciseName() != null) score -= 1;
        return score;
    }

    private static int countOf(SessionClipCounts counts, SessionPhase phase) {
        return switch (phase) {
            case WARMUP -> counts.warmup();
            case MAIN -> counts.main();
            case COOLDOWN -> counts.cooldown();
        };
    }

    private static long count(List<ExerciseClip> routine, SessionPhase phase) {
        return routine.stream().filter(it -> it.phase() == phase).count();
    }

    private static Map<SessionPhase, String> phaseNames() {
        Map<SessionPhase, String> names = new LinkedHashMap<>();
        names.put(SessionPhase.WARMUP, "준비운동");
        names.put(SessionPhase.MAIN, MAIN_PHASE);
        names.put(SessionPhase.COOLDOWN, "정리운동");
        return names;
    }

    /**
     * 대상 요인. ai:coach/compose.py {@code target_factor} 와 같은 규칙이다 — 보호자가 키워 주고 싶은 역량이 있으면 그것이고,
     * 없으면 측정에서 백분위가 가장 낮은 요인이다. 백분위가 높아도 강한 요인으로 넘어가지 않는다. 고른 역량의 백분위는 측정의
     * 약점 · 강점과 같은 요인일 때만 인용한다(그 밖의 항목 백분위는 여기서 모른다).
     */
    private static @Nullable Target target(@Nullable FitnessFactor focus, @Nullable LatestFitness latest) {
        FactorPoint weakest = latest == null ? null : latest.weakest();
        FactorPoint strongest = latest == null ? null : latest.strongest();
        if (focus != null) {
            FactorPoint point = weakest != null && weakest.factor() == focus
                    ? weakest
                    : (strongest != null && strongest.factor() == focus ? strongest : null);
            return new Target(focus, point);
        }
        return weakest == null ? null : new Target(weakest.factor(), weakest);
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
