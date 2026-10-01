package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyInviteRepository;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyInvite;

// application 서비스 시험용 인메모리 가족 초대 저장소. 코드 → 초대.
class InMemoryFamilyInviteRepository implements FamilyInviteRepository {
    final Map<String, FamilyInvite> invites = new LinkedHashMap<>();

    @Override
    public void add(FamilyInvite invite) {
        if (invites.putIfAbsent(invite.code().code(), invite) != null) {
            throw new IllegalStateException("같은 코드가 이미 있다: " + invite.code().code());
        }
    }

    @Override
    public boolean isCodeTaken(String code) {
        return invites.containsKey(code);
    }

    @Override
    public List<FamilyInvite> liveOf(UUID familyId, Instant now) {
        return invites.values().stream()
                .filter(it -> it.familyId().equals(familyId) && it.isLive(now))
                .sorted(Comparator.comparing(FamilyInvite::createdAt).reversed())
                .toList();
    }

    @Override
    public boolean deleteUnclaimed(UUID familyId, String code) {
        FamilyInvite invite = invites.get(code);
        if (invite == null || !invite.familyId().equals(familyId) || invite.isClaimed()) return false;
        invites.remove(code);
        return true;
    }
}
