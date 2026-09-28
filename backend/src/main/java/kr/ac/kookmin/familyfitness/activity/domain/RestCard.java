package kr.ac.kookmin.familyfitness.activity.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 쉬는 날 카드 한 장. 가족 모두가 그날 하루 운동을 쉰다. 되돌리면 지운다(카드가 돌아온다).
 * {@code createdBy} 는 카드를 쓴 보호자 프로필이다.
 */
public record RestCard(UUID id, UUID familyId, LocalDate restDate, int cardNo, UUID createdBy, Instant createdAt) {
    public RestCard {
        if (cardNo < 1 || cardNo > RestCardMonth.PER_MONTH) {
            throw new IllegalArgumentException("카드 번호는 1~" + RestCardMonth.PER_MONTH + " 이어야 합니다: " + cardNo);
        }
    }

    public YearMonth restMonth() {
        return YearMonth.from(restDate);
    }

    public static RestCard use(UUID familyId, LocalDate restDate, int cardNo, UUID createdBy, Instant at) {
        return new RestCard(UUID.randomUUID(), familyId, restDate, cardNo, createdBy, at);
    }

    /**
     * 쓸 수 있는 날인가 — 오늘부터 이번 달 안. 지난 날에 쓰게 하면 안 한 날을 나중에 쉬는 날로 덮을 수 있다.
     * 다음 달 카드는 그달이 되어야 생기므로 다음 달 날도 받지 않는다.
     */
    public static void requireUsable(LocalDate restDate, LocalDate today) {
        if (restDate.isBefore(today) || !YearMonth.from(restDate).equals(YearMonth.from(today))) {
            throw new InvalidRestDateException("이번 달 오늘부터만 쓸 수 있습니다");
        }
    }

    /** 되돌릴 수 있는 날인가 — 오늘과 앞날. 지난 날은 되돌리지 않는다. */
    public static void requireCancellable(LocalDate restDate, LocalDate today) {
        if (restDate.isBefore(today)) throw new InvalidRestDateException("지난 날은 되돌릴 수 없습니다");
    }
}
