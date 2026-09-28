package kr.ac.kookmin.familyfitness.activity.application.port;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.domain.RestCard;

public interface RestCardRepository {
    /** 그달 쓴 카드 */
    List<RestCard> findByMonth(UUID familyId, YearMonth month);

    /**
     * 곧바로 DB 에 넣는다. 같은 날이나 같은 카드 번호가 이미 있으면(다른 요청이 먼저 넣었다) false.
     * false 뒤 트랜잭션은 롤백 전용이 되므로 호출자는 그 트랜잭션을 끝내고 새로 읽어야 한다.
     */
    boolean insert(RestCard card);

    /** 그날 카드를 지운다. 지웠으면 true. */
    boolean delete(UUID familyId, LocalDate restDate);

    /** {@code from}~{@code to}(양끝 포함) 안의 쉬는 날, 오름차순 */
    List<LocalDate> restDatesBetween(UUID familyId, LocalDate from, LocalDate to);
}
