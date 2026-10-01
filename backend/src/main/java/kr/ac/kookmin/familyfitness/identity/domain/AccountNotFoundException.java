package kr.ac.kookmin.familyfitness.identity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 토큰의 계정이 없다(탈퇴한 계정). 액세스 토큰은 상태 없는 JWT 라 탈퇴 뒤에도 만료까지 서명 검사를 지나므로, 계정을 찾는 곳에서
 * 401 로 돌린다. 코드는 토큰이 없거나 만료됐을 때와 같은 UNAUTHORIZED 다. 화면은 같은 길(리프레시, 다시 로그인)로 간다.
 */
public class AccountNotFoundException extends DomainException {
    public AccountNotFoundException() {
        super("UNAUTHORIZED", ErrorKind.UNAUTHORIZED, "계정이 없습니다. 다시 로그인해 주세요");
    }
}
