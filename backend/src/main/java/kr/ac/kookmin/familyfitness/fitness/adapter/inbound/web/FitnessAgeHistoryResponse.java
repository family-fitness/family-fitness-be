package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.time.LocalDate;
import java.util.List;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessAgeService;
import org.jspecify.annotations.Nullable;

/**
 * 체력 나이 이력. 측정 회차마다 그때의 나이와 체력 나이. {@code tests} 는 측정일이 늦은 것부터다.
 * {@code fitnessAge} · {@code ageGap} 은 그 회차의 status 가 OK 일 때만 있다. {@code itemCount} 는 셈에 쓴 항목 수다.
 */
public record FitnessAgeHistoryResponse(List<Entry> tests) {
    public record Entry(
            LocalDate testedOn,
            int age,
            FitnessAgeService.Status status,
            @Nullable Integer fitnessAge,
            @Nullable Integer ageGap,
            int itemCount) {}

    public static FitnessAgeHistoryResponse of(List<FitnessAgeService.HistoryEntry> entries) {
        return new FitnessAgeHistoryResponse(entries.stream()
                .map(it -> new Entry(
                        it.testedOn(),
                        it.ageAtTest(),
                        it.status(),
                        it.fitnessAge(),
                        it.fitnessAge() == null ? null : it.fitnessAge() - it.ageAtTest(),
                        it.itemCount()))
                .toList());
    }
}
