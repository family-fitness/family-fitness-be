package kr.ac.kookmin.familyfitness.identity.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyInvite;

/** 가족 초대(family_invites) 저장소. 코드는 정규화된(대문자) 값으로 다룬다. */
public interface FamilyInviteRepository {
    /** 새 초대를 넣는다. 같은 코드가 이미 있으면(동시 발급이 같은 코드를 뽑음) 유니크 위반으로 409 CONFLICT 가 된다. */
    void add(FamilyInvite invite);

    boolean isCodeTaken(String code);

    /** 그 가족의 아직 쓰지 않았고 {@code now} 에 만료되지 않은 초대. 최근에 만든 것부터. */
    List<FamilyInvite> liveOf(UUID familyId, Instant now);

    /** 그 가족의 아직 쓰지 않은 초대를 지운다. 지웠으면 true, 그런 초대가 없으면(없는 코드, 쓴 코드, 다른 가족 코드) false. */
    boolean deleteUnclaimed(UUID familyId, String code);
}
