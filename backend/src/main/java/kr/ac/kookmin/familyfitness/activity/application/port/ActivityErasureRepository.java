package kr.ac.kookmin.familyfitness.activity.application.port;

import java.util.Collection;
import java.util.UUID;

/** 탈퇴와 구성원 내보내기 때 activity 표에서 지울 행을 지운다. 부르는 쪽 트랜잭션 안에서만 돈다. */
public interface ActivityErasureRepository {
    /** 이 사람의 하루 활동을 지우고, 이 사람이 쓴 쉬는 날 카드는 {@code heirProfileId} 가 쓴 것으로 돌린다(카드는 가족 것이라 남긴다). */
    void eraseProfile(UUID profileId, UUID heirProfileId);

    /** 이 가족의 쉬는 날 카드와 이 프로필들의 하루 활동을 지운다. */
    void eraseFamily(UUID familyId, Collection<UUID> profileIds);
}
