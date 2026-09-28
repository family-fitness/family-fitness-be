package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.MissionCancelled;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionFeedbackRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.MissionRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.SessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.Mission;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionCompletions;
import kr.ac.kookmin.familyfitness.coaching.domain.MissionNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미션 지우기(DELETE /missions/{missionId}, 결정 40 · 46). 판단 차례:
 * <ol>
 *   <li>미션 행을 SELECT … FOR UPDATE 로 잠그고 읽는다. 없거나 기다리는 사이 지워졌으면 404 MISSION_NOT_FOUND
 *   <li>그 가족 보호자가 아님 403(다른 가족 NOT_SAME_FAMILY · 자녀 계정 NOT_A_PARENT)
 *   <li>기간이 끝남(endDate &lt; 오늘 KST) 409 MISSION_ENDED
 *   <li>누군가 칸을 끝냈거나 참여자가 완료됨 409 MISSION_ALREADY_STARTED
 * </ol>
 * 잠금을 맨 앞에 두는 까닭: 잠그기 전에 읽은 참여자 · 칸 끝 상태는 영속성 컨텍스트에 남아 잠근 뒤 다시 읽어도 바뀌지 않는다.
 *
 * <p>같은 미션에 다른 요청이 겹칠 때(PostgreSQL READ COMMITTED):
 * <ul>
 *   <li>칸 끝이 먼저 칸 끝 행을 넣었으면: 그 INSERT 의 외래 키 잠금(FOR KEY SHARE)이 풀릴 때까지 여기 잠금이 기다렸다가, 커밋된
 *       칸 끝 행을 보고 409 MISSION_ALREADY_STARTED.
 *   <li>여기가 먼저 잠갔으면: 칸 끝의 INSERT 가 기다렸다가 지워진 미션 행을 보고 외래 키에 걸린다 — 칸 끝은 404
 *       ({@code SessionCompletionPersistenceAdapter}).
 *   <li>느낌은 같은 행을 같은 방식으로 잠그므로 차례로 돈다. 지운 뒤에 온 느낌은 404.
 * </ul>
 * 지우는 것: 느낌 → 참여자 → 칸 → 미션 행. 칸 끝 · 활동 · 경험치 기록은 위 검사로 없는 것이 확인된 상태다.
 * 응원(cheers.mission_id) · 경험치 원장(progress_xp_events.mission_id)은 외래 키가 없어 그대로 둔다 — 다른 모듈의 표이고, 응원은 받은
 * 사람의 기록이라 지우지 않는다. 지운 뒤 {@link MissionCancelled} 를 낸다(알림이 그 미션의 MISSION_READY 를 지운다).
 */
@Service
public class MissionDeletionService {
    private final MissionRepository missions;
    private final SessionCompletionRepository completions;
    private final MissionFeedbackRepository feedbacks;
    private final FamilyAccess familyAccess;
    private final ApplicationEventPublisher events;
    private final AppTime time;

    public MissionDeletionService(
            MissionRepository missions,
            SessionCompletionRepository completions,
            MissionFeedbackRepository feedbacks,
            FamilyAccess familyAccess,
            ApplicationEventPublisher events,
            AppTime time) {
        this.missions = missions;
        this.completions = completions;
        this.feedbacks = feedbacks;
        this.familyAccess = familyAccess;
        this.events = events;
        this.time = time;
    }

    @Transactional
    public void delete(UUID userId, UUID missionId) {
        Mission mission = missions.findByIdForUpdate(missionId);
        if (mission == null) throw new MissionNotFoundException(missionId);
        familyAccess.requireParent(userId, mission.getFamilyId());

        LocalDate today = time.today();
        mission.requireCancellableOn(today, MissionCompletions.of(completions.findByMission(missionId)));

        feedbacks.deleteByMission(missionId);
        missions.delete(missionId);
        events.publishEvent(new MissionCancelled(missionId, mission.getFamilyId(), time.now()));
    }
}
