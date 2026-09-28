package kr.ac.kookmin.familyfitness.coaching.application;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import org.jspecify.annotations.Nullable;

/**
 * 한 사람의 날짜별 기록 — FE 요청서 3장 · 4장 「날짜별 기록」 모양(fe:src/lib/api/types.ts CalendarView).
 * 넣는 날 · 셈은 {@link CalendarService} 에 있다.
 *
 * @param days 날짜 오름차순. 아무것도 없는 날은 싣지 않는다
 */
public record CalendarView(UUID profileId, LocalDate from, LocalDate to, List<Day> days) {
    /**
     * 하루.
     *
     * @param minutes 그날 서버가 잰 활동(TIMER + VIDEO) 초 합 ÷ 60, 내림
     * @param plannedMinutes 그날 선 운동의 잡힌 분 합. 선 운동이 없거나 합이 0 이면 null
     * @param rest 쉬는 날이면 true. 아니면 응답에 싣지 않는다
     */
    public record Day(
            LocalDate date,
            int minutes,
            @Nullable Integer plannedMinutes,
            List<Entry> entries,
            List<Sticker> stickers,
            @JsonInclude(NON_NULL) @Nullable Boolean rest) {}

    /**
     * 그날 선 운동 하나.
     *
     * @param minutes 그날 끝낸 칸의 잡힌 분 합(칸이 없으면 진행도로 셈). 걸음수는 0
     * @param verifiedBy 이 사람의 확인 방법. 아직 없으면 null
     * @param completed 다 했는가. 여러 날짜리는 마지막 칸을 끝낸 날에만 true
     * @param sessions 칸. 칸 없는 운동은 null
     */
    public record Entry(
            UUID missionId,
            String title,
            int minutes,
            @Nullable VerifiedBy verifiedBy,
            boolean completed,
            @Nullable List<Session> sessions) {}

    /**
     * 칸 하나(FE ASKS 0-2 — position · clip · verifiedBy 를 더했다).
     *
     * @param verifiedBy 그날 끝낸 칸의 확인 방법. 안 끝냈으면 null
     * @param done 이 사람이 그날 끝냈는가. 여러 날짜리는 다른 날 끝낸 칸은 false
     */
    public record Session(
            int position,
            SessionPhase phase,
            String title,
            int minutes,
            @Nullable SessionClipView clip,
            @Nullable VerifiedBy verifiedBy,
            boolean done) {}

    /** 그날(KST, 붙인 시각 기준) 받은 칭찬 스티커 한 장. */
    public record Sticker(
            UUID cheerId,
            String stickerId,
            UUID fromProfileId,
            String fromName,
            @Nullable String message,
            @Nullable UUID missionId,
            Instant createdAt) {}
}
