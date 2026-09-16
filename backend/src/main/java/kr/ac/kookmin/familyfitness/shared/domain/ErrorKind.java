package kr.ac.kookmin.familyfitness.shared.domain;

/**
 * 도메인 규칙 위반의 분류. HTTP 상태 매핑은 웹 어댑터({@link kr.ac.kookmin.familyfitness.shared.web.ApiErrorHandler})가 한다.
 * 도메인은 HTTP 를 모르고 "어떤 종류의 실패인가"만 말한다.
 */
public enum ErrorKind {
    /** 호출자 버그 — 필수 누락·형식 오류 (400) */
    BAD_REQUEST,

    /** 인증 없음·만료 (401) */
    UNAUTHORIZED,

    /** 권한 없음 (403) */
    FORBIDDEN,

    /** 대상 없음 (404) */
    NOT_FOUND,

    /** 상태 충돌 — 중복·이미 처리됨 (409) */
    CONFLICT,

    /** 만료 (410) */
    GONE,

    /** 도메인 규칙 위반 — 동의 없음·측정 대상 아님 (422) */
    RULE_VIOLATION,

    /** 과다 호출 (429) */
    TOO_MANY,

    /** 외부 의존성(AI 서비스) 일시 장애 (503) */
    UNAVAILABLE
}
