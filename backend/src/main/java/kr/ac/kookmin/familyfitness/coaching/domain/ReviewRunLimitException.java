package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 심사용 계정이 오늘 편성을 한도만큼 이미 돌렸다. 누구나 만들 수 있는 계정이 AI(LLM) 편성을 끝없이 돌리지 못하게 막는다. */
public class ReviewRunLimitException extends DomainException {
    public ReviewRunLimitException(int limit) {
        super("TOO_MANY", ErrorKind.TOO_MANY, "심사용 계정은 하루에 편성을 " + limit + "번까지 할 수 있습니다. 내일 다시 해 주세요");
    }
}
