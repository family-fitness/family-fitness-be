package kr.ac.kookmin.familyfitness.shared.ai;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** AI `POST /v1/coach/runs` 요청(AI 인터페이스-명세 4장). */
public record CoachRunRequest(List<Participant> profiles, String startDate, int weeks, Constraints constraints) {
    /** role: 주행자 · 동반자 · 응원 (응원은 편성 대상에서 빠지고 참여자로만 기록) */
    public record Participant(AiProfile profile, String role) {}

    /**
     * daysPerWeek · minutesPerSession — 일간 미션. weeklyMinutes — 주간 미션(null 이면 만들지 않는다).
     * quiet · smallSpace · noProps — 클립 조건(ai:video/catalog.py Conditions).
     * focusFactor(한글 요인 라벨) — 보호자가 키워 주고 싶은 역량. AI develop 은 아직 이 칸을 몰라 받아서 버린다. AI 에서 이 칸을
     * 받는 변경이 develop 에 들어간 뒤부터 AI 편성에 반영되고, 그 전에 배포한 AI 는 버린다. 대체 편성 · 스텁은 지금도 쓴다.
     * withCompanion — AI 계약에 아직 없는 칸이다. AI 는 모르는 칸을 무시한다(pydantic 기본값).
     * recentVideoIds — 대상이 최근 14일 동안 미션으로 받은 영상 id(유튜브 id · 공단 파일 이름), 최근 것부터 최대 150개.
     * AI 는 후보를 고를 때 이 영상들을 뒤로 미룬다(같은 요인 · 단계 · 연령 후보가 모자랄 때만 다시 쓴다). 이 칸을 모르는 AI 는 버린다.
     */
    public record Constraints(
            int daysPerWeek,
            int minutesPerSession,
            @Nullable Integer weeklyMinutes,
            boolean quiet,
            boolean smallSpace,
            boolean noProps,
            @Nullable String focusFactor,
            boolean withCompanion,
            List<String> recentVideoIds) {
        public Constraints {
            recentVideoIds = List.copyOf(recentVideoIds);
        }
    }
}
