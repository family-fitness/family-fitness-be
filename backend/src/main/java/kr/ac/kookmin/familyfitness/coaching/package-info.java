/**
 * coaching — 영상 · 코치 실행(승인 게이트) · 미션 · 대화 · 주간 요약.
 * 미션 완료 판정이 활동(activity)과 측정(fitness)에 걸치므로 세 모듈의 공개 API 를 참조한다.
 * 칸 끝 적립은 progress 의 공개 API 로 부르고, progress 가 쓰는 잡힌 날(PlannedDays)을 구현한다.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "coaching",
        allowedDependencies = {"identity::api", "fitness::api", "activity::api", "progress::api"})
@org.jspecify.annotations.NullMarked
package kr.ac.kookmin.familyfitness.coaching;
