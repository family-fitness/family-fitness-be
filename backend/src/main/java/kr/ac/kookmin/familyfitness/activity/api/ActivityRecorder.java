package kr.ac.kookmin.familyfitness.activity.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 활동 기록. 미션 진행도 갱신은 coaching 이 이 결과를 받아서 한다.
 * 활동 시간은 초로 쌓고(결정 37), 분은 그 초 합을 60 으로 나눠 내림한 값이다.
 */
public interface ActivityRecorder {
    /** 걸음수는 그날 총량으로 덮어쓴다(누적 아님). source = MANUAL. */
    DailyActivity overwriteSteps(UUID profileId, LocalDate activityDate, int steps);

    /** 분을 누적한다({@code minutes × 60} 초). source 는 TIMER 또는 VIDEO. 그날 해당 출처의 누적 결과를 돌려준다. */
    DailyActivity addActiveMinutes(UUID profileId, LocalDate activityDate, ActivitySource source, int minutes);

    /**
     * 초를 누적한다. source 는 TIMER 또는 VIDEO, 초는 0 보다 커야 한다. 운동 한 칸 끝이 영상 재생 초를 VIDEO 로 쌓는다.
     * 그날 해당 출처의 누적 결과를 돌려준다(분은 내림).
     */
    DailyActivity addActiveSeconds(UUID profileId, LocalDate activityDate, ActivitySource source, int seconds);
}
