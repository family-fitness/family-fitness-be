package kr.ac.kookmin.familyfitness.activity.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.application.port.RestCardRepository;
import kr.ac.kookmin.familyfitness.activity.domain.RestCard;
import org.jspecify.annotations.Nullable;

/**
 * DB 의 두 유니크 인덱스(같은 날 · 같은 카드 번호)를 흉내 내는 메모리 저장소.
 * {@link #beforeNextInsert} 에 넣은 일은 넣은 차례대로 insert 한 번마다 하나씩, 그 insert 직전에 돈다 —
 * 다른 요청이 먼저 넣은 경우를 만든다. 여러 번 넣으면 연달아 뺏기는 경우가 된다.
 */
class InMemoryRestCardRepository implements RestCardRepository {
    final List<RestCard> rows = new ArrayList<>();
    int insertCalls;
    private final Queue<Runnable> beforeInserts = new ArrayDeque<>();

    void beforeNextInsert(Runnable task) {
        beforeInserts.add(task);
    }

    @Override
    public List<RestCard> findByMonth(UUID familyId, YearMonth month) {
        return rows.stream()
                .filter(card ->
                        card.familyId().equals(familyId) && card.restMonth().equals(month))
                .sorted(Comparator.comparing(RestCard::restDate))
                .toList();
    }

    @Override
    public boolean insert(RestCard card) {
        insertCalls++;
        @Nullable Runnable task = beforeInserts.poll();
        if (task != null) task.run();
        boolean taken = rows.stream()
                .filter(row -> row.familyId().equals(card.familyId()))
                .anyMatch(row -> row.restDate().equals(card.restDate())
                        || (row.restMonth().equals(card.restMonth()) && row.cardNo() == card.cardNo()));
        if (taken) return false;
        rows.add(card);
        return true;
    }

    @Override
    public boolean delete(UUID familyId, LocalDate restDate) {
        return rows.removeIf(
                card -> card.familyId().equals(familyId) && card.restDate().equals(restDate));
    }

    @Override
    public List<LocalDate> restDatesBetween(UUID familyId, LocalDate from, LocalDate to) {
        return rows.stream()
                .filter(card -> card.familyId().equals(familyId))
                .map(RestCard::restDate)
                .filter(date -> !date.isBefore(from) && !date.isAfter(to))
                .sorted()
                .toList();
    }

    @Override
    public Map<UUID, List<LocalDate>> restDatesOf(Collection<UUID> familyIds, LocalDate from, LocalDate to) {
        Map<UUID, List<LocalDate>> out = new HashMap<>();
        for (UUID familyId : familyIds) {
            List<LocalDate> dates = restDatesBetween(familyId, from, to);
            if (!dates.isEmpty()) out.put(familyId, dates);
        }
        return out;
    }
}
