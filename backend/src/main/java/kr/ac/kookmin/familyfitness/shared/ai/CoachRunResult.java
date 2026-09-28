package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * AI `GET /v1/coach/runs/{id}` 결과(AI 인터페이스-명세 4장 편성 결과). 숫자 칸은 AI 가 비워 보내도 읽히도록 null 을 허용한다.
 */
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

    /** notices — 또래 자료가 없어 넓혀 골랐을 때의 알림. 없으면 빈 목록. */
    public record Proposal(List<Mission> missions, List<Citation> citations, List<String> notices) {}

    /**
     * kind — 일간 · 주간(모르면 빈 문자열). durationMin — 그 회 운동 시간(요청한 분).
     * videoSec — 클립 길이의 합(초)이라 durationMin 과 다르다. reason — 왜 이렇게 짰는지, 본문에 [n] 인용이 박힌다.
     */
    public record Mission(
            String kind,
            String title,
            String startDate,
            String endDate,
            List<ParticipantRef> participants,
            @Nullable Integer durationMin,
            @Nullable Integer videoSec,
            List<Session> sessions,
            String copyChild,
            String copyParent,
            String reason) {}

    public record ParticipantRef(String ref, String role) {}

    /** phase — 준비운동 · 본운동 · 정리운동. order — 그날 안의 차례(1부터). durationSec — 그 클립 길이(초). */
    public record Session(
            @Nullable Integer dayOffset,
            String phase,
            @Nullable Integer order,
            String exerciseName,
            String fitnessFactor,
            @Nullable Integer durationSec,
            @Nullable Video video,
            List<Integer> evidence) {}

    /** 클립 구간. startSec 부터 endSec 까지만 튼다. */
    public record Video(
            String videoId,
            @Nullable Integer startSec,
            @Nullable Integer endSec) {}
}
