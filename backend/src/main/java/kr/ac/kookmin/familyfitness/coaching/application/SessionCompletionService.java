package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCompleted;
import kr.ac.kookmin.familyfitness.coaching.api.SessionCompleted;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidInputException;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotActiveException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionSession;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionCompletion;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionTooShortException;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.progress.api.ProgressRecorder;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 한 칸 끝(POST /missions/{missionId}/sessions/{seq}/complete). 한 트랜잭션에서 판정 → 기록 → 활동 → 진행 → 경험치 → 이벤트.
 *
 * <p>판정 차례(FE 목 fe:src/mocks/handlers.ts 칸 끝과 같은 차례에 권한 · 기간 · 시간을 끼웠다):
 *
 * <ol>
 *   <li>미션 행을 SELECT … FOR UPDATE 로 잠그고 읽는다. 없거나 기다리는 사이 지워졌으면 404 MISSION_NOT_FOUND
 *   <li>칸 없음 404 SESSION_NOT_FOUND — 칸 없는 미션은 1번을 미션 전체 한 칸으로 받는다(결정 35)
 *   <li>이 계정이 그 프로필 이름으로 할 수 없음 403 — 자기 프로필이거나, 부모가 계정 없는 아이 이름으로만 된다
 *   <li>참여자 아님 403 NOT_A_PARTICIPANT
 *   <li>보호자 동의 없음 422 CONSENT_REQUIRED(결정 4)
 *   <li>이미 끝낸 칸 → 200 · xpGained 0. 아무것도 다시 쓰지 않는다(끝낸 날도 옮기지 않는다). 응답을 잃어 다시 보낸 요청이
 *       자정을 넘겨도 성공으로 답하도록 기간 · 시간 검사보다 앞에 둔다
 *   <li>오늘(KST)이 기간 밖 422 MISSION_NOT_ACTIVE(결정 22 · 36)
 *   <li>endedAt ≤ startedAt 400. 인정 초 = min(activeSeconds, endedAt − startedAt), 칸 시간의 절반 미만 422 TOO_SHORT(결정 3-1)
 * </ol>
 *
 * <p>기록: 끝낸 사람에게 칸 끝 한 행. 끝낸 사람이 아이면 같이 하기로 한 보호자에게도 한 행씩 번진다(결정 34,
 * {@link Mission#companionsOf}) — 형제에게는 번지지 않고, 보호자가 끝낸 칸은 그 보호자 것뿐이다. 새로 적은 사람마다 같은 인정 초를
 * 활동(VIDEO)에 쌓고, 진행도를 다시 셈한 뒤 경험치를 적립한다(활동을 먼저 쌓아야 누적 분 업적이 이번 칸까지 센다).
 *
 * <p>미션 행을 맨 앞에서 잠그는 까닭(지우기 · 느낌과 같은 잠금): 진행도는 커밋된 칸 끝 기록으로 셈해 참여자 행을 통째로 덮어쓴다. 잠그지
 * 않으면 같은 미션의 서로 다른 칸을 동시에 끝낸 두 요청이 서로의 칸 끝 행을 못 본 채 셈해, 두 칸 모두 번진 보호자가 끝나지 않고
 * MissionCompleted · 미션 끝 +20 이 빠졌다(SA-10). 같은 날 활동 행도 두 요청이 같은 값을 읽어 고쳐 써 한 칸 분을 잃었다. 잠그면 같은
 * 미션의 칸 끝 · 느낌 · 지우기가 차례로 돈다 — 늦은 쪽은 먼저 온 쪽이 커밋하길 기다렸다가 그 기록을 보고 셈한다. 셋 다 미션 행을 먼저
 * 잠그므로 서로를 기다리다 멈추지 않는다. 지우기가 먼저 잠갔으면 기다렸다가 지워진 미션을 보고 404 다.
 */
@Service
public class SessionCompletionService {
    private final MissionRepository missions;
    private final SessionCompletionRepository completions;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profiles;
    private final ActivityRecorder activity;
    private final ProgressRecorder progress;
    private final MissionCompletionPolicy policy;
    private final ApplicationEventPublisher events;
    private final AppTime time;

    public SessionCompletionService(
            MissionRepository missions,
            SessionCompletionRepository completions,
            FamilyAccess familyAccess,
            ProfileQuery profiles,
            ActivityRecorder activity,
            ProgressRecorder progress,
            MissionCompletionPolicy policy,
            ApplicationEventPublisher events,
            AppTime time) {
        this.missions = missions;
        this.completions = completions;
        this.familyAccess = familyAccess;
        this.profiles = profiles;
        this.activity = activity;
        this.progress = progress;
        this.policy = policy;
        this.events = events;
        this.time = time;
    }

    @Transactional
    public SessionCompletedView complete(UUID userId, UUID missionId, int position, CompleteSessionCommand command) {
        Mission mission = missions.findByIdForUpdate(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        MissionSession session = mission.plannedSession(position);
        if (session == null) throw new SessionNotFoundException(missionId, position);
        ProfileSummary actor = familyAccess.requireActingAs(userId, command.profileId());
        mission.participantOf(actor.profileId());
        ParticipantConsent.require(actor);

        SessionCompletion existing = completions.find(missionId, position, actor.profileId());
        if (existing != null) return alreadyDone(mission, existing);

        LocalDate today = time.today();
        if (!mission.isActiveOn(today)) {
            throw new MissionNotActiveException(mission.getStartsOn(), mission.getEndsOn(), today);
        }
        int seconds = creditedSeconds(session, command);
        Instant now = time.now();

        List<UUID> recorded = record(mission, session, recipientsOf(mission, actor), seconds, today, now);
        Set<UUID> completedBefore = recorded.stream()
                .filter(it -> mission.participantOf(it).isCompleted())
                .collect(Collectors.toSet());
        Mission updated = policy.refreshParticipants(mission, recorded, now);
        int xpGained = 0;
        for (UUID profileId : recorded) {
            MissionParticipant participant = updated.participantOf(profileId);
            int gained = progress.sessionDone(sessionDone(updated, session, participant, today, now));
            if (profileId.equals(actor.profileId())) xpGained = gained;
            publish(updated, session.position(), participant, completedBefore.contains(profileId), today, now);
        }
        MissionParticipant me = updated.participantOf(actor.profileId());
        return new SessionCompletedView(
                session.position(), VerifiedBy.VIDEO_PROGRESS, me.getProgress(), me.isCompleted(), xpGained);
    }

    /** 이미 끝낸 칸 — 저장된 진행 그대로, 경험치 0. */
    private static SessionCompletedView alreadyDone(Mission mission, SessionCompletion existing) {
        MissionParticipant me = mission.participantOf(existing.profileId());
        return new SessionCompletedView(
                existing.position(), existing.verifiedBy(), me.getProgress(), me.isCompleted(), 0);
    }

    /**
     * 인정 초. endedAt ≤ startedAt 이면 400. 재생 초가 기기 시각의 간격보다 길면 간격으로 자른다.
     * 칸 시간(분 × 60)의 절반 미만이면 422 TOO_SHORT(FE 목 {@code activeSeconds < planned × 0.5} 와 같다).
     * 칸 시간은 long 으로 셈한다 — 상한이 생기기 전에 만든 칸 없는 미션은 분이 아주 커서 int 곱셈이 음수로 넘쳤다(SA-11).
     */
    private static int creditedSeconds(MissionSession session, CompleteSessionCommand command) {
        if (!command.endedAt().isAfter(command.startedAt())) {
            throw new InvalidInputException("endedAt 은 startedAt 이후여야 합니다");
        }
        long elapsed = Duration.between(command.startedAt(), command.endedAt()).getSeconds();
        long active = Math.min(command.activeSeconds(), elapsed);
        long planned = session.minutes() * 60L;
        if (active * 2 < planned) throw new SessionTooShortException(active, planned);
        return (int) active;
    }

    /** 끝낸 사람, 그리고 아이면 같이 하기로 한 보호자. 동의가 없거나 거둔 보호자는 뺀다(활동을 저장하지 않는다, 결정 4). */
    private List<UUID> recipientsOf(Mission mission, ProfileSummary actor) {
        List<UUID> recipients = new ArrayList<>(List.of(actor.profileId()));
        if (actor.role() != ProfileRole.CHILD) return recipients;
        Set<UUID> parents = profiles.summariesOfFamily(mission.getFamilyId()).stream()
                .filter(ProfileSummary::isParent)
                .filter(it -> !ParticipantConsent.missing(it))
                .map(ProfileSummary::profileId)
                .collect(Collectors.toSet());
        mission.companionsOf(parents).stream()
                .filter(it -> !it.equals(actor.profileId()))
                .forEach(recipients::add);
        return recipients;
    }

    /** 칸 끝 행을 넣고 인정 초를 활동(VIDEO)에 쌓는다. 그 칸을 이미 끝낸 사람(스스로 먼저 한 보호자)은 건너뛴다. 새로 적은 사람. */
    private List<UUID> record(
            Mission mission, MissionSession session, List<UUID> recipients, int seconds, LocalDate today, Instant now) {
        List<UUID> recorded = new ArrayList<>();
        for (UUID profileId : recipients) {
            if (completions.find(mission.getId(), session.position(), profileId) != null) continue;
            completions.insert(new SessionCompletion(
                    mission.getId(), session.position(), profileId, now, today, seconds, VerifiedBy.VIDEO_PROGRESS));
            activity.addActiveSeconds(profileId, today, ActivitySource.VIDEO, seconds);
            recorded.add(profileId);
        }
        return recorded;
    }

    private static SessionDone sessionDone(
            Mission mission, MissionSession session, MissionParticipant participant, LocalDate today, Instant now) {
        VerifiedBy verifiedBy = participant.getVerifiedBy();
        return new SessionDone(
                mission.getFamilyId(),
                participant.getProfileId(),
                mission.getId(),
                session.position(),
                SessionDone.Phase.valueOf(session.phase().name()),
                session.factor(),
                participant.isCompleted(),
                verifiedBy == null ? null : SessionDone.Verification.valueOf(verifiedBy.name()),
                today,
                now);
    }

    /** 새로 적은 사람마다 SessionCompleted, 이 칸으로 미션이 막 끝났으면 MissionCompleted 도(결정 42). */
    private void publish(
            Mission mission,
            int position,
            MissionParticipant participant,
            boolean completedBefore,
            LocalDate today,
            Instant now) {
        events.publishEvent(new SessionCompleted(
                mission.getFamilyId(),
                mission.getId(),
                position,
                participant.getProfileId(),
                today,
                now,
                participant.isCompleted()));
        if (participant.isCompleted() && !completedBefore) {
            events.publishEvent(
                    new MissionCompleted(mission.getId(), participant.getProfileId(), mission.getFamilyId(), now));
        }
    }
}
