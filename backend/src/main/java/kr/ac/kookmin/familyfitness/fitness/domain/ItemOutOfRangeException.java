package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 항목 값이 `GET /fitness/items` 가 알려 준 범위({@link ValueRange}) 밖이다. 호출자 입력 오류라 400. */
public class ItemOutOfRangeException extends DomainException {
    public ItemOutOfRangeException(String itemCode, BigDecimal value, ValueRange range) {
        super(
                "ITEM_OUT_OF_RANGE",
                ErrorKind.BAD_REQUEST,
                "측정값이 범위(" + range.min() + "~" + range.max() + ") 밖입니다: " + itemCode + "=" + value.toPlainString());
    }
}
