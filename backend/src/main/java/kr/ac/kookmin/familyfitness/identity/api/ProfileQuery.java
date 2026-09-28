package kr.ac.kookmin.familyfitness.identity.api;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 여러 가족의 식구를 한 번에 읽는다(리그 방처럼 여러 가족을 같이 셀 때). 요청한 가족마다 한 줄이고, 없는 가족은 빈 목록이다.
     * 식구 차례는 {@link #summariesOfFamily} 와 같다. 기본 구현은 가족마다 {@link #summariesOfFamily} 를 부른다.
     */
    default Map<UUID, List<ProfileSummary>> summariesOfFamilies(Collection<UUID> familyIds) {
        Map<UUID, List<ProfileSummary>> out = new LinkedHashMap<>();
        for (UUID familyId : familyIds) out.put(familyId, summariesOfFamily(familyId));
        return Collections.unmodifiableMap(out);
    }

    /** 여러 가족의 이름을 한 번에 읽는다. 없는 가족은 빠진다. 기본 구현은 가족마다 {@link #familyName} 을 부른다. */
    default Map<UUID, String> familyNames(Collection<UUID> familyIds) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (UUID familyId : familyIds) {
            String name = familyName(familyId);
            if (name != null) out.put(familyId, name);
        }
        return Collections.unmodifiableMap(out);
    }
}
