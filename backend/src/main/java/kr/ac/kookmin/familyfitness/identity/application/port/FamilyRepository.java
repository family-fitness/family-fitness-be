package kr.ac.kookmin.familyfitness.identity.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import org.jspecify.annotations.Nullable;

/** 가족 애그리게잇 저장소. 프로필은 가족을 통해서만 저장된다. */
public interface FamilyRepository {
    @Nullable
    Family findById(UUID familyId);

    List<UUID> allIds();

    @Nullable
    Family findByProfileId(UUID profileId);

    /** 정규화된(대문자) 코드로 찾는다. */
    @Nullable
    Family findByClaimCode(String code);

    boolean isClaimCodeTaken(String code);

    /** 이 계정에 붙은 프로필들(여러 가족 가능). */
    List<Profile> profilesOfUser(UUID userId);

    /** 가족과 모든 프로필을 한 트랜잭션에 저장한다(신규 INSERT · 기존 UPDATE). */
    Family save(Family family);

    /**
     * 동시성 안전한 계정 연결: `UPDATE profiles SET user_id=? ... WHERE id=? AND user_id IS NULL` 한 문장.
     * 영향 0행이면 false — 다른 계정이 먼저 가져간 것이다.
     */
    boolean attachUserIfUnclaimed(UUID profileId, UUID userId, Instant at);
}
