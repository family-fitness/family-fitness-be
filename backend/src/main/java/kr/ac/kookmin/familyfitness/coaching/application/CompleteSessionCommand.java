package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.Instant;
import java.util.UUID;

/**
 * 운동 한 칸 끝 요청.
 *
 * @param profileId 칸을 끝낸 사람. 부모 계정이 계정 없는 아이 이름으로 보낼 수 있다
 * @param activeSeconds 영상 재생 시간(초, 결정 3-1). {@code endedAt − startedAt} 을 넘으면 그 값으로 자른다
 * @param startedAt 기기 시각. 재생 시간을 자르는 데만 쓰고 끝낸 날은 서버가 받은 날로 정한다
 */
public record CompleteSessionCommand(UUID profileId, int activeSeconds, Instant startedAt, Instant endedAt) {}
