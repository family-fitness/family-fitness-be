package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyCheersException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가족 안의 응원. 규칙은 {@link Family#validateCheer}, 과다 호출은 저장된 건수로 판정. */
@Service
@Transactional
public class CheerService {
    private final FamilyRepository families;
    private final CheerRepository cheers;
    private final IdentityClock clock;

    public CheerService(FamilyRepository families, CheerRepository cheers, IdentityClock clock) {
        this.families = families;
        this.cheers = cheers;
        this.clock = clock;
    }

    public Cheer cheer(
            UUID userId,
            UUID familyId,
            UUID fromProfileId,
            UUID toProfileId,
            @Nullable String message,
            @Nullable String emoji,
            @Nullable UUID missionId) {
        Family family = families.findById(familyId);
        if (family == null) throw new FamilyNotFoundException(familyId);
        family.validateCheer(userId, fromProfileId, toProfileId);
        Instant now = clock.now();
        if (cheers.countFromTo(fromProfileId, toProfileId, now.minus(Cheer.WINDOW)) >= Cheer.MAX_PER_WINDOW) {
            throw new TooManyCheersException();
        }
        return cheers.save(new Cheer(
                UUID.randomUUID(),
                familyId,
                fromProfileId,
                toProfileId,
                blankToNull(message),
                blankToNull(emoji),
                missionId,
                now));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
