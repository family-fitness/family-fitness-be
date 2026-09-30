package kr.ac.kookmin.familyfitness.notification.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ac.kookmin.familyfitness.activity.api.RestDayQuery;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMission;
import kr.ac.kookmin.familyfitness.coaching.api.StandingMissionQuery;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessQuery;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.notification.application.port.NotificationRepository;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import kr.ac.kookmin.familyfitness.notification.domain.NotificationRules;
import kr.ac.kookmin.familyfitness.progress.api.AchievementEarned;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 만들기 · 지우기. 메서드마다 <b>새 트랜잭션</b>이다(REQUIRES_NEW). 보통은 알림 전용 스레드(트랜잭션 없음)나 스케줄러 스레드가
 * 부르지만, 시험 프로필처럼 커밋 뒤 콜백에서 곧바로 부를 때도 원래 트랜잭션에 끼지 않게 한다. 여기서 실패해도 응원 · 칸 끝 · 미션
 * 만들기는 되돌아가지 않는다. 같은 (받는 사람, 멱등 키)는 한 번만 들어가서 다시 불러도 늘지 않는다(동시에 넣어도 DB 가 한 건만 남긴다).
 * 돌려주는 수는 새로 넣거나 지운 행 수다.
 */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class NotificationWriter {
    private final NotificationRepository notifications;
    private final ProfileQuery profiles;
    private final RestDayQuery restDays;
    private final StandingMissionQuery standingMissions;
    private final FitnessQuery fitness;
    private final ZoneId zone;

    public NotificationWriter(
            NotificationRepository notifications,
            ProfileQuery profiles,
            RestDayQuery restDays,
            StandingMissionQuery standingMissions,
            FitnessQuery fitness,
            ZoneId appZone) {
        this.notifications = notifications;
        this.profiles = profiles;
        this.restDays = restDays;
        this.standingMissions = standingMissions;
        this.fitness = fitness;
        this.zone = appZone;
    }

    /**
     * 응원 한 건 → 받은 사람에게 알림 한 건. 누가 누구에게 보내는지는 응원 kind 가 이미 정했다(identity CheerRules).
     *
     * <pre>
     * DONE   아이 → 부모  KID_DONE    「서준이 운동을 마쳤어요」 — 칸 끝 자동 알림은 따로 만들지 않는다(두 번 가지 않게)
     * THANKS 아이 → 부모  KID_THANKS  「서준이 고맙대요」
     * PRAISE 부모 → 아이  PRAISE      「은영이 스티커를 붙여 줬어요」 — 보낸 보호자의 프로필 이름
     * </pre>
     *
     * 보낸 프로필을 못 찾으면 만들지 않는다(목도 보낸 사람을 모르면 건너뛴다).
     */
    public int fromCheer(CheerSent cheer) {
        ProfileSummary sender = profiles.findSummary(cheer.fromProfileId());
        if (sender == null) return 0;
        LocalDate on = LocalDate.ofInstant(cheer.createdAt(), zone);
        Notification notification =
                switch (cheer.kind()) {
                    case DONE ->
                        Notification.kidDone(
                                cheer.toProfileId(),
                                sender.profileId(),
                                sender.name(),
                                cheer.cheerId(),
                                cheer.message(),
                                cheer.missionId(),
                                on,
                                cheer.createdAt());
                    case THANKS ->
                        Notification.kidThanks(
                                cheer.toProfileId(),
                                sender.profileId(),
                                sender.name(),
                                cheer.cheerId(),
                                cheer.stickerId(),
                                cheer.message(),
                                on,
                                cheer.createdAt());
                    case PRAISE ->
                        Notification.praise(
                                cheer.toProfileId(),
                                sender.profileId(),
                                sender.name(),
                                cheer.cheerId(),
                                cheer.stickerId(),
                                cheer.message(),
                                cheer.missionId(),
                                on,
                                cheer.createdAt());
                };
        return save(notification);
    }

    /** 새 업적 → 아이 프로필이면 한 건. 부모 프로필이 받은 업적은 알리지 않는다(목은 아이에게만). */
    public int achievement(AchievementEarned earned) {
        ProfileSummary owner = profiles.findSummary(earned.profileId());
        if (owner == null || owner.role() != ProfileRole.CHILD) return 0;
        return save(Notification.achievement(
                owner.profileId(),
                earned.code(),
                earned.title(),
                earned.description(),
                LocalDate.ofInstant(earned.earnedAt(), zone),
                earned.earnedAt()));
    }

    /**
     * 07:30 몫 — 이 가족에게 오늘 서는 미션마다, 아직 안 끝낸 참여 아이에게 한 건. 쉬는 날이면 만들지 않는다.
     * 만든 시각은 07:30({@code readyAt})이고, 07:30 뒤에 생긴 미션이면 생긴 시각이다(알림이 미션보다 앞서지 않게).
     */
    public int missionReadyForFamily(UUID familyId, LocalDate today, Instant readyAt) {
        if (isRestDay(familyId, today)) return 0;
        Map<UUID, ProfileSummary> kids = kidsOf(familyId);
        if (kids.isEmpty()) return 0;
        int made = 0;
        for (StandingMission mission : standingMissions.standingOn(familyId, today)) {
            made += missionReady(mission, kids, today, latest(readyAt, mission.createdAt()));
        }
        return made;
    }

    /** 07:30 이 지난 뒤 오늘 서는 미션이 생겼다 — 그 미션만 곧바로. 만든 시각은 미션이 생긴 시각. */
    public int missionReadyForMission(UUID missionId, LocalDate today, Instant createdAt) {
        StandingMission mission = standingMissions.standing(missionId, today);
        if (mission == null || isRestDay(mission.familyId(), today)) return 0;
        return missionReady(mission, kidsOf(mission.familyId()), today, createdAt);
    }

    /**
     * 09:00 몫 — 이 가족 아이마다 마지막 측정일에서 30일 이상 지났으면 부모 프로필 전원에게 한 건. 측정 회차(마지막 testedOn)마다
     * 한 번이라 다음 날 다시 돌아도 늘지 않고, 다시 재면 그 회차로 30일 뒤에 또 간다. 한 번도 안 잰 아이는 건너뛴다.
     */
    public int remeasureForFamily(UUID familyId, LocalDate today, Instant at) {
        List<ProfileSummary> members = profiles.summariesOfFamily(familyId);
        List<ProfileSummary> parents =
                members.stream().filter(ProfileSummary::isParent).toList();
        List<ProfileSummary> kids =
                members.stream().filter(it -> it.role() == ProfileRole.CHILD).toList();
        if (parents.isEmpty() || kids.isEmpty()) return 0;
        Map<UUID, LocalDate> lastTested = fitness.lastTestedOn(
                kids.stream().map(ProfileSummary::profileId).toList());
        int made = 0;
        for (ProfileSummary kid : kids) {
            LocalDate last = lastTested.get(kid.profileId());
            if (last == null || !NotificationRules.remeasureDue(last, today)) continue;
            for (ProfileSummary parent : parents) {
                made += save(Notification.remeasure(parent.profileId(), kid.profileId(), kid.name(), last, today, at));
            }
        }
        return made;
    }

    /**
     * 이 사람의 측정 회차가 새로 저장됐다 — 마지막 측정 회차가 아닌 회차로 만든 REMEASURE 를 부모 모두에게서 지운다. 목은 알림함을
     * 읽을 때마다 마지막 측정일로 셈하므로 다시 재면 사라진다(fe:src/mocks/notifications.ts 의 {@code days < 30} 이면 건너뜀).
     * 지난 날짜를 나중에 적어 마지막 측정일이 그대로면 그 회차의 알림은 남는다(목도 그대로 보인다).
     *
     * <p>REMEASURE 는 아이에 관해서만 생기므로 부모 · 모르는 프로필의 측정이면 쿼리를 돌리지 않는다. 지울 곳은 그 아이 가족의 부모
     * 알림함이다 — 09:00 이 보낸 사람과 같다(프로필의 가족 · 역할은 바뀌지 않는다).
     */
    public int remeasured(UUID profileId) {
        ProfileSummary measured = profiles.findSummary(profileId);
        if (measured == null || measured.role() != ProfileRole.CHILD) return 0;
        List<UUID> parentIds = profiles.summariesOfFamily(measured.familyId()).stream()
                .filter(ProfileSummary::isParent)
                .map(ProfileSummary::profileId)
                .toList();
        if (parentIds.isEmpty()) return 0;
        LocalDate last = fitness.lastTestedOn(List.of(profileId)).get(profileId);
        return notifications.deleteRemeasureAbout(
                parentIds, profileId, last == null ? null : Notification.remeasureKey(profileId, last));
    }

    /** 이 사람이 미션을 끝까지 했다 — 그 미션의 MISSION_READY 를 목록에서 뺀다(목은 끝낸 미션을 셈하지 않는다). */
    public int missionCompleted(UUID profileId, UUID missionId) {
        return notifications.deleteMissionReady(profileId, missionId);
    }

    /** 미션이 지워졌다 — 그 미션에 걸린 알림을 모두 지운다(결정 45). */
    public int missionCancelled(UUID missionId) {
        return notifications.deleteByMission(missionId);
    }

    private int missionReady(StandingMission mission, Map<UUID, ProfileSummary> kids, LocalDate today, Instant at) {
        int made = 0;
        for (UUID profileId : mission.pendingProfileIds()) {
            if (!kids.containsKey(profileId)) continue;
            made += save(Notification.missionReady(profileId, mission.missionId(), mission.title(), today, at));
        }
        return made;
    }

    private boolean isRestDay(UUID familyId, LocalDate day) {
        return restDays.restDaysBetween(familyId, day, day).contains(day);
    }

    private Map<UUID, ProfileSummary> kidsOf(UUID familyId) {
        return profiles.summariesOfFamily(familyId).stream()
                .filter(it -> it.role() == ProfileRole.CHILD)
                .collect(Collectors.toMap(ProfileSummary::profileId, Function.identity()));
    }

    private int save(Notification notification) {
        return notifications.insertIfAbsent(notification) ? 1 : 0;
    }

    private static Instant latest(Instant a, Instant b) {
        return b.isAfter(a) ? b : a;
    }
}
