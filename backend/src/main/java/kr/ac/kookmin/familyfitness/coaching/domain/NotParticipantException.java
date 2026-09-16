package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class NotParticipantException extends DomainException {
    public NotParticipantException(UUID missionId, UUID profileId) {
        super(
                "NOT_PARTICIPANT",
                ErrorKind.RULE_VIOLATION,
                "미션 참여자가 아닙니다: mission=" + missionId + " profile=" + profileId);
    }
}
