package kr.ac.kookmin.familyfitness.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * 가족 하나를 통째로 지우기 바로 전이다(가족에 혼자 남은 오너의 탈퇴). {@link ProfileDeleting} 과 같은 트랜잭션 규칙과 같은 듣는
 * 차례를 따르고, 듣는 쪽은 이 가족의 행과 이 가족 프로필들의 행을 모두 지운다. league 도 이때 듣고 리그 참가 기록을 지운다(50).
 * 이벤트가 돌아오면 identity 가 응원, 운동할 수 있는 시간, 동의 이력, 가족 초대, 프로필, 가족 행을 지운다.
 *
 * @param profileIds 이 가족의 프로필 전부
 */
public record FamilyDeleting(UUID familyId, List<UUID> profileIds) {
    public FamilyDeleting {
        profileIds = List.copyOf(profileIds);
    }
}
