package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionFeedbackRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionFeedback;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 느낌 보내기(POST /missions/{missionId}/feedback, 결정 40 · 46). 저장만 한다 — 다음 편성에 싣는 것은 AI 계약에 칸이 생긴 뒤다.
 * 판단 차례(칸 끝과 같은 차례):
 * <ol>
 *   <li>미션 없음 404 MISSION_NOT_FOUND
 *   <li>이 계정이 그 프로필 이름으로 할 수 없음 403 — 자기 프로필이거나, 부모가 계정 없는 아이 이름으로만 된다
 *   <li>참여자 아님 403 NOT_A_PARTICIPANT
 *   <li>보호자 동의가 없거나 거둠 422 CONSENT_REQUIRED(결정 4 — 아이에 대한 새 기록을 쌓지 않는다)
 * </ol>
 * 같은 사람이 다시 보내면 느낌 · 시각을 덮어쓴다.
 *
 * <p>미션 행은 지우기와 같은 방식(SELECT … FOR UPDATE)으로 잠그고 읽는다. 느낌 행의 외래 키는 미션이 아니라 참여자 행을 가리켜,
 * 잠그지 않으면 지우기가 느낌을 지운 뒤 · 참여자를 지우기 전에 느낌이 들어가 커밋될 수 있다. 그러면 지우기가 참여자 행에서 외래 키에
 * 걸려 500 이 된다. 잠그면 둘은 차례로 돈다 — 지우기가 먼저면 느낌은 기다렸다가 404, 느낌이 먼저면 지우기가 그 느낌까지 지운다.
 */
@Service
public class MissionFeedbackService {
    private final MissionRepository missions;
    private final MissionFeedbackRepository feedbacks;
    private final FamilyAccess familyAccess;
    private final AppTime time;

    public MissionFeedbackService(
            MissionRepository missions, MissionFeedbackRepository feedbacks, FamilyAccess familyAccess, AppTime time) {
        this.missions = missions;
        this.feedbacks = feedbacks;
        this.familyAccess = familyAccess;
        this.time = time;
    }

    @Transactional
    public void send(UUID userId, UUID missionId, MissionFeedbackCommand command) {
        Mission mission = missions.findByIdForUpdate(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        ProfileSummary actor = familyAccess.requireActingAs(userId, command.profileId());
        mission.participantOf(actor.profileId());
        ParticipantConsent.require(actor);
        feedbacks.upsert(new MissionFeedback(missionId, actor.profileId(), command.feel(), time.now()));
    }
}
