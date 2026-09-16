package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import org.jspecify.annotations.Nullable;

// application 서비스 테스트용 인메모리 포트 구현. 애그리게잇을 그대로 들고 있어 저장 뒤 같은 객체가 보인다.
class InMemoryFamilyRepository implements FamilyRepository {
    final Map<UUID, Family> families = new LinkedHashMap<>();

    /** 조건부 UPDATE 를 흉내낸다. 테스트가 `false` 로 바꾸면 "다른 계정이 먼저 가져간" 상황이 된다. */
    boolean attachSucceeds = true;

    final List<AttachCall> attachCalls = new ArrayList<>();

    record AttachCall(UUID profileId, UUID userId, Instant at) {}

    @Override
    public List<UUID> allIds() {
        return List.copyOf(families.keySet());
    }

    @Override
    public @Nullable Family findById(UUID familyId) {
        return families.get(familyId);
    }

    @Override
    public @Nullable Family findByProfileId(UUID profileId) {
        return families.values().stream()
                .filter(f -> f.getProfiles().stream().anyMatch(it -> it.getId().equals(profileId)))
                .findFirst()
                .orElse(null);
    }

    @Override
    public @Nullable Family findByClaimCode(String code) {
        return families.values().stream()
                .filter(f -> f.getProfiles().stream()
                        .anyMatch(it -> it.getClaimCode() != null
                                && it.getClaimCode().code().equals(code)))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean isClaimCodeTaken(String code) {
        return findByClaimCode(code) != null;
    }

    @Override
    public List<Profile> profilesOfUser(UUID userId) {
        return families.values().stream()
                .flatMap(f -> f.getProfiles().stream().filter(it -> userId.equals(it.getUserId())))
                .toList();
    }

    @Override
    public Family save(Family family) {
        families.put(family.getId(), family);
        return family;
    }

    @Override
    public boolean attachUserIfUnclaimed(UUID profileId, UUID userId, Instant at) {
        attachCalls.add(new AttachCall(profileId, userId, at));
        if (!attachSucceeds) return false;
        Family family = findByProfileId(profileId);
        if (family == null) return false;
        Profile profile = family.profile(profileId);
        if (profile.hasAccount()) return false;
        profile.claim(userId, at);
        return true;
    }
}
