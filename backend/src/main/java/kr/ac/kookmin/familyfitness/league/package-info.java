/**
 * league — 가족 리그. 한 달이 한 판이고 다섯 티어(브론즈 · 실버 · 골드 · 플래티넘 · 다이아)로 겨룬다(결정 41).
 * 겨루는 값은 아이들의 목표 달성률(잡힌 날 중 해낸 날)이다. 가족 이름 · 식구 · 쉬는 날은 identity · activity 의 공개 API 로,
 * 잡힌 날은 progress 의 공개 API(PlannedDaysSinceCreated — coaching 이 구현한다)로 읽는다. 다른 모듈은 league 를 참조하지 않는다.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "league",
        allowedDependencies = {"identity::api", "activity::api", "progress::api"})
@org.jspecify.annotations.NullMarked
package kr.ac.kookmin.familyfitness.league;
