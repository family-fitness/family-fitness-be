package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachProposalItem;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRoles;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachStep;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalCitation;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.ProposalVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.shared.ai.CoachRunResult;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRef;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/**
 * AI proposal → 제안 항목 변환(AI 인터페이스-명세 4장).
 * missions[i] → position=i, title, targetMetric=TIMER_MINUTES, targetValue=duration_min(최소 1),
 * rationale=reason(비었으면 copy.parent), video=order 순으로 첫 영상 있는 세션,
 * participants=편성 대상 한 명(+ withParent 면 요청한 보호자), citations=evidence 가 가리키는 것(없으면 전체).
 * 영상은 BE 카탈로그(exercise_videos)에 없어도 버리지 않는다 — 화면은 videoId 로 유튜브 구간을 튼다.
 */
public class ProposalConverter {
    public static final int MAX_TITLE = 120;
    public static final int MAX_COPY = 400;

    private static final Comparator<CoachRunResult.Session> BY_ORDER =
            Comparator.comparing(CoachRunResult.Session::order, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UUID subjectProfileId;
    private final ProfileRole subjectRole;
    private final @Nullable UUID companionProfileId;

    /**
     * @param companionProfileId withParent 면 편성을 요청한 보호자(run.requestedBy), 아니면 null. AI 는 일간 미션에 동반자를 넣지 않으므로
     *     (ai:coach/compose.py) BE 가 붙인다(결정 2).
     */
    public ProposalConverter(UUID subjectProfileId, ProfileRole subjectRole, @Nullable UUID companionProfileId) {
        this.subjectProfileId = subjectProfileId;
        this.subjectRole = subjectRole;
        this.companionProfileId = companionProfileId;
    }

    public List<CoachProposalItem> convert(CoachRunResult.Proposal proposal) {
        List<CoachProposalItem> items = new ArrayList<>();
        List<CoachRunResult.Mission> missions = proposal.missions();
        for (int index = 0; index < missions.size(); index++) {
            CoachRunResult.Mission mission = missions.get(index);
            List<CoachRunResult.Session> sessions =
                    mission.sessions().stream().sorted(BY_ORDER).toList();
            Set<Integer> evidence = new LinkedHashSet<>();
            sessions.forEach(it -> evidence.addAll(it.evidence()));
            List<ProposalCitation> citations = proposal.citations().stream()
                    .filter(it -> evidence.isEmpty() || evidence.contains(it.index()))
                    .map(it -> new ProposalCitation(it.index(), it.label(), it.chunkId(), it.url()))
                    .toList();
            items.add(new CoachProposalItem(
                    index,
                    take(mission.title(), MAX_TITLE),
                    TargetMetric.TIMER_MINUTES.name(),
                    targetMinutes(mission),
                    take(rationale(mission), MAX_COPY),
                    take(description(sessions), MAX_COPY),
                    LocalDate.parse(mission.startDate()),
                    LocalDate.parse(mission.endDate()),
                    participants(mission),
                    firstVideo(sessions),
                    citations,
                    take(mission.copyChild(), MAX_COPY),
                    take(mission.copyParent(), MAX_COPY)));
        }
        return List.copyOf(items);
    }

    /** 목표 분 = AI 가 준 그 회 운동 시간(요청한 분). 클립 길이의 합(video_sec)이 아니다. 없으면 1. */
    static int targetMinutes(CoachRunResult.Mission mission) {
        Integer minutes = mission.durationMin();
        return Math.max(minutes == null ? 0 : minutes, 1);
    }

    /** 「왜 이렇게 짰는지」 는 reason([n] 인용이 박힌 문장). 옛 응답처럼 비었으면 부모용 문구로 물러선다. */
    static String rationale(CoachRunResult.Mission mission) {
        return mission.reason().isBlank() ? mission.copyParent() : mission.reason();
    }

    /**
     * AI 가 준 참여자 중 편성 대상만 남긴다(응원 · 다른 구성원은 뺀다 — 응원 부모가 참여자가 되면 미션이 DONE 이 되지 않는다).
     * 대상이 든 미션이면 withParent 보호자를 동반자로 덧붙인다. 대상이 없는 미션은 참여자가 비어 승인 때 미션이 되지 않는다.
     */
    private List<ProposalParticipant> participants(CoachRunResult.Mission mission) {
        String subjectRef = ProfileRef.of(subjectProfileId);
        CoachRunResult.ParticipantRef subject = mission.participants().stream()
                .filter(it -> it.ref().equals(subjectRef))
                .findFirst()
                .orElse(null);
        if (subject == null) return List.of();
        List<ProposalParticipant> participants = new ArrayList<>();
        participants.add(new ProposalParticipant(
                subjectProfileId, subjectRole, subject.role().isBlank() ? CoachRoles.DRIVER : subject.role()));
        if (companionProfileId != null && !companionProfileId.equals(subjectProfileId)) {
            participants.add(new ProposalParticipant(companionProfileId, ProfileRole.PARENT, CoachRoles.COMPANION));
        }
        return List.copyOf(participants);
    }

    private static String description(List<CoachRunResult.Session> sessions) {
        return sessions.stream()
                .map(it -> it.phase().isBlank() ? it.exerciseName() : it.phase() + " " + it.exerciseName())
                .collect(Collectors.joining(" · "));
    }

    private static @Nullable ProposalVideo firstVideo(List<CoachRunResult.Session> sessions) {
        for (CoachRunResult.Session session : sessions) {
            CoachRunResult.Video video = session.video();
            if (video != null) return new ProposalVideo(video.videoId(), video.startSec());
        }
        return null;
    }

    public static List<CoachStep> steps(CoachRunResult result) {
        return result.steps().stream()
                .map(it -> new CoachStep(it.seq(), it.name(), it.status(), it.summary()))
                .toList();
    }

    /** 요약 = 첫 미션의 부모용 문구, 없으면 단계 요약을 이어 붙인 것. */
    public static @Nullable String summary(CoachRunResult result) {
        CoachRunResult.Proposal proposal = result.proposal();
        if (proposal != null && !proposal.missions().isEmpty()) {
            String copyParent = proposal.missions().getFirst().copyParent();
            if (!copyParent.isBlank()) return copyParent;
        }
        String joined =
                result.steps().stream().map(CoachRunResult.Step::summary).collect(Collectors.joining(" · "));
        return joined.isBlank() ? null : joined;
    }

    private static String take(String value, int n) {
        return value.length() <= n ? value : value.substring(0, n);
    }
}
