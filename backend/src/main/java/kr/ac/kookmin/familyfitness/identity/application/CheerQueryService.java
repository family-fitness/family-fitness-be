package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.api.CheerView;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈이 읽는 응원({@link CheerQuery}) 구현. */
@Service
@Transactional(readOnly = true)
public class CheerQueryService implements CheerQuery {
    private final CheerRepository cheers;
    private final FamilyRepository families;

    public CheerQueryService(CheerRepository cheers, FamilyRepository families) {
        this.cheers = cheers;
        this.families = families;
    }

    @Override
    public int countCheers(UUID familyId, Instant from, Instant to) {
        return cheers.countInFamily(familyId, from, to);
    }

    @Override
    public List<CheerView> received(UUID toProfileId, Instant from, Instant to) {
        Family family = families.findByProfileId(toProfileId);
        if (family == null) return List.of();
        Map<UUID, String> names = CheerViews.namesOf(family);
        return cheers.findReceived(toProfileId, from, to).stream()
                .map(it -> CheerViews.of(it, names))
                .toList();
    }
}
