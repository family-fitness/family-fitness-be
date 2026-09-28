package kr.ac.kookmin.familyfitness.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 이어서 한 날 — 잡힌 날 기준(결정 25). 앞 셋은 FE 시험(fe:scripts/check-mocks.mts:476-483)과 같은 경우다. */
class StreakTest {
    /** 2026-09-24 목요일 */
    private final LocalDate today = LocalDate.of(2026, 9, 24);

    private LocalDate back(int days) {
        return today.minusDays(days);
    }

    private int count(Set<LocalDate> moved, Set<LocalDate> rest, Set<LocalDate> planned) {
        return Streak.count(today, moved, rest, planned);
    }

    @Test
    @DisplayName("오늘 아직이면 어제부터 센다 — 오늘이 잡힌 날이어도 끊지 않는다")
    void 오늘_아직이면_어제부터_센다() {
        assertThat(count(Set.of(back(1), back(2)), Set.of(), Set.of(today, back(1), back(2))))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("쉬는 날은 사이를 잇고 수에 더하지 않는다 — 잡힌 날이어도 쉬는 날이면 끊지 않는다")
    void 쉬는_날은_사이를_잇고_수에_더하지_않는다() {
        assertThat(count(Set.of(back(1), back(3)), Set.of(back(2)), Set.of(back(1), back(2), back(3))))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("쉬는 날만으로는 이어서 한 날이 생기지 않는다")
    void 쉬는_날만으로는_이어서_한_날이_생기지_않는다() {
        assertThat(count(Set.of(), Set.of(today, back(1)), Set.of())).isZero();
    }

    @Test
    @DisplayName("월 · 목 주 2회를 다 하면 목요일에 2일째다 — 잡히지 않은 화 · 수는 건너뛴다(결정 25 예)")
    void 월_목_주_2회를_다_하면_목요일에_2일째다() {
        LocalDate monday = LocalDate.of(2026, 9, 21);
        assertThat(count(Set.of(today, monday), Set.of(), Set.of(today, monday)))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("잡힌 날을 빼먹으면 끊긴다 — 그 앞에 움직인 날은 세지 않는다")
    void 잡힌_날을_빼먹으면_끊긴다() {
        assertThat(count(Set.of(back(1), back(3)), Set.of(), Set.of(back(1), back(2), back(3))))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("잡히지 않은 날에 스스로 움직였으면 +1, 쉬는 날에 움직였어도 +1")
    void 잡히지_않은_날에_움직였으면_더한다() {
        assertThat(count(Set.of(back(1), back(2)), Set.of(back(2)), Set.of())).isEqualTo(2);
    }

    @Test
    @DisplayName("400일까지만 거꾸로 본다")
    void 사백일까지만_본다() {
        Set<LocalDate> moved = new HashSet<>();
        for (int back = 0; back < 500; back++) moved.add(back(back));
        assertThat(count(moved, Set.of(), Set.of())).isEqualTo(Streak.MAX_DAYS);
        assertThat(Streak.windowStart(today)).isEqualTo(back(Streak.MAX_DAYS - 1));
    }
}
