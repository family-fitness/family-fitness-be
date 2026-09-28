/** activity — 일별 활동 기록(걸음수 · 타이머 · 영상, 시간은 초) · 쉬는 날 카드. identity 의 공개 API 만 참조한다. */
@org.springframework.modulith.ApplicationModule(
        displayName = "activity",
        allowedDependencies = {"identity::api"})
@org.jspecify.annotations.NullMarked
package kr.ac.kookmin.familyfitness.activity;
