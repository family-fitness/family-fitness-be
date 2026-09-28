package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability;

/** 한 사람의 한 주. {@code slots} 는 요일 차례(월 → 일), 적어 둔 것이 없으면 빈 목록. {@code start} 는 한국 시각 「HH:mm」. */
public record AvailabilityResponse(UUID profileId, List<SlotView> slots) {
    public record SlotView(String day, String start, int minutes) {}

    static AvailabilityResponse of(UUID profileId, List<AvailabilitySlot> slots) {
        return new AvailabilityResponse(
                profileId,
                slots.stream()
                        .map(it -> new SlotView(
                                WeeklyAvailability.dayCode(it.day()),
                                WeeklyAvailability.startText(it.start()),
                                it.minutes()))
                        .toList());
    }
}
