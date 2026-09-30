package kr.ac.kookmin.familyfitness.progress.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivityQuery;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.progress.application.port.AchievementStore;
import kr.ac.kookmin.familyfitness.progress.application.port.XpLedger;
import kr.ac.kookmin.familyfitness.progress.domain.Achievement;
import kr.ac.kookmin.familyfitness.progress.domain.LevelCurve;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 레벨 · 경험치 · 업적 · 이어서 한 날 읽기. 같은 가족이면 누구나 읽는다(부모 프로필도 — FE 요청서 0-3).
 *
 * <pre>
 * xp          원장 합(창 없이 전부)
 * level       xp 의 구간({@link LevelCurve})
 * streakDays  이어서 한 날 — 잡힌 날 기준(결정 25), 읽을 때마다 센다(끊기면 줄어드는 값이라 저장하지 않는다)
 * activeDays  서버가 잰 분이 있는 날 수(기간 제한 없음)
 * achievements 받은 업적(저장된 것)과 아직인 업적 — 열두 개 전부
 * recentXp    최근 경험치 다섯 줄({@link RecentXp}). 스티커 줄의 문장(전환기 reason)은 줄의 주인이 붙인 사람을 부르는 말로 짓는다
 * </pre>
 */
@Service
@Transactional(readOnly = true)
public class ProgressQueryService {
    /** 최근 줄을 고르려고 읽는 원장 행 수. 한 줄이 여러 행을 묶어도 다섯 줄이 차기에 넉넉하다. */
    static final int RECENT_ROWS = 100;

    private final XpLedger ledger;
    private final AchievementStore achievements;
    private final MoveHistory history;
    private final ActivityQuery activity;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profiles;
    private final Clock clock;
    private final ZoneId zone;

    public ProgressQueryService(
            XpLedger ledger,
            AchievementStore achievements,
            MoveHistory history,
            ActivityQuery activity,
            FamilyAccess familyAccess,
            ProfileQuery profiles,
            Clock clock,
            ZoneId appZone) {
        this.ledger = ledger;
        this.achievements = achievements;
        this.history = history;
        this.activity = activity;
        this.familyAccess = familyAccess;
        this.profiles = profiles;
        this.clock = clock;
        this.zone = appZone;
    }

    /** 같은 가족이 아니면 403 NOT_SAME_FAMILY, 없는 프로필이면 404(identity 예외 그대로). */
    public ProgressView view(UUID userId, UUID profileId) {
        ProfileSummary target = familyAccess.requireSameFamilyAsProfile(userId, profileId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int xp = ledger.totalOf(profileId);
        int level = LevelCurve.levelOf(xp);
        return new ProgressView(
                profileId,
                level,
                xp,
                LevelCurve.floorOf(level),
                LevelCurve.nextFloorOf(level),
                history.load(profileId, target.familyId(), today).streakDays(),
                activity.verifiedSummary(profileId).activeDays(),
                achievementsOf(profileId),
                RecentXp.linesOf(
                        ledger.recent(profileId, RECENT_ROWS),
                        days -> ledger.exerciseOn(profileId, days),
                        senders -> callNames(target, senders)));
    }

    /**
     * 스티커를 붙인 사람들의 프로필 이름. 보호자도 「엄마」 · 「아빠」 가 아니라 이름으로 부른다(누가 읽든 같다).
     * 지금 가족에 없는 사람은 빠진다 — 문장은 「가족」 으로 부른다.
     */
    private Map<UUID, String> callNames(ProfileSummary target, Set<UUID> senders) {
        Map<UUID, String> calls = new HashMap<>();
        for (ProfileSummary member : profiles.summariesOfFamily(target.familyId())) {
            if (senders.contains(member.profileId())) {
                calls.put(member.profileId(), member.name());
            }
        }
        return calls;
    }

    private List<AchievementView> achievementsOf(UUID profileId) {
        Map<Achievement, Instant> earned = achievements.earnedOf(profileId);
        return Arrays.stream(Achievement.values())
                .map(it -> new AchievementView(it.code(), it.title(), it.description(), earned.get(it)))
                .toList();
    }
}
