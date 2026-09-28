package kr.ac.kookmin.familyfitness.coaching.domain;

import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

/** 누구의 찜인지(profileId) 없이 찜을 바꾸거나 찜 목록을 달라고 했다. FE 목(src/mocks/clips.ts)과 같은 코드다. */
public class ProfileRequiredException extends DomainException {
    public ProfileRequiredException() {
        super("PROFILE_REQUIRED", ErrorKind.BAD_REQUEST, "누구의 찜인지 profileId 가 필요합니다");
    }
}
