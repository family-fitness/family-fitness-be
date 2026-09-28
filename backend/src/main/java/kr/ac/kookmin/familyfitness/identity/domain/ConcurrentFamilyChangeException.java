package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;
import org.jspecify.annotations.Nullable;

/**
 * 가족을 읽은 뒤 저장하기 전에 다른 요청이 같은 프로필 행을 먼저 바꿔 커밋했다(낙관적 잠금 충돌). 옛 상태로 덮어쓰지 않고 409 로 끝낸다.
 * 코드는 표준 상태 이름 CONFLICT 를 그대로 쓴다. 다시 불러온 뒤 같은 요청을 보내면 된다.
 */
public class ConcurrentFamilyChangeException extends DomainException {
    public ConcurrentFamilyChangeException(@Nullable Throwable cause) {
        super("CONFLICT", ErrorKind.CONFLICT, "다른 요청이 같은 가족 정보를 먼저 바꿨습니다. 다시 불러온 뒤 보내 주세요", cause);
    }
}
