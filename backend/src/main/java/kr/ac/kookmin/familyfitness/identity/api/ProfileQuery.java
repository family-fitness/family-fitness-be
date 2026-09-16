package kr.ac.kookmin.familyfitness.identity.api;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 다른 모듈이 프로필 정보를 읽는 유일한 통로. */
public interface ProfileQuery {
    @Nullable
    ProfileSummary findSummary(UUID profileId);

    @Nullable
    ProfileDetails findDetails(UUID profileId);

    List<ProfileSummary> summariesOfFamily(UUID familyId);

    List<ProfileDetails> detailsOfFamily(UUID familyId);

    /** 이 계정에 붙은 프로필들(여러 가족 가능). */
    List<ProfileSummary> summariesOfUser(UUID userId);

    @Nullable
    String familyName(UUID familyId);

    /** 전 가족 ID. 주간 코치 스케줄러가 가족 단위로 돈다. */
    List<UUID> allFamilyIds();
}
