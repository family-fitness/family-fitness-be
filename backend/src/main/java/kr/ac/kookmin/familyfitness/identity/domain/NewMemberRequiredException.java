package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 가족 초대코드로 들어오면서 이름, 생년월일, 성별 가운데 하나라도 보내지 않았다. 400 BAD_REQUEST 이고 코드는 그대로 남는다. */
public class NewMemberRequiredException extends DomainException {
    public NewMemberRequiredException() {
        super("BAD_REQUEST", ErrorKind.BAD_REQUEST, "가족 초대코드로 들어올 때는 name, birthDate, sex 를 함께 보내야 합니다");
    }
}
