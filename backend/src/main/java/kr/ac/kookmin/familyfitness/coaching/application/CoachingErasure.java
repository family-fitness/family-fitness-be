package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.api.MissionsErased;
import kr.ac.kookmin.familyfitness.coaching.application.port.CoachingErasureRepository;
import kr.ac.kookmin.familyfitness.identity.api.FamilyDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileRecordsDeleting;
import kr.ac.kookmin.familyfitness.progress.api.DeletedMissions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴, 구성원 내보내기, 동의 철회 때 미션, 편성, 대화, 찜을 정리한다. identity 가 지우는 트랜잭션 안에서 동기로 듣는다(차례는
 * {@link ProfileDeleting} 설명). 한 사람이 빠질 때의 차례(미션 지우기 {@link MissionDeletionService} 와 같이 자식 행부터):
 * <ol>
 *   <li>참여자가 그 사람뿐인 미션은 통째로 지운다(느낌, 칸 끝, 참여자, 칸, 미션)
 *   <li>여럿이 하는 미션에서는 그 사람 몫만 지운다. 그 사람이 만든 미션과 확인한 참여자 행은 오너가 한 것으로 돌린다
 *   <li>그 사람을 대상으로 짠 편성은 지운다. 그 편성으로 만든 미션에 남는 사람이 있으면 미션은 남기고 편성 연결만 끊는다
 *   <li>남는 편성에서 그 사람을 요청한 사람, 승인한 사람, 제안 참여자 목록에서 지운다
 *   <li>그 사람의 코치 대화와 인용, 영상 기록, 구간 찜을 지운다
 * </ol>
 * 통째로 지운 미션은 경험치 원장({@link DeletedMissions})과 알림({@link MissionsErased})에 알린다. 응원의 missionId 는 identity 가
 * 마지막에 미션이 남아 있는지 물어 비운다.
 */
@Component
public class CoachingErasure {
    private final CoachingErasureRepository rows;
    private final DeletedMissions deletedMissions;
    private final ApplicationEventPublisher events;

    public CoachingErasure(
            CoachingErasureRepository rows, DeletedMissions deletedMissions, ApplicationEventPublisher events) {
        this.rows = rows;
        this.deletedMissions = deletedMissions;
        this.events = events;
    }

    @EventListener
    @Order(30)
    public void on(ProfileDeleting deleting) {
        UUID profileId = deleting.profileId();
        UUID heir = deleting.ownerProfileId();
        List<UUID> solo = rows.soloMissionsOf(deleting.familyId(), profileId);
        rows.deleteMissions(solo);
        rows.leaveMissions(profileId, heir);
        rows.deleteRunsAbout(profileId);
        rows.forgetInRuns(deleting.familyId(), profileId, heir);
        rows.erasePersonal(List.of(profileId));
        if (solo.isEmpty()) return;
        deletedMissions.forget(solo);
        events.publishEvent(new MissionsErased(deleting.familyId(), solo));
    }

    /**
     * 동의 철회. 프로필은 남는다. 위와 같은 차례로 그 사람만 참여한 미션은 통째로, 여럿이 하는 미션은 그 사람 몫만 지우고, 그 사람을
     * 대상으로 짠 편성과 제안을 지운다. 그 사람의 코치 대화와 인용, 영상 기록, 구간 찜도 지운다. 아이 프로필만 오므로 미션을 만든
     * 사람, 참여자를 확인한 사람, 편성을 요청하거나 승인한 사람처럼 보호자만 하는 일을 적은 칸은 돌릴 것이 없다.
     */
    @EventListener
    @Order(30)
    public void on(ProfileRecordsDeleting deleting) {
        UUID profileId = deleting.profileId();
        List<UUID> solo = rows.soloMissionsOf(deleting.familyId(), profileId);
        rows.deleteMissions(solo);
        rows.dropParticipation(profileId);
        rows.deleteRunsAbout(profileId);
        rows.dropFromProposals(deleting.familyId(), profileId);
        rows.erasePersonal(List.of(profileId));
        if (solo.isEmpty()) return;
        deletedMissions.forget(solo);
        events.publishEvent(new MissionsErased(deleting.familyId(), solo));
    }

    /** 가족의 미션과 편성을 모두 지운다. 경험치와 알림은 그 모듈이 같은 이벤트를 듣고 프로필로 지운다. */
    @EventListener
    @Order(30)
    public void on(FamilyDeleting deleting) {
        rows.eraseFamily(deleting.familyId());
        rows.erasePersonal(deleting.profileIds());
    }
}
