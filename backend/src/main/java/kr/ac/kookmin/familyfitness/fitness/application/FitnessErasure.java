package kr.ac.kookmin.familyfitness.fitness.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessErasureRepository;
import kr.ac.kookmin.familyfitness.identity.api.FamilyDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDeleting;
import kr.ac.kookmin.familyfitness.identity.api.ProfileRecordsDeleting;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 탈퇴, 구성원 내보내기, 동의 철회 때 그 사람의 측정을 지운다. identity 가 지우는 트랜잭션 안에서 동기로 듣는다(차례는
 * {@link ProfileDeleting} 설명). 측정에는 다른 사람을 가리키는 칸이 없어 남는 가족의 측정은 건드리지 않는다.
 */
@Component
public class FitnessErasure {
    private final FitnessErasureRepository rows;

    public FitnessErasure(FitnessErasureRepository rows) {
        this.rows = rows;
    }

    @EventListener
    @Order(20)
    public void on(ProfileDeleting deleting) {
        rows.eraseProfiles(List.of(deleting.profileId()));
    }

    /** 동의 철회. 그 사람의 측정 회차와 항목(키, 몸무게, 체지방률, 허리둘레, 종목 값)을 지운다. */
    @EventListener
    @Order(20)
    public void on(ProfileRecordsDeleting deleting) {
        rows.eraseProfiles(List.of(deleting.profileId()));
    }

    @EventListener
    @Order(20)
    public void on(FamilyDeleting deleting) {
        rows.eraseProfiles(deleting.profileIds());
    }
}
