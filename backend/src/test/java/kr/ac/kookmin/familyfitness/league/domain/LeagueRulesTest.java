package kr.ac.kookmin.familyfitness.league.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.league.domain.AchievementRate.ChildDays;
import kr.ac.kookmin.familyfitness.league.domain.LeagueTable.Seat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 리그 규칙 — FE 목(fe:src/mocks/league.ts) · FE 화면의 올라가는/내려가는 자리 셈(fe:src/lib/league.ts zoneOf,
 * fe:src/app/parent/league/page.tsx 의 ranked) · FE 요청서 3장.
 */
class LeagueRulesTest {
    /** 자리 번호 차례로 가족을 만든다. rate 는 null 일 수 있다. */
    private static List<Seat> seats(Integer... rates) {
        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < rates.length; i++) {
            Integer rate = rates[i];
            seats.add(new Seat(UUID.randomUUID(), i + 1, rate, rate == null ? null : rate / 100.0));
        }
        return seats;
    }

    private static List<LeagueMove> moves(LeagueTable table) {
        return table.seats().stream().map(it -> table.moveOf(it.familyId())).toList();
    }

    private static List<Integer> ranks(LeagueTable table) {
        return table.seats().stream().map(it -> table.rankOf(it.familyId())).toList();
    }

    @Nested
    @DisplayName("올라가는 · 내려가는 자리")
    class Zones {
        @Test
        @DisplayName("열 가족 골드 방 — 위 셋은 올라가고 아래 셋은 내려간다(promote · demote 3)")
        void 열_가족() {
            LeagueTable table = new LeagueTable(LeagueTier.GOLD, seats(90, 80, 70, 60, 55, 50, 40, 30, 20, 10));

            assertThat(table.groupSize()).isEqualTo(10);
            assertThat(table.promote()).isEqualTo(3);
            assertThat(table.demote()).isEqualTo(3);
            assertThat(moves(table))
                    .containsExactly(
                            LeagueMove.UP,
                            LeagueMove.UP,
                            LeagueMove.UP,
                            LeagueMove.STAY,
                            LeagueMove.STAY,
                            LeagueMove.STAY,
                            LeagueMove.STAY,
                            LeagueMove.DOWN,
                            LeagueMove.DOWN,
                            LeagueMove.DOWN);
        }

        @Test
        @DisplayName("다이아는 promote 0 · 브론즈는 demote 0 — 더 오를 곳 · 내려갈 곳이 없다")
        void 맨_위와_맨_아래() {
            LeagueTable diamond = new LeagueTable(LeagueTier.DIAMOND, seats(90, 80, 70, 60, 50, 40, 30, 20));
            LeagueTable bronze = new LeagueTable(LeagueTier.BRONZE, seats(90, 80, 70, 60, 50, 40, 30, 20));

            assertThat(diamond.promote()).isZero();
            assertThat(diamond.demote()).isEqualTo(3);
            assertThat(moves(diamond)).doesNotContain(LeagueMove.UP);
            assertThat(bronze.promote()).isEqualTo(3);
            assertThat(bronze.demote()).isZero();
            assertThat(moves(bronze)).doesNotContain(LeagueMove.DOWN);
        }

        @Test
        @DisplayName("8가족 미만 방은 오르내리지 않는다 — promote · demote 0")
        void 여덟_가족_미만() {
            LeagueTable table = new LeagueTable(LeagueTier.SILVER, seats(90, 80, 70, 60, 50, 40, 30));

            assertThat(table.promote()).isZero();
            assertThat(table.demote()).isZero();
            assertThat(moves(table)).containsOnly(LeagueMove.STAY);
        }

        @Test
        @DisplayName("달성률이 없는 집은 그대로 머물고, 자리는 달성률이 있는 집끼리 절반까지만 센다(FE 화면과 같은 셈)")
        void 달성률_없는_집() {
            // 열 가족 중 넷이 셀 날이 없다 — ranked 6, 절반 3 이라 위 셋 올라가고 아래 셋(4~6등) 내려간다
            LeagueTable table = new LeagueTable(LeagueTier.GOLD, seats(90, 80, 70, 60, 50, 40, null, null, null, null));

            assertThat(table.ranked()).isEqualTo(6);
            assertThat(moves(table))
                    .containsExactly(
                            LeagueMove.UP,
                            LeagueMove.UP,
                            LeagueMove.UP,
                            LeagueMove.DOWN,
                            LeagueMove.DOWN,
                            LeagueMove.DOWN,
                            LeagueMove.STAY,
                            LeagueMove.STAY,
                            LeagueMove.STAY,
                            LeagueMove.STAY);
        }

        @Test
        @DisplayName("셀 날이 있는 집이 넷뿐이면 절반인 둘씩만 오르내린다")
        void 절반까지만() {
            LeagueTable table = new LeagueTable(LeagueTier.GOLD, seats(90, 80, 70, 60, null, null, null, null));

            assertThat(moves(table))
                    .containsExactly(
                            LeagueMove.UP,
                            LeagueMove.UP,
                            LeagueMove.DOWN,
                            LeagueMove.DOWN,
                            LeagueMove.STAY,
                            LeagueMove.STAY,
                            LeagueMove.STAY,
                            LeagueMove.STAY);
        }

        @Test
        @DisplayName("경계에서 동률이면 같은 순위 — 3등 동률 두 집이 모두 올라간다(두 집 화면 모두 「올라가요」 였다)")
        void 경계의_동률() {
            LeagueTable table = new LeagueTable(LeagueTier.GOLD, seats(90, 80, 70, 70, 50, 40, 30, 20));

            assertThat(ranks(table)).containsExactly(1, 2, 3, 3, 5, 6, 7, 8);
            assertThat(moves(table))
                    .containsExactly(
                            LeagueMove.UP,
                            LeagueMove.UP,
                            LeagueMove.UP,
                            LeagueMove.UP,
                            LeagueMove.STAY,
                            LeagueMove.DOWN,
                            LeagueMove.DOWN,
                            LeagueMove.DOWN);
        }
    }

    @Nested
    @DisplayName("순위표 차례와 순위")
    class Order {
        @Test
        @DisplayName("달성률 내림차순 · 없는 집은 맨 아래 · 같으면 보는 가족 먼저, 그다음 들어온 차례")
        void 차례() {
            List<Seat> seats = seats(60, null, 80, 60, 60);
            LeagueTable table = new LeagueTable(LeagueTier.BRONZE, seats);
            UUID me = seats.get(3).familyId();

            assertThat(table.orderedFor(me).stream().map(Seat::seatNo)).containsExactly(3, 4, 1, 5, 2);
            assertThat(table.rankOf(me)).isEqualTo(2);
            assertThat(table.rankOf(seats.get(0).familyId())).isEqualTo(2);
            assertThat(table.rankOf(seats.get(1).familyId())).isNull();
        }
    }

    @Nested
    @DisplayName("달성률 — FE 목 familyRate")
    class Rate {
        private final LocalDate from = LocalDate.of(2026, 9, 1);
        private final LocalDate today = LocalDate.of(2026, 9, 15);

        private Set<LocalDate> days(int... dayOfMonth) {
            return Set.copyOf(
                    Arrays.stream(dayOfMonth).mapToObj(from::withDayOfMonth).toList());
        }

        @Test
        @DisplayName("잡힌 날 중 해낸 날 — 쉬는 날은 빼고, 오늘은 해냈을 때만 센다")
        void 한_아이() {
            ChildDays kid = new ChildDays(days(1, 2, 3, 4, 15), days(1, 3, 10));

            // 센 날 = 1 · 3 · 4 (2일은 쉬는 날, 15일은 오늘이고 아직 안 움직였다) → 2/3
            assertThat(AchievementRate.of(List.of(kid), days(2), from, today, today))
                    .isEqualTo(67);

            ChildDays movedToday = new ChildDays(kid.planned(), days(1, 3, 10, 15));
            assertThat(AchievementRate.of(List.of(movedToday), days(2), from, today, today))
                    .isEqualTo(75);
        }

        @Test
        @DisplayName("쉬는 날에 움직였어도 그날은 빠진다(FE 목 !rest.has(d))")
        void 쉬는_날에_움직임() {
            ChildDays kid = new ChildDays(days(1, 2), days(2));

            assertThat(AchievementRate.of(List.of(kid), days(2), from, today, today))
                    .isZero();
        }

        @Test
        @DisplayName("아이들 평균을 반올림한다 — 셀 날이 없는 아이는 평균에서 뺀다")
        void 아이들_평균() {
            ChildDays half = new ChildDays(days(1, 2), days(1));
            ChildDays third = new ChildDays(days(1, 2, 3), days(3));
            ChildDays nothing = new ChildDays(days(), days(1, 2, 3));

            // (1/2 + 1/3) / 2 = 0.41666 → 42
            assertThat(AchievementRate.of(List.of(half, third, nothing), Set.of(), from, today, today))
                    .isEqualTo(42);
        }

        @Test
        @DisplayName("셀 날이 없으면 null 이다 — 0 이 아니다(달의 첫날 아침 · 오늘 잡힌 운동을 아직 안 함)")
        void 셀_날이_없으면_null() {
            ChildDays firstMorning = new ChildDays(days(15), days());

            assertThat(AchievementRate.of(List.of(firstMorning), Set.of(), from, today, today))
                    .isNull();
            assertThat(AchievementRate.of(List.of(), Set.of(), from, today, today))
                    .isNull();
        }

        @Test
        @DisplayName("셈 기간 밖의 잡힌 날은 세지 않는다 — 지난달 말일까지로 굳힐 때")
        void 셈_기간() {
            ChildDays kid = new ChildDays(Set.of(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)), Set.of());

            assertThat(AchievementRate.of(
                            List.of(kid), Set.of(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), today))
                    .isZero();
        }
    }

    @Test
    @DisplayName("티어는 한 칸씩 오르내리고 맨 위 · 맨 아래를 넘지 않는다")
    void 티어() {
        assertThat(LeagueTier.START).isEqualTo(LeagueTier.BRONZE);
        assertThat(LeagueTier.GOLD.after(LeagueMove.UP)).isEqualTo(LeagueTier.PLATINUM);
        assertThat(LeagueTier.GOLD.after(LeagueMove.DOWN)).isEqualTo(LeagueTier.SILVER);
        assertThat(LeagueTier.DIAMOND.after(LeagueMove.UP)).isEqualTo(LeagueTier.DIAMOND);
        assertThat(LeagueTier.BRONZE.after(LeagueMove.DOWN)).isEqualTo(LeagueTier.BRONZE);
    }
}
