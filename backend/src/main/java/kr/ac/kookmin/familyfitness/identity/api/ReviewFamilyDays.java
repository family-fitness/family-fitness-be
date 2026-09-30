package kr.ac.kookmin.familyfitness.identity.api;

import java.util.List;

/**
 * 체험 가족이 지난 2주 동안 어떻게 지냈는지. 날은 모두 「며칠 전」 으로 적고, 오늘(0)을 빼면 1~{@link #HISTORY_DAYS} 이다.
 * {@link ReviewFamilyCreated} 를 듣는 모듈이 이 표 하나를 같이 보고 기록을 넣는다. 한 모듈만 날을 바꾸면 캘린더, 연속 기록,
 * 리그 달성률이 서로 어긋나므로 날은 여기서만 고친다.
 *
 * <pre>
 * 활동(activity)   {@link #REST_DAY} 에 쉬는 날 카드를 쓴다. 가장 먼저 넣어야 연속 기록과 업적이 그날을 쉬는 날로 센다
 * 측정(fitness)    아이는 {@link #FIRST_TEST_DAY} 와 사흘 전 두 번, 보호자는 {@link #PARENT_TEST_DAY} 에 한 번
 * 운동(coaching)   쉬는 날만 빼고 날마다 아이마다 미션 하나. {@link #FAMILY_DAYS} 는 네 식구가 함께 하는 미션 하나로 대신한다.
 *                  빠진 날에는 미션만 서고 아무 칸도 끝내지 않는다. 오늘 하윤 미션은 끝내지 않은 채로 둔다
 * 응원(identity)   {@link #PRAISE_DAYS} 저녁에 엄마가 칭찬 스티커를 보내고, 몇 번은 아이가 고마워요로 답한다
 * </pre>
 */
public final class ReviewFamilyDays {
    /** 오늘 앞으로 며칠까지 기록을 넣는가. */
    public static final int HISTORY_DAYS = 13;

    /** 쉬는 날 카드를 쓴 날. 이날은 미션도 활동도 없다. */
    public static final int REST_DAY = 5;

    /** 네 식구가 함께 한 미션이 선 날. */
    public static final List<Integer> FAMILY_DAYS = List.of(12, 8, 2);

    /** 하윤이 미션을 받고도 하지 못한 날. 연속 기록이 여기서 끊긴다. */
    public static final int YOUTH_MISSED_DAY = 10;

    /** 서준이 미션을 받고도 하지 못한 날. */
    public static final int TODDLER_MISSED_DAY = 9;

    /** 아이들이 처음 잰 날. 사흘 전에 다시 쟀다. */
    public static final int FIRST_TEST_DAY = 30;

    /** 엄마, 아빠가 잰 날. */
    public static final int PARENT_TEST_DAY = 11;

    /** 엄마가 아이에게 칭찬 스티커를 보낸 날. 두 아이 모두 운동을 끝낸 날만 고른다. */
    public static final List<Integer> PRAISE_DAYS = List.of(8, 6, 3, 1);

    private ReviewFamilyDays() {}
}
