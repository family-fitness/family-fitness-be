package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CheerQuery;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 응원 집계({@link CheerQuery}) 구현. */
@Service
@Transactional(readOnly = true)
public class CheerQueryService implements CheerQuery {
    private final CheerRepository cheers;

    public CheerQueryService(CheerRepository cheers) {
        this.cheers = cheers;
    }

    @Override
    public int countCheers(UUID familyId, Instant from, Instant to) {
        return cheers.countInFamily(familyId, from, to);
    }
}
