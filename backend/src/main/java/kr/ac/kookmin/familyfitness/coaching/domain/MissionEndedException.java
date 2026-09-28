package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 기간이 끝난 미션(endDate &lt; 오늘 KST)은 지우지 않는다(결정 40). 지난 미션은 리그 · 달력의 기록이라, 지우면 지난 달성률이 거슬러 바뀐다.
 * FE 목에 코드가 없어 새로 둔다.
 */
public class MissionEndedException extends DomainException {
    public MissionEndedException(UUID missionId, LocalDate endsOn, LocalDate today) {
        super(
                "MISSION_ENDED",
                ErrorKind.CONFLICT,
                "기간이 끝난 운동은 지울 수 없습니다: mission=" + missionId + ", endDate=" + endsOn + ", today=" + today);
    }
}
