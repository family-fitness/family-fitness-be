/**
 * notification — 알림함. 응원 · 미션 · 업적 · 측정에서 알림을 만들어 받는 사람마다 한 건씩 쌓고, 알림함 목록 · 읽음을 준다(결정 45).
 * 다른 모듈의 일은 그 모듈이 커밋한 뒤 이벤트로 받는다(identity CheerSent · coaching MissionCreated · MissionCompleted ·
 * MissionCancelled · progress AchievementEarned). 오늘 서는 미션 · 마지막 측정일 · 쉬는 날 · 식구는 각 모듈의 공개 API 로 읽는다.
 * 어느 모듈도 notification 을 참조하지 않는다(결정 49 — 순환 금지).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "notification",
        allowedDependencies = {"identity::api", "coaching::api", "progress::api", "fitness::api", "activity::api"})
@org.jspecify.annotations.NullMarked
package kr.ac.kookmin.familyfitness.notification;
