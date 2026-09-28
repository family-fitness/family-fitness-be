package kr.ac.kookmin.familyfitness.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability.RawSlot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 운동할 수 있는 시간의 값 규칙. FE 목(fe:src/mocks/handlers.ts PUT availability)과 같고, 같은 요일 두 칸만 더 막는다. */
class WeeklyAvailabilityTest {
    private static RawSlot slot(@Nullable String day, @Nullable String start, @Nullable String minutes) {
        return new RawSlot(day, start, minutes == null ? null : new BigDecimal(minutes));
    }

    private static WeeklyAvailability parse(@Nullable RawSlot... slots) {
        return WeeklyAvailability.parse(Arrays.asList(slots));
    }

    private static void assertInvalid(@Nullable RawSlot... slots) {
        assertThatThrownBy(() -> parse(slots))
                .isInstanceOf(InvalidSlotException.class)
                .extracting("code")
                .isEqualTo("INVALID_SLOT");
    }

    @Test
    @DisplayName("보낸 차례와 상관없이 요일 차례(월 → 일)로 줄 세운다")
    void sortsByWeekday() {
        WeeklyAvailability week =
                parse(slot("SUN", "10:00", "30"), slot("MON", "19:00", "20"), slot("SAT", "09:30", "40"));

        assertThat(week.slots())
                .containsExactly(
                        new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(19, 0), 20),
                        new AvailabilitySlot(DayOfWeek.SATURDAY, LocalTime.of(9, 30), 40),
                        new AvailabilitySlot(DayOfWeek.SUNDAY, LocalTime.of(10, 0), 30));
    }

    @Test
    @DisplayName("빈 목록은 된다 — 적어 둔 시간을 모두 지운다")
    void emptyWeek() {
        assertThat(WeeklyAvailability.parse(List.of()).slots()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "120", "20.0"})
    @DisplayName("분은 5 이상 120 이하의 정수면 받는다(20.0 은 20)")
    void minutesInRange(String minutes) {
        int expected = new BigDecimal(minutes).intValueExact();

        assertThat(parse(slot("MON", "19:00", minutes)).slots().getFirst().minutes())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4", "121", "0", "-10", "20.5", "1E+3"})
    @DisplayName("분이 5 ~ 120 밖이거나 소수면 400 INVALID_SLOT — 소수를 버려 받지 않는다")
    void minutesOutOfRange(String minutes) {
        assertInvalid(slot("MON", "19:00", minutes));
    }

    @Test
    @DisplayName("분이 없으면 400 INVALID_SLOT")
    void minutesMissing() {
        assertInvalid(slot("MON", "19:00", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "09:05", "19:00", "23:59"})
    @DisplayName("시작 시각은 HH:mm 두 자리씩, 00:00 ~ 23:59")
    void startAccepted(String start) {
        assertThat(WeeklyAvailability.startText(
                        parse(slot("MON", start, "20")).slots().getFirst().start()))
                .isEqualTo(start);
    }

    @ParameterizedTest
    @ValueSource(strings = {"24:00", "7:00", "19:60", "19:00:00", " 19:00", "19:0", "1900", ""})
    @DisplayName("시작 시각 형식이 다르면 400 INVALID_SLOT")
    void startRejected(String start) {
        assertInvalid(slot("MON", start, "20"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mon", "MONDAY", "Mon", "1", ""})
    @DisplayName("요일은 MON … SUN 대문자 그대로만 받는다")
    void dayRejected(String day) {
        assertInvalid(slot(day, "19:00", "20"));
    }

    @Test
    @DisplayName("요일 · 시각이 없거나 칸 자리가 null 이면 400 INVALID_SLOT")
    void missingValues() {
        assertInvalid(slot(null, "19:00", "20"));
        assertInvalid(slot("MON", null, "20"));
        assertInvalid(slot("MON", "19:00", "20"), null);
    }

    @Test
    @DisplayName("같은 요일이 두 번이면 400 INVALID_SLOT — 화면 · 홈 링이 하루 한 칸을 전제한다")
    void duplicateDay() {
        assertThatThrownBy(() -> parse(slot("MON", "07:00", "10"), slot("MON", "19:00", "20")))
                .isInstanceOf(InvalidSlotException.class)
                .hasMessageContaining("MON");
    }

    @Test
    @DisplayName("요일 이름은 MON … SUN 과 DayOfWeek 사이를 오간다")
    void dayCodeRoundTrip() {
        assertThat(Arrays.stream(DayOfWeek.values()).map(WeeklyAvailability::dayCode))
                .containsExactly("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        for (DayOfWeek day : DayOfWeek.values()) {
            assertThat(WeeklyAvailability.dayOf(WeeklyAvailability.dayCode(day)))
                    .isEqualTo(day);
        }
    }
}
