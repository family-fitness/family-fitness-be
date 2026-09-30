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
     * 같은 키(profile_id, kind, source_key)가 없을 때만 넣는다 — 판단은 DB 의 ON CONFLICT DO NOTHING 이 한다
     * ({@link XpEventJpaRepository#insertIfAbsent}). 같은 트랜잭션에서 앞서 넣은 행과도, 동시에 온 다른 요청이 넣은 행과도 겹치면
     * false 이고 예외는 없다. 그래서 두 보호자가 같은 운동에 동시에 스티커를 붙여도 늦은 쪽 응원이 되돌려지지 않는다(QA SA-12).
     */
    @Override
    @Transactional
    public boolean append(XpEvent event) {
        SessionDone.Phase phase = event.phase();
        FitnessFactor factor = event.factor();
        return jpa.insertIfAbsent(
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
                        event.createdAt())
                == 1;
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
