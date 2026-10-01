package kr.ac.kookmin.familyfitness.fitness.application.port;

import java.util.Collection;
import java.util.UUID;

/** 탈퇴, 구성원 내보내기, 동의 철회 때 이 프로필들의 측정 회차와 그 항목을 지운다. 부르는 쪽 트랜잭션 안에서만 돈다. */
public interface FitnessErasureRepository {
    void eraseProfiles(Collection<UUID> profileIds);
}
