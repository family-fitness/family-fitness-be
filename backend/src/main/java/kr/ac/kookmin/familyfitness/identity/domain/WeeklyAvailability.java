package kr.ac.kookmin.familyfitness.identity.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import org.jspecify.annotations.Nullable;

/**
 * 한 사람의 한 주 「운동할 수 있는 시간」. 요일 차례(월 → 일)로 하루 한 칸씩 든다.
 *
 * <p>값 규칙은 FE 목(fe:src/mocks/handlers.ts 의 PUT availability)과 같다. 하나라도 어기면 400 INVALID_SLOT:
 *
 * <ol>
 *   <li>칸 자리가 비어 있으면(null) 안 된다.
 *   <li>요일은 MON · TUE · WED · THU · FRI · SAT · SUN 중 하나. 대문자 그대로만 받는다.
 *   <li>시작 시각은 「HH:mm」(00:00 ~ 23:59, 두 자리씩). 한국 시각이다.
 *   <li>분은 5 이상 120 이하의 정수. 소수는 버리지 않고 거절한다.
 *   <li>같은 요일이 두 번 나오면 안 된다. 목은 이것을 막지 않지만, 화면(설정 · 첫 시작)이 하루 한 칸만 그리고
 *       홈 링이 칸 수를 「이번 주 며칠」 목표로 세므로 여기서 막는다.
 * </ol>
 *
 * 빈 목록은 된다 — 적어 둔 시간을 모두 지운다.
 */
public final class WeeklyAvailability {
    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 120;

    private static final Pattern START = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final BigDecimal MIN = BigDecimal.valueOf(MIN_MINUTES);
    private static final BigDecimal MAX = BigDecimal.valueOf(MAX_MINUTES);

    private final List<AvailabilitySlot> slots;

    private WeeklyAvailability(List<AvailabilitySlot> slots) {
        this.slots = slots.stream()
                .sorted(Comparator.comparing(AvailabilitySlot::day))
                .toList();
    }

    /** 요청의 한 칸. 형식을 여기서 판단하려고 받은 그대로(문자열 · 소수) 둔다. */
    public record RawSlot(
            @Nullable String day,
            @Nullable String start,
            @Nullable BigDecimal minutes) {}

    /** 요청 칸들을 읽는다. 위 규칙을 하나라도 어기면 {@link InvalidSlotException}. */
    public static WeeklyAvailability parse(List<? extends @Nullable RawSlot> raw) {
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        List<AvailabilitySlot> slots = new ArrayList<>(raw.size());
        for (RawSlot each : raw) {
            if (each == null) throw new InvalidSlotException();
            AvailabilitySlot slot =
                    new AvailabilitySlot(dayOf(each.day()), startOf(each.start()), minutesOf(each.minutes()));
            if (!seen.add(slot.day())) {
                throw new InvalidSlotException("같은 요일이 두 번 있습니다: " + dayCode(slot.day()));
            }
            slots.add(slot);
        }
        return new WeeklyAvailability(slots);
    }

    public List<AvailabilitySlot> slots() {
        return slots;
    }

    /** 와이어 · DB 의 요일 이름. MONDAY → MON. */
    public static String dayCode(DayOfWeek day) {
        return day.name().substring(0, 3);
    }

    /** {@link #dayCode} 의 반대. 모르는 이름이면 {@link InvalidSlotException}. */
    public static DayOfWeek dayOf(@Nullable String code) {
        if (code != null) {
            for (DayOfWeek day : DayOfWeek.values()) {
                if (dayCode(day).equals(code)) return day;
            }
        }
        throw new InvalidSlotException();
    }

    /** 와이어의 시작 시각. 19:00 → "19:00". */
    public static String startText(LocalTime start) {
        return start.format(HH_MM);
    }

    private static LocalTime startOf(@Nullable String text) {
        if (text == null || !START.matcher(text).matches()) throw new InvalidSlotException();
        return LocalTime.parse(text, HH_MM);
    }

    private static int minutesOf(@Nullable BigDecimal minutes) {
        if (minutes == null || minutes.stripTrailingZeros().scale() > 0) throw new InvalidSlotException();
        if (minutes.compareTo(MIN) < 0 || minutes.compareTo(MAX) > 0) throw new InvalidSlotException();
        return minutes.intValueExact();
    }
}
