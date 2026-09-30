package kr.ac.kookmin.familyfitness.coaching.application;

import java.time.LocalDate;
import java.util.UUID;

public record RecordStepsCommand(UUID profileId, LocalDate activityDate, int steps) {}
