package kr.ac.kookmin.familyfitness.identity.application.port;

import kr.ac.kookmin.familyfitness.identity.domain.FamilyInvite;

/** 가족 초대(family_invites) 저장소. 코드는 정규화된(대문자) 값으로 다룬다. */
public interface FamilyInviteRepository {
    /** 새 초대를 넣는다. 같은 코드가 이미 있으면(동시 발급이 같은 코드를 뽑음) 유니크 위반으로 409 CONFLICT 가 된다. */
    void add(FamilyInvite invite);

    boolean isCodeTaken(String code);
}
