package kr.ac.kookmin.familyfitness.activity.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 같은 가족의 다른 요청이 여러 번 연달아 먼저 카드를 넣어 이 요청이 끝내 자리를 못 잡았다.
 * 한 달 두 장이라 뺏긴 카드가 그대로 남아 있으면 세 번째 시도는 ALREADY_REST_DAY · NO_REST_CARD_LEFT 로 끝난다.
 * 그 사이 다른 보호자가 되돌리기까지 해서 빈 번호가 계속 바뀔 때만 나므로 보통은 나지 않는다.
 * 코드는 유니크 위반을 돌리는 공통 처리(ApiErrorHandler)와 같은 CONFLICT 다.
 */
public class RestCardConflictException extends DomainException {
    public RestCardConflictException() {
        super("CONFLICT", ErrorKind.CONFLICT, "다른 요청과 겹쳤습니다. 다시 시도해 주세요");
    }
}
