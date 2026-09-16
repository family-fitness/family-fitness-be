package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record CoachRunResult(
        String runId,
        /** running · succeeded · failed · refused */
        String status,
        List<Step> steps,
        @Nullable Proposal proposal,
        boolean refused,
        @Nullable String refusalReason) {
    public boolean isRunning() {
        return status.equals("running");
    }

    public record Step(int seq, String name, String status, String summary) {}

    public record Proposal(List<Mission> missions, List<Citation> citations) {}

    public record Mission(
            String title,
            String startDate,
            String endDate,
            List<ParticipantRef> participants,
            List<Session> sessions,
            String copyChild,
            String copyParent) {}

    public record ParticipantRef(String ref, String role) {}

    public record Session(
            int dayOffset,
            String exerciseName,
            String fitnessFactor,
            int durationMin,
            @Nullable Video video,
            List<Integer> evidence) {}

    public record Video(String videoId, @Nullable Integer startSec) {}
}
