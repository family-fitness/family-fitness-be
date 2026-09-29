package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.fitness.domain.Certification.PeerShare;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;

/**
 * 같은 (연령대, 성별, 나이) 참가자의 인증 등급별 비율 — `fitness_grade_distribution`(AI `data/release/grade_distribution.csv`,
 * 원자료는 국민체력100 인증 결과 CRTFC_FLAG_NM). AI assess 의 peer_distribution 과 같다. 나이는 유아기만 개월이다.
 * 성인 · 청소년은 2025년 6월부터 1~6등급 체계로 바뀌어 네 비율의 합이 1 에 못 미칠 수 있다 — 그대로 준다.
 */
public class GradeDistribution {
    public static final GradeDistribution EMPTY = new GradeDistribution(Map.of());

    private final Map<Key, List<PeerShare>> shares;

    private GradeDistribution(Map<Key, List<PeerShare>> shares) {
        this.shares = shares;
    }

    private record Key(AgeGroup ageGroup, Sex sex, int age) {}

    /** 표 한 줄. */
    public record Row(AgeGroup ageGroup, Sex sex, int age, Grade grade, BigDecimal ratio) {}

    public int getSize() {
        return shares.values().stream().mapToInt(List::size).sum();
    }

    /** 1등급 · 2등급 · 3등급 · 참가 차례. 표에 없으면 빈 목록. */
    public List<PeerShare> of(AgeGroup ageGroup, Sex sex, int age) {
        return shares.getOrDefault(new Key(ageGroup, sex, age), List.of());
    }

    /** 한 사람 칸에 같은 등급이 두 줄이면 받지 않는다. */
    public static GradeDistribution of(Collection<Row> rows) {
        Map<Key, List<PeerShare>> byKey = new LinkedHashMap<>();
        for (Row row : rows) {
            List<PeerShare> same =
                    byKey.computeIfAbsent(new Key(row.ageGroup(), row.sex(), row.age()), k -> new ArrayList<>());
            if (same.stream().anyMatch(it -> it.grade() == row.grade())) {
                throw new IllegalArgumentException("등급 비율 칸이 두 줄이다: " + row);
            }
            same.add(new PeerShare(row.grade(), row.ratio()));
        }
        Map<Key, List<PeerShare>> frozen = new LinkedHashMap<>();
        byKey.forEach((key, list) -> frozen.put(
                key,
                list.stream().sorted(Comparator.comparing(PeerShare::grade)).toList()));
        return new GradeDistribution(frozen);
    }
}
