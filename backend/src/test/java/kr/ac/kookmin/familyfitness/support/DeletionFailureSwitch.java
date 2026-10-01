package kr.ac.kookmin.familyfitness.support;

import java.util.concurrent.atomic.AtomicBoolean;
import kr.ac.kookmin.familyfitness.identity.api.FamilyDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDeleting;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴와 내보내기를 중간에 실패시키는 시험용 스위치. 켜 두면 모든 모듈이 자기 행을 지운 뒤(가장 늦은 차례)에 예외를 던진다. 그때까지
 * 지운 행이 모두 되돌아가는지 본다. 꺼져 있으면 아무것도 하지 않는다.
 */
@Component
public class DeletionFailureSwitch {
    private final AtomicBoolean armed = new AtomicBoolean();

    public void failNext() {
        armed.set(true);
    }

    public void reset() {
        armed.set(false);
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void on(ProfileDeleting deleting) {
        failIfArmed();
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void on(FamilyDeleting deleting) {
        failIfArmed();
    }

    private void failIfArmed() {
        if (armed.getAndSet(false)) throw new IllegalStateException("시험이 일부러 낸 실패");
    }
}
