package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.CoachRunConditions;

/** 하루 편성 요청: 누구(profileId)의 어느 날(date)을 어떤 조건으로. */
public record StartCoachRunCommand(UUID profileId, LocalDate date, CoachRunConditions conditions) {}
