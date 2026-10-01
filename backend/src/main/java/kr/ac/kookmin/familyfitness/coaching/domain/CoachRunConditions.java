package kr.ac.kookmin.familyfitness.coaching.domain;

import java.math.BigDecimal;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 하루 편성 조건(FE 요청서 1장 ②). 값 객체.
 * minutes — 그날 운동 시간(분). quiet — 뛰는 동작을 뺀다. place — null 이면 장소를 가리지 않는다.
 * focusFactor — 보호자가 키워 주고 싶은 역량, null 이면 코치가 가장 낮은 요인을 고른다. withParent — 요청한 보호자도 같이 한다.
 * heightCm, weightKg: 편성 요청에 실어 온 대상의 키(cm)와 몸무게(kg)이고, 안 보냈으면 null 이다.
 * 대상에게 측정 기록이 없을 때만 AI 요청에 싣는다. 측정 기록이 있으면 기록의 값을 쓴다.
 */
public record CoachRunConditions(
        int minutes,
        boolean quiet,
        @Nullable CoachPlace place,
        @Nullable FitnessFactor focusFactor,
        boolean withParent,
        @Nullable BigDecimal heightCm,
        @Nullable BigDecimal weightKg) {
    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 60;

    public CoachRunConditions {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException("minutes 는 " + MIN_MINUTES + "~" + MAX_MINUTES + " 이다: " + minutes);
        }
    }

    /** 키와 몸무게를 싣지 않은 조건. */
    public CoachRunConditions(
            int minutes,
            boolean quiet,
            @Nullable CoachPlace place,
            @Nullable FitnessFactor focusFactor,
            boolean withParent) {
        this(minutes, quiet, place, focusFactor, withParent, null, null);
    }
}
