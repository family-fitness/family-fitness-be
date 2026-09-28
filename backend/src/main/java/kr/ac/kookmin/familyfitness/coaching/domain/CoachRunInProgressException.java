package kr.ac.kookmin.familyfitness.coaching.domain;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 같은 (대상 프로필, 날짜)의 편성이 아직 RUNNING 이다. */
public class CoachRunInProgressException extends DomainException {
    public CoachRunInProgressException(UUID profileId, LocalDate date) {
        super("RUN_IN_PROGRESS", ErrorKind.CONFLICT, "이 날 편성이 이미 진행 중입니다: profile=" + profileId + ", date=" + date);
    }
}
