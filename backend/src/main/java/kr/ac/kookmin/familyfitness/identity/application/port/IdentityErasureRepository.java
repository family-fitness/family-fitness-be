package kr.ac.kookmin.familyfitness.identity.application.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 탈퇴와 구성원 내보내기 때 identity 표를 지운다. 다른 모듈이 자기 행을 먼저 지운 뒤에 부른다. 부르는 쪽 트랜잭션 안에서만 돈다.
 * 동의 이력(consent_events)은 고치지 않는 표지만(V145), 개인정보처리방침이 「탈퇴하면 바로 지워요」 라고 약속해서 이때만 예외로
 * 지우거나 고친다.
 */
public interface IdentityErasureRepository {
    /**
     * 가족 행을 SELECT … FOR UPDATE 로 잠근다. 탈퇴와 내보내기가 맨 먼저 잡는다. 구성원 추가는 프로필 행을 넣을 때 외래 키 검사로
     * 이 행을 잡으므로, 혼자인지 세는 동안 구성원이 새로 붙지 않는다.
     */
    void lockFamily(UUID familyId);

    /** 이 사람이 보내거나 받은 응원과, 그 응원에 단 답장의 id. */
    List<UUID> cheersOf(UUID profileId);

    /** 이 가족 응원에 붙은 미션 id(겹치지 않게). */
    List<UUID> missionsOnCheers(UUID familyId);

    /** 이 미션을 가리키는 응원의 missionId 를 비운다. 응원은 받은 사람의 기록이라 남긴다. */
    void forgetMissionOnCheers(UUID familyId, UUID missionId);

    /**
     * 한 사람의 identity 행을 지운다. 응원, 그 사람의 운동할 수 있는 시간, 동의 이력, 프로필 행 차례다. 남는 사람의 운동할 수 있는
     * 시간을 이 사람이 적었으면, 이 사람이 보낸 초대코드가 남아 있으면 {@code heirProfileId} 가 한 것으로 돌린다.
     */
    void eraseProfile(UUID profileId, UUID heirProfileId, Collection<UUID> cheerIds);

    /**
     * 이 계정이 남긴 동의 기록에서 계정을 지운다(profiles.consent_by_user_id, consent_events.actor_user_id 를 비운다). 동의는 그대로
     * 살아 있고, 누가 했는지만 모르는 채로 남는다(V145 가 옮겨 넣은 철회 줄과 같다).
     */
    void forgetConsentActor(UUID userId);

    /** 가족 하나를 지운다. 응원, 운동할 수 있는 시간, 동의 이력, 프로필, 가족 행 차례다. */
    void eraseFamily(UUID familyId, Collection<UUID> profileIds);

    /** 계정을 지운다. 리프레시 토큰 기록, 동의 기록의 계정 칸, 계정 행 차례다. 이 계정에 붙은 프로필은 먼저 지워 둬야 한다. */
    void eraseAccount(UUID userId);
}
