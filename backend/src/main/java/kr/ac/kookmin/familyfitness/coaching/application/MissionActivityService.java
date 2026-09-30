package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidInputException;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotActiveException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미션 경로의 옛 활동 기록(POST /missions/{id}/activity/timer · steps). 기록 자체는 activity 모듈({@link ActivityRecorder})이
 * 하고, 이쪽은 참여자 진행도를 갱신한다. FE 는 이 주소를 부르지 않는다(칸 끝으로 옮겼다) — 걷어 낼지는 결정 대기라 권한만 좁혀 둔다.
 *
 * <p>누구 이름으로 적는지는 칸 끝과 같다: 이 계정이 그 프로필 이름으로 할 수 있어야 한다(자기 프로필이거나, 보호자가 계정 없는 아이를
 * 대신할 때). 같은 가족이기만 하면 되던 때는 형제나 계정 있는 아이 이름으로 「움직인 날」을 적어 캘린더 · 리그 · 연속 날에 들어갔다(KP-04).
 */
@Service
public class MissionActivityService {
    private final MissionRepository missions;
    private final FamilyAccess familyAccess;
    private final ActivityRecorder recorder;
    private final ActivityQuery activityQuery;
    private final MissionCompletionPolicy policy;
    private final AppTime time;

    public MissionActivityService(
            MissionRepository missions,
            FamilyAccess familyAccess,
            ActivityRecorder recorder,
            ActivityQuery activityQuery,
            MissionCompletionPolicy policy,
            AppTime time) {
        this.missions = missions;
        this.familyAccess = familyAccess;
        this.recorder = recorder;
        this.activityQuery = activityQuery;
        this.policy = policy;
        this.time = time;
    }

    /**
     * 걸음수는 그날 총량으로 덮어쓴다(MANUAL). 도달해도 보호자 확인 전에는 완료가 아니다. 판정 차례: 앞날 400 → 미션 없음 404 →
     * 그 프로필 이름으로 할 수 없음 403 → 참여자 아님 403 → 걸음수 미션 아님 422 → 동의 없음 422.
     */
    @Transactional
    public StepsRecordedView recordSteps(UUID userId, UUID missionId, RecordStepsCommand command) {
        if (command.activityDate().isAfter(time.today())) {
            throw new InvalidInputException("activityDate 는 미래일 수 없습니다");
        }
        Mission mission = missions.findById(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        ProfileSummary actor = familyAccess.requireActingAs(userId, command.profileId());
        mission.participantOf(actor.profileId());
        mission.requireMetric(TargetMetric.STEPS);
        ParticipantConsent.require(actor);

        recorder.overwriteSteps(actor.profileId(), command.activityDate(), command.steps());
        MissionParticipant participant = policy.refreshParticipant(mission, actor.profileId(), time.now())
                .participantOf(actor.profileId());
        return new StepsRecordedView(
                ActivitySource.MANUAL,
                ActivitySource.MANUAL.isServerVerified(),
                VerifiedBy.SELF_REPORT,
                participant.getProgress(),
                participant.isCompleted(),
                participant.isNeedsGuardianCheck());
    }

    /**
     * 타이머 분 누적(TIMER). `endedAt-startedAt` 분을 넘으면 그 값으로 자른다(최소 1분). 활동 날짜는 기기가 보낸 시각이 아니라 서버가
     * 받은 날(KST)이다 — 칸 끝과 같게, 지난 날이나 앞날에 「움직인 날」을 적지 못하게. 판정 차례: endedAt ≤ startedAt 400 → 미션 없음
     * 404 → 그 프로필 이름으로 할 수 없음 403 → 참여자 아님 403 → 분 목표 미션 아님 422 → 동의 없음 422 → 오늘이 미션 기간 밖 422
     * MISSION_NOT_ACTIVE(결정 22 · 36).
     */
    @Transactional
    public TimerRecordedView recordTimer(UUID userId, UUID missionId, RecordTimerCommand command) {
        if (!command.endedAt().isAfter(command.startedAt())) {
            throw new InvalidInputException("endedAt 은 startedAt 이후여야 합니다");
        }
        Mission mission = missions.findById(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        ProfileSummary actor = familyAccess.requireActingAs(userId, command.profileId());
        mission.participantOf(actor.profileId());
        mission.requireMetric(TargetMetric.TIMER_MINUTES);
        ParticipantConsent.require(actor);
        LocalDate today = time.today();
        if (!mission.isActiveOn(today)) {
            throw new MissionNotActiveException(mission.getStartsOn(), mission.getEndsOn(), today);
        }

        long elapsed = Duration.between(command.startedAt(), command.endedAt()).toMinutes();
        int minutes = (int) Math.max(Math.min(command.activeMinutes(), elapsed), 1L);
        recorder.addActiveMinutes(actor.profileId(), today, ActivitySource.TIMER, minutes);
        MissionParticipant participant = policy.refreshParticipant(mission, actor.profileId(), time.now())
                .participantOf(actor.profileId());
        return new TimerRecordedView(
                today,
                ActivitySource.TIMER,
                ActivitySource.TIMER.isServerVerified(),
                activityQuery.activeMinutesOn(actor.profileId(), today),
                participant.getProgress(),
                participant.isCompleted());
    }
}
