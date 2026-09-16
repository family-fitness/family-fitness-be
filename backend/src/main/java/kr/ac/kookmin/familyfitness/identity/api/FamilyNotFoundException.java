package kr.ac.kookmin.familyfitness.identity.api;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.DomainException;
import kr.ac.kookmin.familyfitness.shared.domain.ErrorKind;

public class FamilyNotFoundException extends DomainException {
    public FamilyNotFoundException(UUID familyId) {
        super("FAMILY_NOT_FOUND", ErrorKind.NOT_FOUND, "가족이 없습니다: " + familyId);
    }
}
