package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** THANKS 의 replyToCheerId 가 내가 받은 스티커가 아니거나, 스티커를 보낸 사람에게 돌아가지 않는다. */
public class NotAReplyTargetException extends DomainException {
    public NotAReplyTargetException() {
        super("NOT_A_REPLY_TARGET", ErrorKind.RULE_VIOLATION, "내가 받은 칭찬 스티커에만, 보낸 사람에게 고마워요를 보낼 수 있습니다");
    }
}
