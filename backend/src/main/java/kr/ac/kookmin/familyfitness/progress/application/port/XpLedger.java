package kr.ac.kookmin.familyfitness.progress.application.port;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.domain.XpEvent;

/** 경험치 원장. 넣기와 읽기만 있다 — 고치거나 지우는 메서드를 두지 않는다(결정 23). */
public interface XpLedger {
    /**
     * 같은 (프로필, 종류, 키)가 아직 없을 때만 넣는다. 넣었으면 true, 이미 있으면 아무것도 하지 않고 false.
     * 두 요청이 같은 키를 동시에 넣으면 늦은 쪽은 유니크 제약에 걸려 그 트랜잭션이 실패한다(409 CONFLICT).
     */
    boolean append(XpEvent event);

    /** 원장 합 — 지금까지 받은 경험치. */
    int totalOf(UUID profileId);

    /** 최근에 적은 줄부터 최대 {@code limit} 줄. */
    List<XpEvent> recent(UUID profileId, int limit);

    /** 그 날들(occurredOn)에 운동(칸 · 미션)으로 받은 줄 전부. */
    List<XpEvent> exerciseOn(UUID profileId, Collection<LocalDate> dates);
}
