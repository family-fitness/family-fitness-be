package kr.ac.kookmin.familyfitness.progress.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.progress.application.port.XpLedger;
import kr.ac.kookmin.familyfitness.progress.domain.Achievement;
import kr.ac.kookmin.familyfitness.progress.domain.XpEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 알린 일로 경험치를 쌓는다 — 칭찬 스티커(identity)와 다시 재기(fitness).
 *
 * <p><b>발행한 쪽과 같은 트랜잭션에서 동기로 듣는다</b>({@code @EventListener}). 커밋 뒤({@code AFTER_COMMIT})에 받으면
 * 이벤트 저장소(spring-modulith 이벤트 발행 기록)가 없어 적립이 실패했을 때 조용히 빠지고, 되살릴 길이 없다. 원장은 줄지 않는 값이라
 * 빠진 적립도 영영 빠진 채로 남는다. 같은 트랜잭션이면 적립과 원래 일(응원 · 측정)이 함께 저장되거나 함께 되돌려지고, FE 가
 * 곧바로 다시 읽는 레벨(fe:src/lib/api/queries.ts refreshProgress)에도 이미 반영돼 있다. 대신 적립이 실패하면 응원 · 측정도
 * 실패한다 — 적립은 한 줄 넣기라 그 실패는 DB 장애뿐이고, 그때는 응원 · 측정 저장도 어차피 실패한다.
 */
@Component
public class ProgressEventListener {
    private final XpLedger ledger;
    private final AchievementAwards achievements;
    private final Clock clock;
    private final ZoneId zone;

    public ProgressEventListener(XpLedger ledger, AchievementAwards achievements, Clock clock, ZoneId appZone) {
        this.ledger = ledger;
        this.achievements = achievements;
        this.clock = clock;
        this.zone = appZone;
    }

    /**
     * 부모가 아이에게 붙인 칭찬 스티커(PRAISE · stickerId 있음)만 +10(결정 24). 같은 운동(missionId)에는 한 번,
     * 운동에 붙지 않은 스티커는 한 장마다. 고마워요(THANKS) · 알리기(DONE)는 주지 않는다. 첫 칭찬 스티커면 업적 「첫 스티커」.
     */
    @EventListener
    @Transactional
    public void on(CheerSent cheer) {
        if (cheer.kind() != CheerKind.PRAISE || cheer.stickerId() == null) return;
        LocalDate receivedOn = LocalDate.ofInstant(cheer.createdAt(), zone);
        ledger.append(XpEvent.sticker(
                cheer.toProfileId(),
                cheer.fromProfileId(),
                cheer.missionId(),
                cheer.cheerId(),
                receivedOn,
                clock.instant()));
        achievements.grant(cheer.toProfileId(), Achievement.FIRST_STICKER, cheer.createdAt());
    }

    /**
     * testedOn 이 가장 이른 회차 하나를 뺀 회차마다 +20(결정 27, fe:src/mocks/progress.ts 의 {@code tests.slice(0, -1)}).
     * 적립 대상은 이벤트가 알려 준 「새로 다시 잰 회차」 이고, 원장 키는 그 회차 id, occurred_on 은 그 회차의 testedOn 이다.
     * 지난 날짜를 나중에 적어 가장 이른 회차가 바뀌면 그때까지 가장 이르던 회차가 이때 +20 을 받는다.
     * 처음 다시 잰 회차가 생기면 업적 「다시 측정」.
     */
    @EventListener
    @Transactional
    public void on(FitnessTestRegistered test) {
        FitnessTestRegistered.Round remeasured = test.remeasured();
        if (remeasured == null) return;
        Instant now = clock.instant();
        ledger.append(XpEvent.remeasure(test.profileId(), remeasured.fitnessTestId(), remeasured.testedOn(), now));
        achievements.grant(test.profileId(), Achievement.REMEASURE, now);
    }
}
