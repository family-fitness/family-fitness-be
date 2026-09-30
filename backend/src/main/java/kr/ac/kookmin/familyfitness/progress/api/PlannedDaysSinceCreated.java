package kr.ac.kookmin.familyfitness.progress.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 가족 리그 달성률의 분모 — 「잡힌 날」 가운데 <b>그 미션을 만든 날(KST)과 같거나 뒤인 날</b>만(결정 41).
 * 서는 날의 규칙은 {@link PlannedDays} 와 같고, 거기서 만들기 전 날을 뺀다. 지난 날짜로 만든 미션 · 주 중간에 승인한 제안의
 * 지난 날이 분모를 키우거나(안 한 날로 세짐), 영상만 본 지난날을 뒤늦게 해낸 날로 바꾸지 못하게 한다.
 *
 * <p>{@link PlannedDays} 와 따로 둔 까닭: 이어서 한 날은 이 거르기를 하지 않는다(결정 25 그대로). 또 {@link PlannedDays} 는
 * 시험에서 람다로 만드는 함수형 인터페이스라 메서드를 더하면 그 시험들이 깨진다. 구현은 같은 곳(coaching)이 한다.
 */
@FunctionalInterface
public interface PlannedDaysSinceCreated {
    /**
     * {@code from}~{@code to}(양끝 포함) 안에서 프로필마다 잡힌 날(만든 날 이후). 여러 프로필을 쿼리 한 번으로 읽는다
     * (리그 방 하나의 아이 전부). 잡힌 날이 없는 프로필은 결과에 없다.
     */
    Map<UUID, Set<LocalDate>> plannedDaysSinceCreated(Collection<UUID> profileIds, LocalDate from, LocalDate to);
}
