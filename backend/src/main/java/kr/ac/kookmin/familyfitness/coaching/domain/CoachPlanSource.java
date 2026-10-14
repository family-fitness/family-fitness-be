package kr.ac.kookmin.familyfitness.coaching.domain;

/**
 * 제안을 누가 짰는가(V173 coach_runs.plan_source). 화면에는 나가지 않고 사용 기록 분석에만 쓴다.
 *
 * <ul>
 *   <li>{@link #AI} — AI 서비스가 짠 제안. AI 안에서 LLM 대신 규칙 편성이 돌았어도 여기다(BE 는 그 차이를 받지 않는다).
 *   <li>{@link #FALLBACK} — AI 로 짜지 못해 BE 가 영상 라벨로 짠 대체 편성({@code LabelBasedProposalPlanner}). 까닭은
 *       coach_runs.fallback_reason 에 남긴다.
 * </ul>
 *
 * FAILED 로 끝난 실행과 V173 앞에 끝난 실행은 null 이다.
 */
public enum CoachPlanSource {
    AI,
    FALLBACK
}
