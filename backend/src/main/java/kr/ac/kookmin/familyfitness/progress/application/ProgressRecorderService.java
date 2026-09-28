package kr.ac.kookmin.familyfitness.progress.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.progress.api.ProgressRecorder;
import kr.ac.kookmin.familyfitness.progress.api.SessionDone;
import kr.ac.kookmin.familyfitness.progress.application.port.AchievementStore;
import kr.ac.kookmin.familyfitness.progress.application.port.XpLedger;
import kr.ac.kookmin.familyfitness.progress.domain.Achievement;
import kr.ac.kookmin.familyfitness.progress.domain.MoveFacts;
import kr.ac.kookmin.familyfitness.progress.domain.MoveWindow;
import kr.ac.kookmin.familyfitness.progress.domain.XpEvent;
import kr.ac.kookmin.familyfitness.progress.domain.XpKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 칸 끝 적립({@link ProgressRecorder}). 부르는 쪽(coaching)의 트랜잭션에 함께 든다 — 칸 기록이 되돌려지면 적립도 되돌려진다.
 *
 * <p>차례: 칸 +5 → (끝까지 했고 서버가 잰 확인이면) 미션 +20 → 움직임 업적 판정. 업적은 같은 칸을 다시 보내도 다시 판정한다
 * (이미 받은 업적은 저장소가 그대로 둔다).
 */
@Service
@Transactional
public class ProgressRecorderService implements ProgressRecorder {
    private final XpLedger ledger;
    private final AchievementStore achievements;
    private final MoveHistory history;
    private final ActivityQuery activity;
    private final ProfileQuery profiles;
    private final Clock clock;

    public ProgressRecorderService(
            XpLedger ledger,
            AchievementStore achievements,
            MoveHistory history,
            ActivityQuery activity,
            ProfileQuery profiles,
            Clock clock) {
        this.ledger = ledger;
        this.achievements = achievements;
        this.history = history;
        this.activity = activity;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Override
    public int sessionDone(SessionDone done) {
        Instant now = clock.instant();
        int gained = 0;
        if (ledger.append(XpEvent.sessionDone(done, now))) gained += XpKind.SESSION_DONE.amount();
        if (done.countsAsMissionDone() && ledger.append(XpEvent.missionDone(done, now))) {
            gained += XpKind.MISSION_DONE.amount();
        }
        judgeMoves(done);
        return gained;
    }

    /**
     * 움직임으로 받는 업적. 끝낸 사람은 조건을 채운 업적을 다 받는다. 끝낸 사람이 보호자면, 그날 움직인 다른 식구도
     * 「가족과 함께」 를 받는다 — 아이가 먼저 하고 부모가 저녁에 해도 아이가 받아야 한다.
     */
    private void judgeMoves(SessionDone done) {
        List<ProfileSummary> family = profiles.summariesOfFamily(done.familyId());
        MoveWindow window = history.load(done.profileId(), done.familyId(), done.completedOn())
                .withMoved(done.completedOn());
        MoveFacts facts = new MoveFacts(
                window.streakDays(),
                activity.verifiedSummary(done.profileId()).totalMinutes(),
                fullSetOn(done),
                window.movedOnWeekend(),
                movedWithOtherParent(family, done.profileId(), window));
        facts.reached().forEach(it -> achievements.grant(done.profileId(), it, done.completedAt()));

        if (!isParent(family, done.profileId())) return;
        for (ProfileSummary member : family) {
            if (member.profileId().equals(done.profileId())) continue;
            if (!history.movedDays(member.profileId(), done.completedOn(), done.completedOn())
                    .isEmpty()) {
                achievements.grant(member.profileId(), Achievement.TOGETHER, done.completedAt());
            }
        }
    }

    /** 그날 끝낸 칸에 준비 · 본 · 정리가 다 있는가(목 FULL_SET — 여러 운동에 걸쳐도 그날이면 된다). */
    private boolean fullSetOn(SessionDone done) {
        Set<SessionDone.Phase> phases = ledger.exerciseOn(done.profileId(), List.of(done.completedOn())).stream()
                .map(XpEvent::phase)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SessionDone.Phase.class)));
        return phases.containsAll(EnumSet.allOf(SessionDone.Phase.class));
    }

    /** 이 사람이 움직인 날 중 다른 보호자(PARENT)도 움직인 날이 있는가(목 TOGETHER). */
    private boolean movedWithOtherParent(List<ProfileSummary> family, UUID profileId, MoveWindow window) {
        return family.stream()
                .filter(it -> it.isParent() && !it.profileId().equals(profileId))
                .anyMatch(parent -> history.movedDays(parent.profileId(), window.from(), window.today()).stream()
                        .anyMatch(window.moved()::contains));
    }

    private static boolean isParent(List<ProfileSummary> family, UUID profileId) {
        return family.stream().anyMatch(it -> it.profileId().equals(profileId) && it.isParent());
    }
}
