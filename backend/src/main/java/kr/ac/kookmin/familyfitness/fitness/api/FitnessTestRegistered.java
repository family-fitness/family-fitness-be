package kr.ac.kookmin.familyfitness.fitness.api;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 측정 회차가 저장됐다는 도메인 이벤트. 저장과 같은 트랜잭션 안에서 발행한다.
 * 경험치(progress)는 같은 트랜잭션에서 동기로 듣는다 — 측정 등록 뒤 FE 가 곧바로 다시 읽는 레벨에 반영돼 있어야 한다.
 *
 * <p>「다시 잰 회차」 는 testedOn 이 가장 이른 회차 하나를 뺀 나머지 전부다(FE 목 fe:src/mocks/progress.ts 의
 * {@code tests.slice(0, -1)} — 목의 이력은 testedOn 이 늦은 것부터다). 등록 순서가 아니라 날짜로 정한다.
 *
 * @param remeasured 이 등록으로 새로 「다시 잰 회차」 가 된 회차. 새 회차보다 이른 회차가 있으면 새 회차 자신이다.
 *     새 회차가 가장 이른 날이 되면(지난 날짜를 나중에 적은 경우) 그때까지 가장 이르던 회차다. 이 프로필의 처음 회차면 null
 */
public record FitnessTestRegistered(
        UUID profileId,
        UUID fitnessTestId,
        LocalDate testedOn,
        @Nullable Round remeasured) {

    /** 측정 회차 하나를 가리킨다. */
    public record Round(UUID fitnessTestId, LocalDate testedOn) {}
}
