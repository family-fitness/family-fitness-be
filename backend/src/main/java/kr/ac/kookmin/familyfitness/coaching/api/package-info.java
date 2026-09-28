/**
 * coaching 이 밖으로 알리는 도메인 이벤트. 다른 모듈(알림 · 캘린더)은 이 패키지만 참조한다.
 * 이벤트는 칸 끝과 같은 트랜잭션 안에서 발행한다 — 듣는 쪽은 커밋 뒤에 받으려면 {@code @TransactionalEventListener} 를 쓴다.
 */
@org.springframework.modulith.NamedInterface("api")
@org.jspecify.annotations.NullMarked
package kr.ac.kookmin.familyfitness.coaching.api;
