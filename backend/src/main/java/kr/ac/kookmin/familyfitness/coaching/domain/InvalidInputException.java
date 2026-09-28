package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/**
 * 요청 값끼리 맞지 않거나 받을 수 있는 범위를 벗어났다 — 400 BAD_REQUEST(예: endedAt ≤ startedAt, 날짜 칸을 두 가지로 같이 보냄,
 * 칸 분 합과 다른 목표 분). 호출자가 고칠 값이라 스택을 남기지 않는다. {@link IllegalArgumentException} 은 서버 변환 버그(AI 제안 →
 * 미션 등)를 잡으려고 스택과 함께 WARN 을 남기므로 사용자 입력 오류에는 쓰지 않는다.
 */
public class InvalidInputException extends DomainException {
    public InvalidInputException(String message) {
        super("BAD_REQUEST", ErrorKind.BAD_REQUEST, message);
    }
}
