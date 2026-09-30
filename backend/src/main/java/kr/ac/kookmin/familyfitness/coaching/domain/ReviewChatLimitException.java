package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 심사용 계정의 코치 대화가 오늘 한도에 닿았다. 대화마다 AI 가 LLM 을 부를 수 있어, 누구나 만들 수 있는 계정이 LLM 을 끝없이 부르지 못하게
 * 막는다.
 */
public class ReviewChatLimitException extends DomainException {
    private ReviewChatLimitException(String message) {
        super("TOO_MANY", ErrorKind.TOO_MANY, message);
    }

    /** 이 계정이 오늘 한도만큼 물었다. */
    public static ReviewChatLimitException perAccount(int limit) {
        return new ReviewChatLimitException("심사용 계정은 하루에 코치에게 " + limit + "번까지 물을 수 있습니다. 내일 다시 해 주세요");
    }

    /** 심사용 계정을 모두 합친 오늘 몫이 끝났다. */
    public static ReviewChatLimitException ofAll() {
        return new ReviewChatLimitException("오늘 심사용 계정의 코치 대화 몫이 끝났습니다. 내일 다시 해 주세요");
    }
}
