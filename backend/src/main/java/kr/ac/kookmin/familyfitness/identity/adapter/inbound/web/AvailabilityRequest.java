package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability.RawSlot;
import org.jspecify.annotations.Nullable;

/**
 * 한 주 통째로 바꾸기 {@code { slots: [{ day: "MON", start: "19:00", minutes: 20 }] }}.
 * {@code slots} 가 빠지면 400 BAD_REQUEST 다(빈 목록은 된다 — 모두 지우기). FE 목은 빠진 것을 빈 목록으로 보아 한 주를 지우지만,
 * 빈 본문 한 번에 적어 둔 시간을 잃지 않게 막는다. 칸 값은 400 INVALID_SLOT 으로 답하려고 받은 그대로(문자열 · 소수) 받는다.
 */
public record AvailabilityRequest(@NotNull @Nullable List<@Nullable SlotInput> slots) {
    public record SlotInput(
            @Nullable String day,
            @Nullable String start,
            @Nullable BigDecimal minutes) {}

    List<@Nullable RawSlot> toRaw() {
        return Objects.requireNonNull(slots).stream()
                .map(it -> it == null ? null : new RawSlot(it.day(), it.start(), it.minutes()))
                .toList();
    }
}
