package kr.ac.kookmin.familyfitness.identity.api;

/**
 * 응원의 종류. 누가 누구에게 보내는지가 정해져 있다(FE 규칙 12 · FE 요청서 2장).
 * DONE 은 아이가 「다 했어요」 를 부모에게, PRAISE 는 부모가 아이에게 붙이는 칭찬(스티커),
 * THANKS 는 아이가 받은 스티커에 돌려보내는 「고마워요」 다.
 */
public enum CheerKind {
    DONE,
    PRAISE,
    THANKS
}
