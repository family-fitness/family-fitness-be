package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 지난 날짜(오늘 KST 보다 앞)로는 미션을 만들지 않는다(결정 40 · 46). 코드는 편성 · 쉬는 날과 같은 INVALID_DATE 다(FE 가 이미 아는 코드).
 * 지난 날에 운동을 뒤늦게 넣어 리그 달성률 · 경험치를 채우는 길을 막는다(Q-contract-16 · Q-missing-11).
 */
public class InvalidMissionDateException extends DomainException {
    public InvalidMissionDateException(LocalDate date, LocalDate today) {
        super("INVALID_DATE", ErrorKind.RULE_VIOLATION, "지난 날짜로는 운동을 만들 수 없습니다: date=" + date + ", today=" + today);
    }
}
