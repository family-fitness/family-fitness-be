package kr.ac.kookmin.familyfitness.fitness.domain;

import java.util.Arrays;
import java.util.List;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;

/**
 * 한 (연령대, 성별, 나이, 항목)의 또래 분포 — `fitness_value_quantiles` 한 줄(AI `data/release/value_quantiles.csv`).
 * {@code age} 는 유아기만 개월, 나머지는 만 나이다. {@code quantiles} 는 0~100 백분위 101칸이고 오름차순이다.
 * {@code n} 은 표본 수 — 30 에 못 미치면 백분위를 내지 않는다({@link PeerTable#MIN_SAMPLE}).
 */
public record PeerQuantiles(AgeGroup ageGroup, Sex sex, int age, String itemCode, int n, List<Double> quantiles) {
    public static final int SIZE = 101;

    public PeerQuantiles {
        quantiles = List.copyOf(quantiles);
        if (quantiles.size() != SIZE) {
            throw new IllegalArgumentException("분위수가 " + SIZE + "칸이 아니다: " + quantiles.size());
        }
        for (int i = 1; i < SIZE; i++) {
            // AI 는 np.searchsorted 로 자리를 찾는다 — 정렬돼 있지 않으면 답이 달라진다
            if (quantiles.get(i - 1) > quantiles.get(i)) {
                throw new IllegalArgumentException(
                        "분위수가 오름차순이 아니다: " + ageGroup + " " + sex + " " + age + " " + itemCode);
            }
        }
    }

    /** 표에 적힌 글자(0~100 백분위 값을 ; 로 이은 것)를 AI 의 np.fromstring(sep=";") 처럼 읽는다. */
    public static List<Double> parse(String text) {
        return Arrays.stream(text.split(";")).map(Double::valueOf).toList();
    }
}
