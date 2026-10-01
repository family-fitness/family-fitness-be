package kr.ac.kookmin.familyfitness.identity.application;

import java.util.LinkedHashMap;
import java.util.Map;
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
}
