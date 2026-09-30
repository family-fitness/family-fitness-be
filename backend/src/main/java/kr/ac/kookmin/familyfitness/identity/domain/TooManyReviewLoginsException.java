package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 같은 IP 에서 심사용 계정을 한 시간에 너무 많이 만들었다. 계정 · 가족 행이 끝없이 쌓이지 않게 막는다. */
public class TooManyReviewLoginsException extends DomainException {
    public TooManyReviewLoginsException() {
        super("TOO_MANY", ErrorKind.TOO_MANY, "심사용 계정을 너무 자주 만들었습니다. 잠시 뒤에 다시 해 주세요");
    }
}
