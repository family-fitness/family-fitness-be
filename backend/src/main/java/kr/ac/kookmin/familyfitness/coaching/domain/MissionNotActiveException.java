package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 오늘(KST)이 미션 기간 밖이라 칸 끝을 받지 않는다(결정 22). FE 목에는 이 경우의 코드가 없어 새로 둔다(결정 36).
 * 지난 날을 나중에 채우거나 앞날 운동을 미리 끝내 연속 · 리그가 거슬러 바뀌는 것을 막는다.
 */
public class MissionNotActiveException extends DomainException {
    public MissionNotActiveException(LocalDate startsOn, LocalDate endsOn, LocalDate today) {
        super(
                "MISSION_NOT_ACTIVE",
                ErrorKind.RULE_VIOLATION,
                "운동 기간(" + startsOn + " ~ " + endsOn + ")이 아닙니다: today=" + today);
    }
}
