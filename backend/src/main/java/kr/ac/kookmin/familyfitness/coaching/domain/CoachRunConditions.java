package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;

/**
 * 하루 편성 조건(FE 요청서 1장 ②). 값 객체.
 * minutes — 그날 운동 시간(분). quiet — 뛰는 동작을 뺀다. place — null 이면 장소를 가리지 않는다.
 * focusFactor — 부모가 고른 키울 힘, null 이면 코치가 가장 낮은 요인을 고른다. withParent — 요청한 보호자도 같이 한다.
 */
public record CoachRunConditions(
        int minutes,
        boolean quiet,
        @Nullable CoachPlace place,
        @Nullable FitnessFactor focusFactor,
        boolean withParent) {
    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 60;

    public CoachRunConditions {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException("minutes 는 " + MIN_MINUTES + "~" + MAX_MINUTES + " 이다: " + minutes);
        }
    }
}
