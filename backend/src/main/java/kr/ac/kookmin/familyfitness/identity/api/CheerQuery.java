package kr.ac.kookmin.familyfitness.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 다른 모듈이 읽는 응원. cheers 표는 identity 만 읽는다. */
public interface CheerQuery {
    /** [from, to) 구간의 가족 응원 수(주간 요약용). */
    int countCheers(UUID familyId, Instant from, Instant to);

    /**
     * 이 프로필이 [from, to) 구간에 받은 응원 전부(모든 kind). createdAt 오름차순.
     * 캘린더 스티커 · 경험치처럼 kind 로 거를 쪽이 거른다.
     */
    List<CheerView> received(UUID toProfileId, Instant from, Instant to);
}
