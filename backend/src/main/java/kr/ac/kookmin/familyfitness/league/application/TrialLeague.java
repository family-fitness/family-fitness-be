package kr.ac.kookmin.familyfitness.league.application;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.league.domain.AchievementRate.Result;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTable;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTier;
import org.jspecify.annotations.Nullable;

/**
 * 심사용 체험 가족의 리그 방 — 그 가족 + 가짜 가족 일곱. 체험 가족을 실제 가족의 방에 섞지 않으려고 둔다.
 *
 * <pre>
 * 티어      브론즈(처음 들어온 가족과 같다)
 * 우리 가족  자리 1. 달성률 · 점수는 실제 가족과 같은 셈으로 지금 센다
 * 가짜 가족  자리 2~8. 이름 · 달성률(30~100%) · 점수는 체험 가족 id 를 시드로 삼아 뽑아 부를 때마다 같다.
 *           점수 = 달성률 ÷ 100 × (0.45~1) — 실제 셈처럼 날마다 한 집이 달성률 같은 하루치 집보다 앞선다
 * </pre>
 *
 * DB 의 방(league_rounds · league_members)에 적지 않으므로 월초 정산 · 다른 가족의 순위표에 나오지 않는다. 지난달 방도 없다.
 */
final class TrialLeague {
    /** 가짜 가족 수. 우리 가족까지 여덟이라 오르내리는 자리(8가족 이상)가 화면에 보인다. */
    static final int FAKE_FAMILIES = 7;

    private static final List<String> NAMES =
            List.of("도토리네", "햇살네", "바람개비네", "콩콩이네", "새싹네", "구름이네", "별빛네", "산들네", "몽글이네", "다람쥐네", "무지개네", "튼튼이네");

    private TrialLeague() {}

    /**
     * 체험 방의 순위표와 가짜 가족 이름.
     *
     * @param names 가짜 가족 id → 이름. 체험 가족 이름은 싣지 않는다(프로필에서 읽는다)
     */
    record Room(LeagueTable table, Map<UUID, String> names) {}

    static Room of(UUID trialFamilyId, @Nullable Result mine) {
        Random random = new Random(trialFamilyId.getMostSignificantBits() ^ trialFamilyId.getLeastSignificantBits());
        List<String> pool = new ArrayList<>(NAMES);
        Collections.shuffle(pool, random);
        List<LeagueTable.Seat> seats = new ArrayList<>();
        seats.add(new LeagueTable.Seat(
                trialFamilyId, 1, mine != null ? mine.rate() : null, mine != null ? mine.score() : null));
        Map<UUID, String> names = new LinkedHashMap<>();
        for (int i = 0; i < FAKE_FAMILIES; i++) {
            UUID fakeId =
                    UUID.nameUUIDFromBytes((trialFamilyId + "/trial-league/" + i).getBytes(StandardCharsets.UTF_8));
            int rate = 30 + random.nextInt(71);
            double score = Math.round(rate / 100.0 * (0.45 + random.nextDouble() * 0.55) * 10_000) / 10_000.0;
            seats.add(new LeagueTable.Seat(fakeId, i + 2, rate, score));
            names.put(fakeId, pool.get(i));
        }
        return new Room(new LeagueTable(LeagueTier.START, seats), names);
    }
}
