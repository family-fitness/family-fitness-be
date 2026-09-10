/**
 * coaching — 영상 · 코치 실행(승인 게이트) · 미션 · 대화 · 주간 요약.
 * 미션 완료 판정이 활동(activity)과 측정(fitness)에 걸치므로 세 모듈의 공개 API 를 참조한다.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "coaching",
        allowedDependencies = {"identity::api", "fitness::api", "activity::api"})
package kr.ac.kookmin.familyfitness.coaching;
