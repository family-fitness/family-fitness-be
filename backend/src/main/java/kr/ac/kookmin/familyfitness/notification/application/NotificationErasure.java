package kr.ac.kookmin.familyfitness.notification.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.coaching.api.MissionsErased;
import kr.ac.kookmin.familyfitness.identity.api.FamilyDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileRecordsDeleting;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationErasureRepository;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴, 구성원 내보내기, 동의 철회 때 알림을 지운다. 다른 일은 커밋 뒤에 받지만({@link NotificationEventListener}) 지우기는 같은 트랜잭션에서
 * 동기로 듣는다. 알림이 프로필과 응원을 외래 키로 가리켜서, 알림을 먼저 지우지 않으면 identity 가 프로필과 응원을 지울 때 실패한다.
 */
@Component
public class NotificationErasure {
    private final NotificationErasureRepository rows;

    public NotificationErasure(NotificationErasureRepository rows) {
        this.rows = rows;
    }

    @EventListener
    @Order(60)
    public void on(ProfileDeleting deleting) {
        rows.eraseProfiles(List.of(deleting.profileId()));
        rows.eraseCheers(deleting.cheerIds());
    }

    /** 동의 철회. 그 사람이 받은 알림, 그 사람에 관한 알림, 그 사람이 보낸 알림, 지울 응원으로 만든 알림을 지운다. */
    @EventListener
    @Order(60)
    public void on(ProfileRecordsDeleting deleting) {
        rows.eraseProfiles(List.of(deleting.profileId()));
        rows.eraseCheers(deleting.cheerIds());
    }

    @EventListener
    @Order(60)
    public void on(FamilyDeleting deleting) {
        rows.eraseProfiles(deleting.profileIds());
    }

    /** coaching 이 지우는 중에 낸다. 남는 식구가 받은 그 미션의 알림(MISSION_READY 등)을 지운다. */
    @EventListener
    public void on(MissionsErased erased) {
        rows.eraseMissions(erased.missionIds());
    }
}
