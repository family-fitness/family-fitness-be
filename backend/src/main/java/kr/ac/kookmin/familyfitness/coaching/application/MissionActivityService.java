package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.activity.api.ActivityRecorder;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionParticipant;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미션 경로의 활동 기록. 기록 자체는 activity 모듈({@link ActivityRecorder})이 하고, 이쪽은 참여자 진행도를 갱신한다.
 * 활동 날짜는 앱 시간대(KST) 기준이다.
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

    /** 걸음수는 그날 총량으로 덮어쓴다(MANUAL). 도달해도 보호자 확인 전에는 완료가 아니다. */
    @Transactional
    public StepsRecordedView recordSteps(UUID userId, UUID missionId, RecordStepsCommand command) {
        if (command.activityDate().isAfter(time.today())) {
            throw new IllegalArgumentException("activityDate 는 미래일 수 없습니다");
        }
        Mission mission = missions.findById(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        familyAccess.requireMember(userId, mission.getFamilyId());
        mission.participantOf(command.profileId());
        mission.requireMetric(TargetMetric.STEPS);

        recorder.overwriteSteps(command.profileId(), command.activityDate(), command.steps());
        MissionParticipant participant = policy.refreshParticipant(mission, command.profileId(), time.now())
                .participantOf(command.profileId());
        return new StepsRecordedView(
                ActivitySource.MANUAL,
                ActivitySource.MANUAL.isServerVerified(),
                VerifiedBy.SELF_REPORT,
                participant.getProgress(),
                participant.isCompleted(),
                participant.isNeedsGuardianCheck());
    }

    /** 타이머 분 누적(TIMER). `endedAt-startedAt` 분을 넘으면 그 값으로 자른다(최소 1분). */
    @Transactional
    public TimerRecordedView recordTimer(UUID userId, UUID missionId, RecordTimerCommand command) {
        if (!command.endedAt().isAfter(command.startedAt())) {
            throw new IllegalArgumentException("endedAt 은 startedAt 이후여야 합니다");
        }
        Mission mission = missions.findById(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        familyAccess.requireMember(userId, mission.getFamilyId());
        mission.participantOf(command.profileId());
        mission.requireMetric(TargetMetric.TIMER_MINUTES);

        LocalDate activityDate = time.dateOf(command.startedAt());
        long elapsed = Duration.between(command.startedAt(), command.endedAt()).toMinutes();
        int minutes = (int) Math.max(Math.min(command.activeMinutes(), elapsed), 1L);
        recorder.addActiveMinutes(command.profileId(), activityDate, ActivitySource.TIMER, minutes);
        MissionParticipant participant = policy.refreshParticipant(mission, command.profileId(), time.now())
                .participantOf(command.profileId());
        return new TimerRecordedView(
                activityDate,
                ActivitySource.TIMER,
                ActivitySource.TIMER.isServerVerified(),
                activityQuery.activeMinutesOn(command.profileId(), activityDate),
                participant.getProgress(),
                participant.isCompleted());
    }
}
