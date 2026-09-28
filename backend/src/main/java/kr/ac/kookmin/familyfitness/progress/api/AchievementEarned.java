package kr.ac.kookmin.familyfitness.progress.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 한 사람이 업적을 처음 받았다. 이미 받은 업적을 다시 판정할 때는 내지 않는다. 업적을 저장한 트랜잭션 안에서 발행한다
 * (칸 끝 · 응원 · 측정 트랜잭션). 부모 프로필이 받아도 낸다 — 누구에게 알릴지는 듣는 쪽이 정한다.
 *
 * @param code 업적 코드(FIRST_STEP 등)
 * @param title 업적 이름. 서버가 정한 문구다
 * @param description 업적 설명 — 받는 조건이라 「~해요」 로 끝난다
 * @param earnedAt 처음 받은 시각
 */
public record AchievementEarned(UUID profileId, String code, String title, String description, Instant earnedAt) {}
