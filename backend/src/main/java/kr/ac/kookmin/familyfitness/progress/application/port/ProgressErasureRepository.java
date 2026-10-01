package kr.ac.kookmin.familyfitness.progress.application.port;

import java.util.Collection;
import java.util.UUID;

/**
 * 탈퇴와 구성원 내보내기 때만 쓰는 원장 정리. 원장은 넣기만 하는 표지만(V140), 개인정보처리방침이 「탈퇴하면 바로 지워요」 라고
 * 약속해서 이때만 예외로 지운다. 부르는 쪽 트랜잭션 안에서만 돈다.
 */
public interface ProgressErasureRepository {
    /** 이 프로필들의 경험치 줄과 업적을 지운다. 남는 사람의 줄에서 이 프로필들을 보낸 사람으로 적은 칸은 비운다. */
    void eraseProfiles(Collection<UUID> profileIds);

    /** 남는 사람의 경험치 줄에서 지운 미션을 가리키는 칸을 비운다. */
    void forgetMissions(Collection<UUID> missionIds);
}
