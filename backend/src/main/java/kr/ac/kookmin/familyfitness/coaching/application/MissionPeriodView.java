package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.UUID;

/** 만든 미션 한 건과 그 기간. 여러 날 만들기면 startDate = endDate = 그날이다. */
public record MissionPeriodView(UUID missionId, LocalDate startDate, LocalDate endDate) {}
