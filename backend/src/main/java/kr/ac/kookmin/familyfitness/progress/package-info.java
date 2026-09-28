/**
 * progress — 레벨 · 경험치 원장 · 업적 · 이어서 한 날. identity · activity · fitness 의 공개 API 만 참조한다.
 * coaching 은 {@code progress::api} 로 칸 끝 적립을 부르고 잡힌 날(PlannedDays)을 구현한다. progress 는 coaching 을 참조하지 않는다.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "progress",
        allowedDependencies = {"identity::api", "activity::api", "fitness::api"})
@org.jspecify.annotations.NullMarked
package kr.ac.kookmin.familyfitness.progress;
