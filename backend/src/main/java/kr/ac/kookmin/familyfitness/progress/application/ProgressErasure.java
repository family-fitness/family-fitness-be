package kr.ac.kookmin.familyfitness.progress.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.FamilyDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDeleting;
import kr.ac.kookmin.familyfitness.progress.api.DeletedMissions;
import kr.ac.kookmin.familyfitness.progress.application.port.ProgressErasureRepository;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴와 구성원 내보내기 때 경험치와 업적을 정리한다. identity 가 지우는 트랜잭션 안에서 동기로 듣는다(차례는
 * {@link ProfileDeleting} 설명). 지우는 사람의 줄과 업적만 지운다. 남는 사람의 줄은 지우지 않고, 지우는 사람을 보낸 사람으로
 * 적은 칸(from_profile_id)과 지운 미션을 가리키는 칸(mission_id)만 비운다. 그래서 남는 사람의 경험치 합, 레벨, 연속 기록, 업적은
 * 그대로다.
 */
@Component
public class ProgressErasure implements DeletedMissions {
    private final ProgressErasureRepository rows;

    public ProgressErasure(ProgressErasureRepository rows) {
        this.rows = rows;
    }

    @EventListener
    @Order(40)
    public void on(ProfileDeleting deleting) {
        rows.eraseProfiles(List.of(deleting.profileId()));
    }

    @EventListener
    @Order(40)
    public void on(FamilyDeleting deleting) {
        rows.eraseProfiles(deleting.profileIds());
    }

    @Override
    public void forget(Collection<UUID> missionIds) {
        rows.forgetMissions(missionIds);
    }
}
