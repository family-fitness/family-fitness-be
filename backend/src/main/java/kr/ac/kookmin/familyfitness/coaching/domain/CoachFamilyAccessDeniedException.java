package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class CoachFamilyAccessDeniedException extends DomainException {
    public CoachFamilyAccessDeniedException() {
        this("다른 가족의 코치 실행입니다");
    }

    public CoachFamilyAccessDeniedException(String message) {
        super("NOT_SAME_FAMILY", ErrorKind.FORBIDDEN, message);
    }
}
