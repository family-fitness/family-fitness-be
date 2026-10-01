package kr.ac.kookmin.familyfitness.progress.api;

import java.util.Collection;
import java.util.UUID;

/**
 * coaching 이 탈퇴나 구성원 내보내기로 미션을 통째로 지웠다고 알린다. 지우는 트랜잭션 안에서 부른다. progress 는 coaching 을
 * 참조하지 않으므로 이벤트 대신 이 통로로 받는다.
 */
public interface DeletedMissions {
    /** 경험치 원장에서 이 미션들을 가리키는 칸(mission_id)을 비운다. 줄은 지우지 않아 남는 사람의 경험치 합은 그대로다. */
    void forget(Collection<UUID> missionIds);
}
