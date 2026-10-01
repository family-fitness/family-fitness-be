package kr.ac.kookmin.familyfitness.notification.application.port;

import java.util.Collection;
import java.util.UUID;

/** 탈퇴, 구성원 내보내기, 동의 철회 때 알림을 지운다. 부르는 쪽 트랜잭션 안에서만 돈다. */
public interface NotificationErasureRepository {
    /** 이 프로필들이 받은 알림, 이 프로필들에 관한 알림, 이 프로필들이 보낸 알림을 지운다. */
    void eraseProfiles(Collection<UUID> profileIds);

    /** 이 응원으로 만든 알림을 지운다(응원을 지우기 전에). */
    void eraseCheers(Collection<UUID> cheerIds);

    /** 이 미션들의 알림을 지운다. */
    void eraseMissions(Collection<UUID> missionIds);
}
