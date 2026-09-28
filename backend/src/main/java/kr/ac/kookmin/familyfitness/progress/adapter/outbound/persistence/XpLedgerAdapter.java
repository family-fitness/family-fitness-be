package kr.ac.kookmin.familyfitness.progress.adapter.outbound.persistence;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.progress.application.port.XpLedger;
import kr.ac.kookmin.familyfitness.progress.domain.XpEvent;
import kr.ac.kookmin.familyfitness.progress.domain.XpKind;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link XpLedger} 의 JPA 구현. */
@Repository
@Transactional(readOnly = true)
public class XpLedgerAdapter implements XpLedger {
    private static final List<String> EXERCISE_KINDS = Arrays.stream(XpKind.values())
            .filter(XpKind::isExercise)
            .map(Enum::name)
            .toList();

    private final XpEventJpaRepository jpa;

    public XpLedgerAdapter(XpEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * 같은 키가 있는지 먼저 본다. 같은 트랜잭션에서 앞서 넣은 행도 보인다(조회 전에 JPA 가 미룬 insert 를 내보낸다).
     * 동시에 들어온 두 요청이 함께 검사를 지나치면 늦은 쪽의 insert 가 유니크 제약(uq_progress_xp_events_source)에 걸린다.
     */
    @Override
    @Transactional
    public boolean append(XpEvent event) {
        if (jpa.existsByProfileIdAndKindAndSourceKey(
                event.profileId(), event.kind().name(), event.sourceKey())) {
            return false;
        }
        jpa.save(toEntity(event));
        return true;
    }

    @Override
    public int totalOf(UUID profileId) {
        return Math.toIntExact(jpa.sumAmount(profileId));
    }

    @Override
    public List<XpEvent> recent(UUID profileId, int limit) {
        return jpa.findByProfileIdOrderByCreatedAtDescIdDesc(profileId, Limit.of(limit)).stream()
                .map(XpLedgerAdapter::toDomain)
                .toList();
    }

    @Override
    public List<XpEvent> exerciseOn(UUID profileId, Collection<LocalDate> dates) {
        if (dates.isEmpty()) return List.of();
        return jpa.findByProfileIdAndKindInAndOccurredOnIn(profileId, EXERCISE_KINDS, dates).stream()
                .map(XpLedgerAdapter::toDomain)
                .toList();
    }

    private static XpEventEntity toEntity(XpEvent event) {
        SessionDone.Phase phase = event.phase();
        FitnessFactor factor = event.factor();
        return new XpEventEntity(
                event.id(),
                event.profileId(),
                event.kind().name(),
                event.sourceKey(),
                event.amount(),
                event.fromProfileId(),
                event.missionId(),
                phase == null ? null : phase.name(),
                factor == null ? null : factor.getLabel(),
                event.occurredOn(),
                event.createdAt());
    }

    private static XpEvent toDomain(XpEventEntity e) {
        String phase = e.getPhase();
        String factor = e.getFactor();
        return new XpEvent(
                e.getId(),
                e.getProfileId(),
                XpKind.valueOf(e.getKind()),
                e.getSourceKey(),
                e.getAmount(),
                e.getFromProfileId(),
                e.getMissionId(),
                phase == null ? null : SessionDone.Phase.valueOf(phase),
                factor == null ? null : FitnessFactor.fromLabel(factor),
                e.getOccurredOn(),
                e.getCreatedAt());
    }
}
