package kr.ac.kookmin.familyfitness.fitness.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface FitnessQuery {
    @Nullable
    LatestFitness latestOf(UUID profileId);

    /** 가족 중 측정 기록이 한 명이라도 있는가 (`NO_MEASURED_MEMBER` 판정). */
    boolean hasAnyTest(Collection<UUID> profileIds);

    /**
     * 프로필마다 마지막 측정일(testedOn 이 가장 늦은 회차의 날). 한 번도 안 잰 프로필은 결과에 없다. 권한은 부르는 쪽이 본다.
     * 알림(REMEASURE)이 가족의 아이들을 한 번에 묻는다. 기본 구현은 프로필마다 {@link #latestOf} 를 부른다.
     */
    default Map<UUID, LocalDate> lastTestedOn(Collection<UUID> profileIds) {
        Map<UUID, LocalDate> out = new LinkedHashMap<>();
        for (UUID profileId : profileIds) {
            LatestFitness latest = latestOf(profileId);
            if (latest != null) out.put(profileId, latest.testedOn());
        }
        return Collections.unmodifiableMap(out);
    }
}
