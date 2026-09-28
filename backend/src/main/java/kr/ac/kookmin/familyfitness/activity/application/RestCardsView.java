package kr.ac.kookmin.familyfitness.activity.application;

import java.time.LocalDate;
import java.util.List;
import kr.ac.kookmin.familyfitness.activity.domain.RestCardMonth;

/**
 * 한 달 치 쉬는 날 카드. GET · POST · DELETE 가 모두 이 모양으로 답한다(FE 요청서 3장 「쉬는 날 카드」).
 *
 * @param month 「YYYY-MM」
 * @param perMonth 한 달에 주는 장수(2)
 * @param left 남은 장수
 * @param days 쉬는 날(오름차순)
 */
public record RestCardsView(String month, int perMonth, int left, List<LocalDate> days) {
    public static RestCardsView of(RestCardMonth month) {
        return new RestCardsView(month.month().toString(), RestCardMonth.PER_MONTH, month.left(), month.days());
    }
}
