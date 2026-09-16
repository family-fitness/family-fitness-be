package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;

public record CoachRunRequest(
        List<Participant> profiles, String startDate, int weeks, int daysPerWeek, int minutesPerSession) {
    /** role: 주행자 · 동반자 · 응원 (응원은 편성 대상에서 빠지고 참여자로만 기록) */
    public record Participant(AiProfile profile, String role) {}
}
