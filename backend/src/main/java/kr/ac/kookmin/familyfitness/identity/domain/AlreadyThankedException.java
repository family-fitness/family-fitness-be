package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 이 스티커에는 벌써 고마워요를 보냈다(스티커 하나에 한 번, FE 규칙 12). */
public class AlreadyThankedException extends DomainException {
    public AlreadyThankedException() {
        super("ALREADY_THANKED", ErrorKind.CONFLICT, "이 스티커에는 벌써 고마워요를 보냈습니다");
    }
}
