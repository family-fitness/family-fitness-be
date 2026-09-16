package kr.ac.kookmin.familyfitness.shared.ai;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** AI 가 400 을 돌려준 것은 호출자(이쪽) 버그다. 화면에 띄우지 않고 로그로 남긴다. */
public class AiBadRequestException extends DomainException {
    public AiBadRequestException(String message) {
        super("AI_BAD_REQUEST", ErrorKind.UNAVAILABLE, message);
    }
}
