package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 가족의 쉬는 날을 다른 모듈(캘린더 · 이어서 한 날 · 리그)이 읽는 통로. 권한은 부르는 쪽이 본다. */
public interface RestDayQuery {
    /** {@code from}~{@code to}(양끝 포함) 안의 쉬는 날. 오름차순이다. 달을 넘는 범위도 받는다. */
    List<LocalDate> restDaysBetween(UUID familyId, LocalDate from, LocalDate to);
}
