package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;

/**
 * 아직 쓰지 않았고 만료되지 않은 가족 초대 하나.
 *
 * @param issuedByName 초대를 낸 보호자 이름. 그 사람이 가족에서 빠졌으면 오너 이름이다
 */
public record LiveFamilyInvite(
        String code, ProfileRole role, Instant expiresAt, Instant createdAt, String issuedByName) {}
