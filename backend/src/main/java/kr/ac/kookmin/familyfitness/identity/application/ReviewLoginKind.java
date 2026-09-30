package kr.ac.kookmin.familyfitness.identity.application;

/**
 * 심사용 계정 로그인의 kind 세 가지. 로컬 개발용 로그인 상자(fe:src/app/login/page.tsx)의 세 계정과 같은 흐름을 심사위원도 직접 해 볼 수 있게 한다. 셋 모두
 * 심사용 계정(provider REVIEW)이라 IP 한도 · 편성과 대화 하루 한도 · 끝나는 날 · 체험 리그 방이 똑같이 걸린다.
 */
public enum ReviewLoginKind {
    /** 꾸며 둔 체험 가족의 보호자(엄마)로 들어간다. nextStep HOME. 개발용 「은영 · 가족 3명」 과 같다. 본문이 없으면 이것이다. */
    FAMILY,
    /** 가족이 없는 새 계정. nextStep CREATE_FAMILY — 가족 만들기부터 평소 가입 흐름을 그대로 거친다. 개발용 「새 계정 · 가족 없음」 과 같다. */
    FRESH,
    /**
     * 가족이 없는 새 계정과, 심사위원이 아닌 가짜 보호자가 꾸민 체험 가족. 그 가족의 아빠 자리 초대코드를 응답에 싣고 nextStep 은 CLAIM 이다.
     * 개발용 「초대받은 계정」(프로필 없는 계정 + 초대코드)과 같다.
     */
    INVITED
}
