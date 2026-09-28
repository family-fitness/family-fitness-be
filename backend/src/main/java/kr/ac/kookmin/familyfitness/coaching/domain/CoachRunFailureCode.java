package kr.ac.kookmin.familyfitness.coaching.domain;

/**
 * FAILED 로 끝난 편성의 까닭. 화면이 이 코드로 안내 문구와 다음 할 일을 가른다.
 * 개발자용 상세(failure_reason 원문)는 응답에 싣지 않는다.
 */
public enum CoachRunFailureCode {
    /** AI 가 인용할 근거를 찾지 못해 편성을 거부했다(refused — no_relevant_source · no_citation_generated). */
    NO_CITATIONS,

    /** AI 가 짜지 못했고(연결 실패 · failed · 폴링 만료 · 실행 없음) 대체 편성할 근거(측정 · 고른 힘)도 없었다. */
    AI_FAILED,

    /** 편성 대상의 보호자 동의가 요청 뒤에 거둬졌다. */
    CONSENT_REQUIRED,

    /** 편성 스레드와 대기열이 가득 차(또는 서버가 내려가는 중이라) 시작하지 못했다. */
    BUSY,

    /** 서버가 끝내지 못한 실행(재시작 · 강제 종료 · 한도보다 오래 대기)을 정리 작업이 끝냈다. */
    STALE,

    /** 그 밖의 오류(AI 400 · 응답 변환 실패 등 서버 쪽 결함). */
    ERROR
}
