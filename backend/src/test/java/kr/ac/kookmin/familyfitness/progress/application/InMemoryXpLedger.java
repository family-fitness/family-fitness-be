package kr.ac.kookmin.familyfitness.progress.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.application.port.XpLedger;
import kr.ac.kookmin.familyfitness.progress.domain.XpEvent;

/** 원장의 가짜 — 같은 (프로필, 종류, 키)는 한 번만 넣는다. */
class InMemoryXpLedger implements XpLedger {
    final List<XpEvent> rows = new ArrayList<>();

    @Override
    public boolean append(XpEvent event) {
        boolean exists = rows.stream()
                .anyMatch(it -> it.profileId().equals(event.profileId())
                        && it.kind() == event.kind()
                        && it.sourceKey().equals(event.sourceKey()));
        if (exists) return false;
        rows.add(event);
        return true;
    }

    @Override
    public int totalOf(UUID profileId) {
        return of(profileId).stream().mapToInt(XpEvent::amount).sum();
    }

    @Override
    public List<XpEvent> recent(UUID profileId, int limit) {
        return of(profileId).stream()
                .sorted(Comparator.comparing(XpEvent::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<XpEvent> exerciseOn(UUID profileId, Collection<LocalDate> dates) {
        return of(profileId).stream()
                .filter(it -> it.kind().isExercise() && dates.contains(it.occurredOn()))
                .toList();
    }

    List<XpEvent> of(UUID profileId) {
        return rows.stream().filter(it -> it.profileId().equals(profileId)).toList();
    }
}
