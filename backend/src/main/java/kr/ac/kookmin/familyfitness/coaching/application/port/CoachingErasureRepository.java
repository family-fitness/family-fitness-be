package kr.ac.kookmin.familyfitness.coaching.application.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 탈퇴, 구성원 내보내기, 동의 철회 때 coaching 표를 정리한다. 부르는 쪽 트랜잭션 안에서만 돈다. 차례는 {@code CoachingErasure} 가 정한다. */
public interface CoachingErasureRepository {
    /** 이 가족 미션 가운데 참여자가 이 사람 하나뿐인 미션. */
    List<UUID> soloMissionsOf(UUID familyId, UUID profileId);

    /** 미션을 통째로 지운다. 느낌, 칸 끝, 참여자, 칸, 미션 행 차례다. */
    void deleteMissions(Collection<UUID> missionIds);

    /**
     * 여럿이 하는 미션에서 이 사람 몫(느낌, 칸 끝, 참여자 행)만 지운다. 이 사람이 만든 미션과 이 사람이 확인한 참여자 행은
     * {@code heirProfileId} 가 한 것으로 돌린다.
     */
    void leaveMissions(UUID profileId, UUID heirProfileId);

    /** 여럿이 하는 미션에서 이 사람 몫(느낌, 칸 끝, 참여자 행)만 지운다. 돌릴 칸이 없는 동의 철회가 쓴다. */
    void dropParticipation(UUID profileId);

    /**
     * 이 사람을 대상으로 짠 편성과 그 제안 항목, 제안 칸을 지운다. 그 편성으로 만든 미션이 남아 있으면(함께 한 보호자가 남음)
     * 편성과의 연결을 끊고 직접 만든 미션(MANUAL)으로 남긴다.
     */
    void deleteRunsAbout(UUID profileId);

    /**
     * 남는 편성에서 이 사람을 지운다. 요청한 사람 칸은 비우고, 승인한 사람은 {@code heirProfileId} 로 돌리고, 제안 항목의
     * 참여자 목록(participants_json)에서 뺀다.
     */
    void forgetInRuns(UUID familyId, UUID profileId, UUID heirProfileId);

    /** 남는 편성의 제안 항목 참여자 목록(participants_json)에서 이 사람을 뺀다. 돌릴 칸이 없는 동의 철회가 쓴다. */
    void dropFromProposals(UUID familyId, UUID profileId);

    /** 이 사람들의 코치 대화와 인용, 영상 기록, 구간 찜을 지운다. */
    void erasePersonal(Collection<UUID> profileIds);

    /** 이 가족의 미션과 편성을 모두 지운다. */
    void eraseFamily(UUID familyId);
}
