package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;

/**
 * AI proposal → 제안 항목 변환(계약 §5).
 * missions[i] → position=i, title, rationale=copy.parent, targetMetric=TIMER_MINUTES, targetValue=Σduration_min,
 * video=첫 video≠null 세션(카탈로그에 있는 것만), participants=ref→profileId 역매핑, citations=evidence 가 가리키는 것(없으면 전체).
 */
public class ProposalConverter {
    public static final int MAX_TITLE = 120;
    public static final int MAX_COPY = 400;

    private final Map<String, UUID> refIndex;
    private final Map<UUID, ProfileRole> roles;
    private final Map<UUID, String> coachRoles;
    private final Set<String> knownVideoIds;

    public ProposalConverter(
            Map<String, UUID> refIndex,
            Map<UUID, ProfileRole> roles,
            Map<UUID, String> coachRoles,
            Set<String> knownVideoIds) {
        this.refIndex = refIndex;
        this.roles = roles;
        this.coachRoles = coachRoles;
        this.knownVideoIds = knownVideoIds;
    }

    public List<CoachProposalItem> convert(CoachRunResult.Proposal proposal) {
        List<CoachProposalItem> items = new ArrayList<>();
        List<CoachRunResult.Mission> missions = proposal.missions();
        for (int index = 0; index < missions.size(); index++) {
            CoachRunResult.Mission mission = missions.get(index);
            List<CoachRunResult.Session> sessions = mission.sessions();
            Set<Integer> evidence = new LinkedHashSet<>();
            sessions.forEach(it -> evidence.addAll(it.evidence()));
            List<ProposalCitation> citations = proposal.citations().stream()
                    .filter(it -> evidence.isEmpty() || evidence.contains(it.index()))
                    .map(it -> new ProposalCitation(it.index(), it.label(), it.chunkId(), it.url()))
                    .toList();
            ProposalVideo video = firstKnownVideo(sessions);
            List<ProposalParticipant> participants = new ArrayList<>();
            for (CoachRunResult.ParticipantRef p : mission.participants()) {
                UUID profileId = refIndex.get(p.ref());
                if (profileId == null) continue;
                ProfileRole role = roles.getOrDefault(profileId, ProfileRole.CHILD);
                String coachRole =
                        p.role().isBlank() ? coachRoles.getOrDefault(profileId, CoachRoles.DRIVER) : p.role();
                participants.add(new ProposalParticipant(profileId, role, coachRole));
            }
            int totalMinutes = sessions.stream()
                    .mapToInt(CoachRunResult.Session::durationMin)
                    .sum();
            String description = sessions.stream()
                    .map(it -> "D+" + it.dayOffset() + " " + it.exerciseName() + " " + it.durationMin() + "분")
                    .collect(Collectors.joining(" · "));
            items.add(new CoachProposalItem(
                    index,
                    take(mission.title(), MAX_TITLE),
                    TargetMetric.TIMER_MINUTES.name(),
                    Math.max(totalMinutes, 1),
                    take(mission.copyParent(), MAX_COPY),
                    take(description, MAX_COPY),
                    LocalDate.parse(mission.startDate()),
                    LocalDate.parse(mission.endDate()),
                    List.copyOf(participants),
                    video,
                    citations,
                    take(mission.copyChild(), MAX_COPY),
                    take(mission.copyParent(), MAX_COPY)));
        }
        return List.copyOf(items);
    }

    private @Nullable ProposalVideo firstKnownVideo(List<CoachRunResult.Session> sessions) {
        for (CoachRunResult.Session session : sessions) {
            CoachRunResult.Video video = session.video();
            if (video == null) continue;
            return knownVideoIds.contains(video.videoId())
                    ? new ProposalVideo(video.videoId(), video.startSec())
                    : null;
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
