package kr.ac.kookmin.familyfitness.league.domain;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 방 하나의 순위표 — 조회(GET)와 월초 정산이 같이 쓴다. 두 곳이 다른 식으로 세면 화면은 「올라가요」 인데 정산은 안 올린다.
 *
 * <pre>
 * 방 크기(groupSize)     방에 든 가족 수
 * 올라가는 자리(promote)  3. 다이아 0 · 방이 8가족 미만이면 0          fe:src/mocks/league.ts:24,123 · FE 요청서 0장 수정 요청 1
 * 내려가는 자리(demote)   3. 브론즈 0 · 방이 8가족 미만이면 0          fe:src/mocks/league.ts:24,124
 * 줄 세우기              달성률 내림차순, 없는 집(null)은 맨 아래,      fe:src/mocks/league.ts:115
 *                        같으면 보는 가족 먼저, 그다음 들어온 자리 차례
 * 순위(rank)             달성률이 나보다 높은 집 수 + 1(같으면 같은 순위). fe:src/mocks/league.ts:116 — 보는 가족을 같은 값 맨 앞에 두면
 *                        달성률이 없으면 null                          그 자리 + 1 이 곧 이 값이다
 * 가는 곳(move)          FE zoneOf 그대로 — 달성률이 있는 집 수(ranked)로   fe:src/lib/league.ts:46-58,
 *                        up = min(promote, ⌊ranked/2⌋), down = min(demote, ⌊ranked/2⌋),  fe:src/app/parent/league/page.tsx:106-107
 *                        rank ≤ up 이면 UP, rank &gt; ranked − down 이면 DOWN, 나머지 · 달성률 없는 집은 STAY
 * </pre>
 *
 * 순위를 같은 값끼리 같게 두는 것은 가족마다 화면에서 본 자리와 정산 결과를 맞추려는 것이다. 보는 가족을 같은 값 맨 앞에 두는 목은
 * 동률인 두 집이 모두 자기를 앞자리로 본다. 그래서 경계에서 동률이면 두 집 모두 올라가고(또는 모두 머물고), 어느 쪽 화면도 틀리지 않는다.
 */
public record LeagueTable(LeagueTier tier, List<Seat> seats) {
    /** 한 방의 가족 수 상한 — 목의 이웃 아홉 + 우리 = 열(fe:src/mocks/league.ts:27-37), 요청서 「열 가족 안팎」 */
    public static final int GROUP_SIZE = 10;

    /** 올라가는 · 내려가는 자리 수(fe:src/mocks/league.ts:24 MOVE) */
    public static final int MOVES = 3;

    /** 이보다 적은 방은 오르내리지 않는다(FE 요청서 0장 수정 요청 1 「명세의 8가족 미만」) */
    public static final int MIN_FAMILIES_FOR_MOVES = 8;

    /**
     * 방의 한 자리.
     *
     * @param seatNo 방에 들어온 차례(1부터). 동률일 때 보는 가족 다음의 차례다
     * @param rate 달성률(%). 셀 날이 없으면 null
     */
    public record Seat(UUID familyId, int seatNo, @Nullable Integer rate) {}

    public LeagueTable {
        seats = List.copyOf(seats);
    }

    public int groupSize() {
        return seats.size();
    }

    /** 달성률이 있는 집 수. 올라가는 · 내려가는 자리를 이 수로 자른다(FE 화면과 같다). */
    public int ranked() {
        return (int) seats.stream().filter(it -> it.rate() != null).count();
    }

    public int promote() {
        return tier.isTop() || groupSize() < MIN_FAMILIES_FOR_MOVES ? 0 : MOVES;
    }

    public int demote() {
        return tier.isBottom() || groupSize() < MIN_FAMILIES_FOR_MOVES ? 0 : MOVES;
    }

    /** 이 가족의 순위(1부터). 달성률이 없으면 null. 방에 없는 가족이면 IllegalArgumentException. */
    public @Nullable Integer rankOf(UUID familyId) {
        Integer rate = seatOf(familyId).rate();
        if (rate == null) return null;
        int higher = (int) seats.stream()
                .filter(it -> it.rate() != null && it.rate() > rate)
                .count();
        return higher + 1;
    }

    /** 달이 바뀔 때 이 가족이 가는 곳. */
    public LeagueMove moveOf(UUID familyId) {
        Integer rank = rankOf(familyId);
        if (rank == null) return LeagueMove.STAY;
        int ranked = ranked();
        int half = ranked / 2;
        int up = Math.min(promote(), half);
        int down = Math.min(demote(), half);
        if (rank <= up) return LeagueMove.UP;
        if (down > 0 && rank > ranked - down) return LeagueMove.DOWN;
        return LeagueMove.STAY;
    }

    /** {@code viewer} 가 보는 순위표 차례. */
    public List<Seat> orderedFor(UUID viewer) {
        Comparator<Seat> byRate = Comparator.comparing(
                Seat::rate,
                Comparator.nullsLast(Comparator.<Integer>naturalOrder().reversed()));
        Comparator<Seat> viewerFirst = Comparator.comparing(it -> !it.familyId().equals(viewer));
        return seats.stream()
                .sorted(byRate.thenComparing(viewerFirst).thenComparingInt(Seat::seatNo))
                .toList();
    }

    private Seat seatOf(UUID familyId) {
        return seats.stream()
                .filter(it -> it.familyId().equals(familyId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이 방에 없는 가족입니다: " + familyId));
    }
}
