package kr.ac.kookmin.familyfitness.activity.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 한 가족의 한 달 치 쉬는 날 카드. 매달 두 장이고 남은 카드는 다음 달로 넘기지 않는다(FE 요청서 3장 「쉬는 날 카드」).
 * 카드를 미리 만들어 두지 않고 쓴 카드만 센다 — 남은 장 = 두 장 - 그달 쓴 장.
 */
public record RestCardMonth(YearMonth month, List<RestCard> used) {
    /** 한 달에 주는 카드 장수 */
    public static final int PER_MONTH = 2;

    public RestCardMonth {
        used = List.copyOf(used);
        for (RestCard card : used) {
            if (!card.restMonth().equals(month)) {
                throw new IllegalArgumentException("다른 달 카드가 섞였습니다: " + card.restDate() + " (" + month + ")");
            }
        }
    }

    public int left() {
        return Math.max(0, PER_MONTH - used.size());
    }

    /** 쉬는 날 목록(오름차순) */
    public List<LocalDate> days() {
        return used.stream()
                .map(RestCard::restDate)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public boolean isRestDay(LocalDate date) {
        return used.stream().anyMatch(card -> card.restDate().equals(date));
    }

    /** 비어 있는 가장 작은 카드 번호. 되돌려 돌아온 카드의 번호를 다시 쓴다. 다 썼으면 {@link NoRestCardLeftException}. */
    public int nextCardNo() {
        Set<Integer> taken = used.stream().map(RestCard::cardNo).collect(Collectors.toSet());
        return IntStream.rangeClosed(1, PER_MONTH)
                .filter(no -> !taken.contains(no))
                .findFirst()
                .orElseThrow(NoRestCardLeftException::new);
    }
}
