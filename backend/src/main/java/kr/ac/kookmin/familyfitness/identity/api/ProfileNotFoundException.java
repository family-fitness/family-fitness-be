package kr.ac.kookmin.familyfitness.identity.api;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class ProfileNotFoundException extends DomainException {
    public ProfileNotFoundException(UUID profileId) {
        super("PROFILE_NOT_FOUND", ErrorKind.NOT_FOUND, "프로필이 없습니다: " + profileId);
    }
}
