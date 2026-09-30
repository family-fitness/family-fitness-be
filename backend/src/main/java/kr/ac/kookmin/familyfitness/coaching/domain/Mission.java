package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 부모가 직접 만들었거나 승인된 제안에서 만들어진 가족의 실행 과제.
 * 승인({@link CoachRun#approve})과 직접 만들기 외에는 미션이 생기지 않는다.
 */
public class Mission {
    /**
     * 칸 없는 분 목표 미션의 목표 분 상한. 그런 미션은 미션 전체가 한 칸이고(결정 35) 칸은 재생 시간이 칸 시간의 절반 이상이어야
     * 끝난다(결정 3-1). 칸 끝 한 번에 받는 재생 초 상한이 {@link SessionCompletion#MAX_ACTIVE_SECONDS}(10800초)라, 그 두 배인
     * 360분을 넘는 미션은 끝낼 수 없다(SA-11). FE 화면 · 목에는 이 값의 상한이 없어 끝낼 수 있는 최대값을 쓴다.
     */
    public static final int MAX_WHOLE_MINUTES = SessionCompletion.MAX_ACTIVE_SECONDS * 2 / 60;

    private final UUID id;
    private final UUID familyId;
    private final @Nullable UUID coachRunId;
    private final String title;
    private final @Nullable String description;
    private final MissionOrigin origin;
    private final TargetMetric targetMetric;
    private final int targetValue;
    private final @Nullable MissionVideo video;
    private final @Nullable String rationale;
    private final LocalDate startsOn;
    private final LocalDate endsOn;
    private final @Nullable UUID createdBy;
    private final Instant createdAt;
    private final List<MissionParticipant> participants;
    private final List<MissionSession> sessions;

    private Mission(
            UUID id,
            UUID familyId,
            @Nullable UUID coachRunId,
            String title,
            @Nullable String description,
            MissionOrigin origin,
            TargetMetric targetMetric,
            int targetValue,
            @Nullable MissionVideo video,
            @Nullable String rationale,
            LocalDate startsOn,
            LocalDate endsOn,
            @Nullable UUID createdBy,
            Instant createdAt,
            List<MissionParticipant> participants,
            List<MissionSession> sessions) {
        if (targetValue <= 0) throw new IllegalArgumentException("목표값은 0보다 커야 한다");
        if (endsOn.isBefore(startsOn)) throw new IllegalArgumentException("endDate 는 startDate 이후여야 한다");
        if (participants.isEmpty()) throw new IllegalArgumentException("참여자가 한 명 이상 있어야 한다");
        if ((origin == MissionOrigin.COACH) != (coachRunId != null)) {
            throw new IllegalArgumentException("COACH 미션만 coachRunId 를 가진다");
        }
        this.id = id;
        this.familyId = familyId;
        this.coachRunId = coachRunId;
        this.title = title;
        this.description = description;
        this.origin = origin;
        this.targetMetric = targetMetric;
        this.targetValue = targetValue;
        this.video = video;
        this.rationale = rationale;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.participants = List.copyOf(participants);
        this.sessions = MissionSession.ordered(sessions);
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public @Nullable UUID getCoachRunId() {
        return coachRunId;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public MissionOrigin getOrigin() {
        return origin;
    }

    public TargetMetric getTargetMetric() {
        return targetMetric;
    }

    public int getTargetValue() {
        return targetValue;
    }

    public @Nullable MissionVideo getVideo() {
        return video;
    }

    public @Nullable String getRationale() {
        return rationale;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public @Nullable UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<MissionParticipant> getParticipants() {
        return participants;
    }

    /** 참여자 프로필 id, 저장된 차례. */
    public List<UUID> participantProfileIds() {
        return participants.stream().map(MissionParticipant::getProfileId).toList();
    }

    /** position 차례의 칸. 칸 없는 미션은 빈 목록이다. */
    public List<MissionSession> getSessions() {
        return sessions;
    }

    public boolean hasSessions() {
        return !sessions.isEmpty();
    }

    /**
     * 칸 끝이 받는 칸. 칸 없는 미션은 미션 전체를 본운동 한 칸(position 1)으로 본다(결정 35) — 그 칸의 분은
     * FE session-plan(fe:src/lib/session-plan.ts sessionsOf · stepMinutes)과 같게 분 목표면 targetValue, 아니면 1분이다.
     * 이 칸은 저장하지 않는다 — 응답의 sessions 는 여전히 [] 다(서버가 칸을 지어내 싣지 않는다, FE 요청서 4장).
     * 그 번호의 칸이 없으면 null.
     */
    public @Nullable MissionSession plannedSession(int position) {
        List<MissionSession> planned = plannedSessions();
        return position >= 1 && position <= planned.size() ? planned.get(position - 1) : null;
    }

    /** 칸 끝이 받는 칸 전부, position 차례. 칸 없는 미션은 미션 전체 한 칸이다({@link #plannedSession}). */
    public List<MissionSession> plannedSessions() {
        if (hasSessions()) return sessions;
        int minutes = targetMetric == TargetMetric.TIMER_MINUTES ? targetValue : 1;
        return List.of(new MissionSession(1, SessionPhase.MAIN, title, null, minutes, null));
    }

    /** 칸 끝을 받는 날인가 — startDate ≤ 오늘(KST) ≤ endDate(결정 22). 여러 날짜리는 기간 안 어느 날이든 된다. */
    public boolean isActiveOn(LocalDate today) {
        return !today.isBefore(startsOn) && !today.isAfter(endsOn);
    }

    /**
     * 아이가 끝낸 칸을 같이 끝내는 보호자(결정 34). 코치 미션은 편성 역할이 동반자인 참여자, 직접 짜기 미션은 보호자
     * 참여자 전원이다(부모가 직접 골라 넣었다). 응원만 하는 부모에게는 번지지 않고, 형제(보호자가 아닌 참여자)에게도 번지지 않는다.
     *
     * @param parentProfileIds 이 가족의 보호자 프로필. 역할은 identity 가 들고 있어 부르는 쪽이 넘긴다
     */
    public List<UUID> companionsOf(Set<UUID> parentProfileIds) {
        return participants.stream()
                .filter(it -> parentProfileIds.contains(it.getProfileId()))
                .filter(it -> origin != MissionOrigin.COACH || CoachRoles.COMPANION.equals(it.getCoachRole()))
                .map(MissionParticipant::getProfileId)
                .toList();
    }

    public boolean isServerVerifiable() {
        return targetMetric.isServerVerifiable();
    }

    public boolean isAllCompleted() {
        return participants.stream().allMatch(MissionParticipant::isCompleted);
    }

    public MissionParticipant participantOf(UUID profileId) {
        return participants.stream()
                .filter(it -> it.getProfileId().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new NotParticipantException(id, profileId));
    }

    public boolean isParticipant(UUID profileId) {
        return participants.stream().anyMatch(it -> it.getProfileId().equals(profileId));
    }

    public void requireMetric(TargetMetric expected) {
        if (targetMetric != expected) throw new InvalidMetricException(expected, targetMetric);
    }

    /** 정책이 계산한 진행도를 참여자에게 반영한다. 서버 검증 지표는 도달 즉시 완료된다. */
    public boolean recordProgress(UUID profileId, MissionProgress computed, Instant at) {
        return participantOf(profileId).apply(computed, isServerVerifiable(), at);
    }

    /**
     * 보호자 확인. 서버가 재는 지표(타이머 · 영상 · 칸)는 확인할 것이 없어 바꾸지 않는다 — 이미 확인됐거나 아직 덜 했을 뿐이다
     * (FE 요청서 4장 「확인할 것이 없으면 바꾸지 않고 200」, fe:src/mocks/handlers.ts confirm). 걸음수만 확인으로 끝난다.
     */
    public MissionParticipant confirm(UUID profileId, UUID confirmedBy, Instant at) {
        MissionParticipant participant = participantOf(profileId);
        if (isServerVerifiable()) return participant;
        participant.confirm(confirmedBy, at);
        return participant;
    }

    public MissionStatus statusOn(LocalDate today) {
        if (isAllCompleted()) return MissionStatus.DONE;
        if (today.isAfter(endsOn)) return MissionStatus.EXPIRED;
        return MissionStatus.ACTIVE;
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        return !startsOn.isAfter(to) && !endsOn.isBefore(from);
    }

    /**
     * 직접 만들기 · 여러 날 만들기의 날짜 검사(결정 40 · 46). 시작일이 오늘(KST)보다 앞이면 422 INVALID_DATE.
     * 오늘은 받는다. endDate ≥ startDate 는 생성자가 따로 본다.
     */
    public static void requireNotPast(LocalDate startsOn, LocalDate today) {
        if (startsOn.isBefore(today)) throw new InvalidMissionDateException(startsOn, today);
    }

    /**
     * 지울 수 있는 미션인가(결정 40). 차례:
     * <ol>
     *   <li>기간이 끝났으면(endDate &lt; 오늘 KST) 409 MISSION_ENDED — 지난 미션은 리그 · 달력 기록이다
     *   <li>누군가 칸을 끝냈거나(칸 끝 행) 참여자가 완료됐으면(옛 타이머 · 걸음수 · 영상 경로) 409 MISSION_ALREADY_STARTED
     * </ol>
     * 진행도가 0 보다 크기만 한 참여자는 막지 않는다 — 칸 없는 옛 타이머 미션의 진행도는 그 기간의 다른 활동 합이라 이 미션을 했다는 뜻이 아니다.
     */
    public void requireCancellableOn(LocalDate today, MissionCompletions done) {
        if (today.isAfter(endsOn)) throw new MissionEndedException(id, endsOn, today);
        boolean anyCompleted = participants.stream().anyMatch(MissionParticipant::isCompleted);
        if (!done.isEmpty() || anyCompleted) throw new MissionAlreadyStartedException(id);
    }

    /**
     * 부모가 직접 만든 미션. 칸이 있으면 목표는 분(TIMER_MINUTES)이고 {@code targetValue} 가 칸 시간의 합과 같아야 한다 —
     * 다르면 고쳐 넣지 않고 거부한다. 칸은 보낸 position 차례 그대로 두고 단계로 다시 세우지 않는다. 칸 없는 분 목표는
     * {@link #MAX_WHOLE_MINUTES} 분까지다. 어기면 사용자 입력 오류라 400({@link InvalidInputException})이다.
     */
    public static Mission manual(
            UUID id,
            UUID familyId,
            String title,
            TargetMetric targetMetric,
            int targetValue,
            @Nullable MissionVideo video,
            LocalDate startsOn,
            LocalDate endsOn,
            List<UUID> participantProfileIds,
            List<MissionSession> sessions,
            UUID createdBy,
            Instant at) {
        String problem = sessionProblem(targetMetric, targetValue, sessions);
        if (problem != null) throw new InvalidInputException(problem);
        if (sessions.isEmpty() && targetMetric == TargetMetric.TIMER_MINUTES && targetValue > MAX_WHOLE_MINUTES) {
            throw new InvalidInputException(
                    "칸 없는 분 목표(targetValue " + targetValue + ")는 " + MAX_WHOLE_MINUTES + "분까지입니다. 넘으면 칸 끝으로 끝낼 수 없습니다");
        }
        Set<UUID> distinct = new LinkedHashSet<>(participantProfileIds);
        List<MissionParticipant> participants = new ArrayList<>();
        for (UUID profileId : distinct) {
            participants.add(MissionParticipant.pending(profileId, null, at));
        }
        return new Mission(
                id,
                familyId,
                null,
                title,
                null,
                MissionOrigin.MANUAL,
                targetMetric,
                targetValue,
                video,
                null,
                startsOn,
                endsOn,
                createdBy,
                at,
                participants,
                sessions);
    }

    /**
     * 칸 규칙을 어긴 까닭, 지키면 null. 칸 번호는 1..n 으로 빈틈없이 이어져야 하고, 목표는 칸을 다 했을 때 딱 채워지는 분이어야 한다.
     * 합보다 크면 칸을 다 해도 미션이 안 끝나고, 작으면 칸을 덜 해도 끝난다({@code MissionCompletionPolicy} 가 진행도를 목표 분으로
     * 나눈다). 직접 만들기는 사용자 입력이라 400 으로, 제안 복사는 서버 변환이라 {@link IllegalArgumentException} 으로 던진다.
     */
    private static @Nullable String sessionProblem(
            TargetMetric targetMetric, int targetValue, List<MissionSession> sessions) {
        if (sessions.isEmpty()) return null;
        if (!MissionSession.isNumberedFromOne(sessions)) {
            return "칸 번호(position)는 1부터 " + sessions.size() + "까지 겹치지 않아야 한다";
        }
        if (targetMetric != TargetMetric.TIMER_MINUTES) return "칸이 있는 미션의 목표 지표는 TIMER_MINUTES 여야 한다";
        int total = MissionSession.totalMinutes(sessions);
        if (targetValue != total) {
            return "칸이 있는 미션의 목표 분(targetValue " + targetValue + ")은 칸 시간의 합(" + total + "분)과 같아야 한다";
        }
        return null;
    }

    /**
     * 승인된 제안 항목의 복사. 제안에 기간이 없으면 실행의 주(월~일)를 쓴다.
     * 칸은 제안의 차례 그대로 옮긴다(결정 16) — 칸 없는 제안은 칸 없는 미션이다. 칸 분의 합 = 목표 분 규칙은 직접 만들기와 같다.
     */
    public static Mission fromProposal(UUID id, CoachRun run, CoachProposalItem item, UUID createdBy, Instant at) {
        TargetMetric targetMetric = TargetMetric.valueOf(item.targetMetric());
        String problem = sessionProblem(targetMetric, item.targetValue(), item.sessions());
        if (problem != null) throw new IllegalArgumentException(problem);
        Set<UUID> seen = new LinkedHashSet<>();
        List<MissionParticipant> participants = new ArrayList<>();
        for (ProposalParticipant participant : item.participants()) {
            if (seen.add(participant.profileId())) {
                participants.add(MissionParticipant.pending(participant.profileId(), participant.coachRole(), at));
            }
        }
        ProposalVideo video = item.video();
        return new Mission(
                id,
                run.getFamilyId(),
                run.getId(),
                item.title(),
                item.description(),
                MissionOrigin.COACH,
                targetMetric,
                item.targetValue(),
                video == null ? null : new MissionVideo(video.videoId(), video.startSec()),
                item.rationale(),
                run.startsOnOf(item),
                run.endsOnOf(item),
                createdBy,
                at,
                participants,
                item.sessions());
    }

    public static Mission reconstitute(
            UUID id,
            UUID familyId,
            @Nullable UUID coachRunId,
            String title,
            @Nullable String description,
            MissionOrigin origin,
            TargetMetric targetMetric,
            int targetValue,
            @Nullable MissionVideo video,
            @Nullable String rationale,
            LocalDate startsOn,
            LocalDate endsOn,
            @Nullable UUID createdBy,
            Instant createdAt,
            List<MissionParticipant> participants,
            List<MissionSession> sessions) {
        return new Mission(
                id,
                familyId,
                coachRunId,
                title,
                description,
                origin,
                targetMetric,
                targetValue,
                video,
                rationale,
                startsOn,
                endsOn,
                createdBy,
                createdAt,
                participants,
                sessions);
    }
}
