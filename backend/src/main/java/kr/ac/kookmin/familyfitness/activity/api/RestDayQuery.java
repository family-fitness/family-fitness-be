package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 가족의 쉬는 날을 다른 모듈(캘린더 · 이어서 한 날 · 리그)이 읽는 통로. 권한은 부르는 쪽이 본다. */
public interface RestDayQuery {
    /** {@code from}~{@code to}(양끝 포함) 안의 쉬는 날. 오름차순이다. 달을 넘는 범위도 받는다. */
    List<LocalDate> restDaysBetween(UUID familyId, LocalDate from, LocalDate to);

    /**
     * 여러 가족의 {@link #restDaysBetween} 을 한 번에 읽는다(리그 방처럼 여러 가족을 같이 셀 때). 요청한 가족마다 한 줄이고,
     * 쉬는 날이 없으면 빈 목록이다. 기본 구현은 가족마다 {@link #restDaysBetween} 을 부른다.
     */
    default Map<UUID, List<LocalDate>> restDaysOfFamilies(Collection<UUID> familyIds, LocalDate from, LocalDate to) {
        Map<UUID, List<LocalDate>> out = new LinkedHashMap<>();
        for (UUID familyId : familyIds) out.put(familyId, restDaysBetween(familyId, from, to));
        return Collections.unmodifiableMap(out);
    }
}
