package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.UUID;

public record MemberReportView(
        UUID profileId, String name, int activeMinutes, int verifiedMinutes, int completedMissions) {}
