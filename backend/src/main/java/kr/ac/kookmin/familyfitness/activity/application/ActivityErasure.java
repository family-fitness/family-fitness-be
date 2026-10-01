package kr.ac.kookmin.familyfitness.activity.application;

import kr.ac.kookmin.familyfitness.activity.application.port.ActivityErasureRepository;
import kr.ac.kookmin.familyfitness.identity.api.FamilyDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDeleting;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴와 구성원 내보내기 때 activity 의 행을 지운다. identity 가 지우는 트랜잭션 안에서 동기로 듣는다(차례는
 * {@link ProfileDeleting} 설명). 여기서 실패하면 탈퇴 전체가 되돌아간다.
 */
@Component
public class ActivityErasure {
    private final ActivityErasureRepository rows;

    public ActivityErasure(ActivityErasureRepository rows) {
        this.rows = rows;
    }

    @EventListener
    @Order(10)
    public void on(ProfileDeleting deleting) {
        rows.eraseProfile(deleting.profileId(), deleting.ownerProfileId());
    }

    @EventListener
    @Order(10)
    public void on(FamilyDeleting deleting) {
        rows.eraseFamily(deleting.familyId(), deleting.profileIds());
    }
}
