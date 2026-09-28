package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 미션 참여자가 아닌 사람의 기록 · 확인 요청. FE 요청서 3장대로 권한 문제(403 NOT_A_PARTICIPANT)로 본다. */
public class NotParticipantException extends DomainException {
    public NotParticipantException(UUID missionId, UUID profileId) {
        super("NOT_A_PARTICIPANT", ErrorKind.FORBIDDEN, "미션 참여자가 아닙니다: mission=" + missionId + " profile=" + profileId);
    }
}
