package kr.ac.kookmin.familyfitness.notification.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.notification.domain.Notification;
import org.jspecify.annotations.Nullable;

/** 알림 저장소(notifications). */
public interface NotificationRepository {
    /**
     * 같은 (받는 사람, 멱등 키)가 없을 때만 넣고 true. 있으면 아무것도 하지 않고 false.
     * 두 트랜잭션이 같은 키를 함께 넣으면 늦은 쪽은 유니크 제약(ux_notifications_dedupe)에 걸려 그 트랜잭션이 실패한다.
     */
    boolean insertIfAbsent(Notification notification);

    /**
     * 알림함에 보이는 것 가운데 최신 {@code limit} 건, 만든 시각이 늦은 것부터.
     * MISSION_READY 는 {@code readyOn} 의 것만, ACHIEVEMENT 는 {@code achievementsSince} 이후에 받은 것만 보인다.
     */
    List<Notification> latest(UUID profileId, LocalDate readyOn, LocalDate achievementsSince, int limit);

    /** 안 읽은 것을 읽음으로. {@code upTo} 가 있으면 만든 시각이 그 시각과 같거나 앞선 것만. 바꾼 행 수. */
    int markRead(UUID profileId, @Nullable Instant upTo, Instant readAt);

    /** 이 사람의 이 미션 MISSION_READY 를 지운다. 지운 행 수. */
    int deleteMissionReady(UUID profileId, UUID missionId);

    /** 이 미션에 걸린 알림을 모두 지운다. 지운 행 수. */
    int deleteByMission(UUID missionId);
}
