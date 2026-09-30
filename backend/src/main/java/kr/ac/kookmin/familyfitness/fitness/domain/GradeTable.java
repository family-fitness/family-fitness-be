package kr.ac.kookmin.familyfitness.fitness.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;

/**
 * 부팅 시 메모리에 올리는 국민체력100 등급 기준표. 한 사람의 인증 등급을 AI `stats/tables.py` certify 와 한 줄씩 같게
 * 판정한다({@link #judge}). 불변이라 교체는 통째로 바꾼다.
 */
public class GradeTable {
    public static final GradeTable EMPTY = new GradeTable(Map.of());

    /** 판정하는 등급 차례. 참가는 기준표에 줄이 없다. */
    public static final List<Grade> CERTIFIED = List.of(Grade.FIRST, Grade.SECOND, Grade.THIRD);

    /** 심폐지구력은 두 시험 중 하나만 재면 된다(AI certify 의 alternatives). */
    public static final Set<String> ALTERNATIVES = Set.of("035", "037");

    private final Map<Key, List<GradeThreshold>> rows;

    private GradeTable(Map<Key, List<GradeThreshold>> rows) {
        this.rows = rows;
    }

    private record Key(AgeGroup ageGroup, Sex sex, Grade grade) {}

    public int getSize() {
        return rows.values().stream().mapToInt(List::size).sum();
    }

    /** 그 사람 · 등급의 기준 줄. {@code age} 는 줄의 단위(유아기 개월 · 그 밖 만 나이)로 본 나이다. */
    public List<GradeThreshold> rows(AgeGroup ageGroup, Sex sex, Grade grade, int age) {
        return rows.getOrDefault(new Key(ageGroup, sex, grade), List.of()).stream()
                .filter(it -> it.covers(age))
                .toList();
    }

    /**
     * 판정 결과. {@code grade} 는 AI certify 의 반환값과 같다. {@code missing} 은 줄이 있는데 보는 항목을 다 재지 않아 판정하지
     * 못한 등급마다 모자란 항목 코드다(035 · 037 은 둘 다 안 쟀을 때 줄에 있는 쪽을 모두 넣는다).
     */
    public record Judgement(@Nullable Grade grade, boolean hasCriteria, Map<Grade, Set<String>> missing) {
        public Judgement {
            missing = Map.copyOf(missing);
        }
    }

    /**
     * AI certify 를 옮긴 것. 1 → 2 → 3등급 차례로, 그 등급 줄이 보는 항목을 전부 쟀으면 판정하고 전부 넘으면 그 등급이다.
     * 하나라도 안 쟀으면 그 등급으로는 판정하지 않는다(집에서 두어 개만 잰 사람을 맨 아래로 내리지 않으려는 것).
     * 한 등급이라도 판정했는데 다 못 넘으면 참가, 한 등급도 판정하지 못했으면 null.
     * 035 · 037 은 줄에 있으면 하나라도 재야 하고, 잰 것은 모두 넘어야 한다.
     *
     * @param values 항목 코드 → 값. 신체조성(003 체지방률 · 018 BMI · 042 허리둘레-신장비)도 여기 들어 있어야 한다
     */
    public Judgement judge(AgeGroup ageGroup, Sex sex, int age, Map<String, BigDecimal> values) {
        boolean judged = false;
        boolean hasCriteria = false;
        Map<Grade, Set<String>> missing = new EnumMap<>(Grade.class);
        for (Grade grade : CERTIFIED) {
            List<GradeThreshold> gradeRows = rows(ageGroup, sex, grade, age);
            if (gradeRows.isEmpty()) continue;
            hasCriteria = true;
            Map<String, GradeThreshold> byItem = new LinkedHashMap<>();
            for (GradeThreshold row : gradeRows) byItem.put(row.itemCode(), row);
            Set<String> lacking = new TreeSet<>();
            Set<String> alternatives = new TreeSet<>(byItem.keySet());
            alternatives.retainAll(ALTERNATIVES);
            if (!alternatives.isEmpty() && alternatives.stream().noneMatch(values::containsKey)) {
                lacking.addAll(alternatives);
            }
            for (String code : byItem.keySet()) {
                if (!ALTERNATIVES.contains(code) && !values.containsKey(code)) lacking.add(code);
            }
            if (!lacking.isEmpty()) {
                missing.put(grade, lacking);
                continue;
            }
            judged = true;
            boolean passed = byItem.entrySet().stream()
                    .filter(it -> values.containsKey(it.getKey()))
                    .allMatch(it -> it.getValue().passes(values.get(it.getKey())));
            if (passed) return new Judgement(grade, true, missing);
        }
        return new Judgement(judged ? Grade.PARTICIPATION : null, hasCriteria, missing);
    }

    /** 같은 (연령대, 성별, 등급, 항목)의 나이 구간이 겹치면 한 사람에게 줄이 둘이 되므로 받지 않는다. */
    public static GradeTable of(Collection<GradeThreshold> thresholds) {
        Map<Key, List<GradeThreshold>> byKey = new LinkedHashMap<>();
        for (GradeThreshold row : thresholds) {
            List<GradeThreshold> same =
                    byKey.computeIfAbsent(new Key(row.ageGroup(), row.sex(), row.grade()), k -> new ArrayList<>());
            for (GradeThreshold other : same) {
                boolean overlaps = other.itemCode().equals(row.itemCode())
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
