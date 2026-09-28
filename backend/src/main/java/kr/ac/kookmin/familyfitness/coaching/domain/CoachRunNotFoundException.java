package kr.ac.kookmin.familyfitness.coaching.domain;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.jspecify.annotations.Nullable;

public class CoachRunNotFoundException extends DomainException {
    public CoachRunNotFoundException(UUID runId) {
        this("코치 실행이 없습니다: " + runId);
    }

    private CoachRunNotFoundException(String message) {
        super("COACH_RUN_NOT_FOUND", ErrorKind.NOT_FOUND, message);
    }

    /** 가족(또는 그 가족의 한 프로필)에 코치 실행이 아직 없다. */
    public static CoachRunNotFoundException latestOf(UUID familyId, @Nullable UUID profileId) {
        return new CoachRunNotFoundException(
                "코치 실행이 없습니다: family=" + familyId + (profileId == null ? "" : ", profile=" + profileId));
    }
}
