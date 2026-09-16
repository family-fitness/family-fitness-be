package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;

class InMemoryCheerRepository implements CheerRepository {
    final List<Cheer> cheers = new ArrayList<>();

    @Override
    public Cheer save(Cheer cheer) {
        cheers.add(cheer);
        return cheer;
    }

    @Override
    public int countFromTo(UUID fromProfileId, UUID toProfileId, Instant after) {
        return (int) cheers.stream()
                .filter(it -> it.fromProfileId().equals(fromProfileId)
                        && it.toProfileId().equals(toProfileId)
                        && it.createdAt().isAfter(after))
                .count();
    }

    @Override
    public int countInFamily(UUID familyId, Instant from, Instant to) {
        return (int) cheers.stream()
                .filter(it -> it.familyId().equals(familyId)
                        && !it.createdAt().isBefore(from)
                        && it.createdAt().isBefore(to))
                .count();
    }
}
