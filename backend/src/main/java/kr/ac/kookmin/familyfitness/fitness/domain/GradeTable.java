package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 부팅 시 메모리에 올리는 국민체력100 등급 기준표. 항목 하나의 등급을 AI 인증 등급(`stats/tables.py` certify)과 같은 표 ·
 * 같은 한 줄 규칙으로 낸다. 불변이라 교체는 통째로 바꾼다.
 */
public class GradeTable {
    public static final GradeTable EMPTY = new GradeTable(Map.of());

    private static final List<Grade> CERTIFIED = List.of(Grade.FIRST, Grade.SECOND, Grade.THIRD);

    private final Map<Key, List<GradeThreshold>> rows;

    private GradeTable(Map<Key, List<GradeThreshold>> rows) {
        this.rows = rows;
    }

    private record Key(AgeGroup ageGroup, Sex sex, String itemCode) {}

    public int getSize() {
        return rows.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 항목 하나의 등급. 연령대는 AI 처럼 만 나이에서 정하고, 나이 구간은 줄의 단위(유아기 개월 · 그 밖 세)로 고른다.
     * <ul>
     *   <li>1 → 2 → 3등급 차례로, 그 등급 줄이 있고 값이 통과하는 첫 등급.
     *   <li>그 사람 · 항목의 줄이 하나라도 있는데 하나도 통과하지 못하면 {@link Grade#PARTICIPATION}. 3등급 줄이 없는 항목(유소년
     *       043 · 022 등)은 2등급에 못 미치면 곧바로 참가다.
     *   <li>줄이 없으면(만 7~10세 · 어르신 · 표에 없는 항목) null.
     * </ul>
     */
    public @Nullable Grade grade(FitnessItem item, Sex sex, int ageYears, int ageMonths, BigDecimal value) {
        List<GradeThreshold> covering =
                rows.getOrDefault(new Key(AgeGroup.ofAge(ageYears), sex, item.getCode()), List.of()).stream()
                        .filter(it -> it.covers(ageYears, ageMonths))
                        .toList();
        if (covering.isEmpty()) return null;
        for (Grade grade : CERTIFIED) {
            boolean passed = covering.stream().anyMatch(it -> it.grade() == grade && it.passes(value));
            if (passed) return grade;
        }
        return Grade.PARTICIPATION;
    }

    /** 같은 (연령대, 성별, 등급, 항목)의 나이 구간이 겹치면 한 사람에게 줄이 둘이 되므로 받지 않는다. */
    public static GradeTable of(Collection<GradeThreshold> thresholds) {
        Map<Key, List<GradeThreshold>> byKey = new LinkedHashMap<>();
        for (GradeThreshold row : thresholds) {
            List<GradeThreshold> same =
                    byKey.computeIfAbsent(new Key(row.ageGroup(), row.sex(), row.itemCode()), k -> new ArrayList<>());
            for (GradeThreshold other : same) {
                boolean overlaps = other.grade() == row.grade()
                        && other.ageUnit() == row.ageUnit()
                        && row.ageFrom() <= other.ageTo()
                        && other.ageFrom() <= row.ageTo();
                if (overlaps) throw new IllegalArgumentException("등급 기준 나이 구간이 겹친다: " + other + " · " + row);
            }
            same.add(row);
        }
        Map<Key, List<GradeThreshold>> frozen = new LinkedHashMap<>();
        byKey.forEach((key, list) -> frozen.put(key, List.copyOf(list)));
        return new GradeTable(frozen);
    }
}
