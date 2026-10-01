package kr.ac.kookmin.familyfitness.league.application;

import kr.ac.kookmin.familyfitness.identity.api.FamilyDeleting;
import kr.ac.kookmin.familyfitness.league.application.port.LeagueErasureRepository;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 가족이 지워질 때 리그 참가 기록을 지운다. identity 가 지우는 트랜잭션 안에서 동기로 듣는다. 리그는 가족 단위라 구성원 한 사람이
 * 빠지거나({@code ProfileDeleting}) 아이의 동의를 거둘 때({@code ProfileRecordsDeleting})는 할 일이 없다. 이번 달 달성률은 저장하지
 * 않고 조회 때 남은 식구와 남은 기록으로 다시 센다. 정산한 달은 정산 때 굳힌 값을 그대로 둔다.
 */
@Component
public class LeagueErasure {
    private final LeagueErasureRepository rows;

    public LeagueErasure(LeagueErasureRepository rows) {
        this.rows = rows;
    }

    @EventListener
    @Order(50)
    public void on(FamilyDeleting deleting) {
        rows.eraseFamily(deleting.familyId());
    }
}
