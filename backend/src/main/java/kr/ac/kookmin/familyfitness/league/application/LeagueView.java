package kr.ac.kookmin.familyfitness.league.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTier;
import org.jspecify.annotations.Nullable;

/**
 * 가족 리그 — FE 요청서 3장 「가족 리그」 모양(fe:src/lib/api/types.ts FamilyLeague).
 * 다른 가족의 id · 식구 · 아이별 값은 싣지 않는다. 순위표에는 가족 이름과 달성률만 있다(규칙 14).
 *
 * @param month 「YYYY-MM」
 * @param rate 우리 가족 달성률(%). 셀 날이 없으면 null(0 이 아니다)
 * @param rank 방 안의 우리 자리(1부터). 달성률이 없으면 null
 * @param groupSize 방에 든 가족 수
 * @param promote 달이 바뀌면 올라가는 자리 수. 다이아 · 8가족 미만 방은 0
 * @param demote 달이 바뀌면 내려가는 자리 수. 브론즈 · 8가족 미만 방은 0
 * @param daysLeft 이 달이 끝나기까지 남은 날(말일 − 오늘). 지난달은 0
 * @param standings 달성률 순. 없는 집은 맨 아래, 같으면 우리 가족 먼저
 */
public record LeagueView(
        String month,
        LeagueTier tier,
        @Nullable Integer rate,
        @Nullable Integer rank,
        int groupSize,
        int promote,
        int demote,
        int daysLeft,
        List<Standing> standings) {
    /** 순위표 한 줄. {@code me} 는 보는 가족인가. */
    public record Standing(String familyName, @Nullable Integer rate, boolean me) {}
}
