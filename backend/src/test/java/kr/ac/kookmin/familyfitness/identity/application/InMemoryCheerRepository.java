package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.port.CheerRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyThankedException;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import org.jspecify.annotations.Nullable;

class InMemoryCheerRepository implements CheerRepository {
    final List<Cheer> cheers = new ArrayList<>();

    /** DB 의 ux_cheers_reply_to 와 같게 같은 스티커에 두 번째 THANKS 를 막는다. */
    @Override
    public Cheer save(Cheer cheer) {
        if (cheer.replyToCheerId() != null && existsReplyTo(cheer.replyToCheerId())) {
            throw new AlreadyThankedException();
        }
        cheers.add(cheer);
        return cheer;
    }

    @Override
    public @Nullable Cheer findById(UUID cheerId) {
        return cheers.stream().filter(it -> it.id().equals(cheerId)).findFirst().orElse(null);
    }

    @Override
    public boolean existsReplyTo(UUID cheerId) {
        return cheers.stream().anyMatch(it -> cheerId.equals(it.replyToCheerId()));
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

    @Override
    public List<Cheer> findInFamily(
            UUID familyId,
            @Nullable UUID toProfileId,
            @Nullable UUID fromProfileId,
            @Nullable UUID missionId,
            int limit) {
        return cheers.stream()
                .filter(it -> it.familyId().equals(familyId))
                .filter(it -> toProfileId == null || it.toProfileId().equals(toProfileId))
                .filter(it -> fromProfileId == null || it.fromProfileId().equals(fromProfileId))
                .filter(it -> missionId == null || Objects.equals(it.missionId(), missionId))
                .sorted(Comparator.comparing(Cheer::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<Cheer> findReceived(UUID toProfileId, Instant from, Instant to) {
        return cheers.stream()
                .filter(it -> it.toProfileId().equals(toProfileId)
                        && !it.createdAt().isBefore(from)
                        && it.createdAt().isBefore(to))
                .sorted(Comparator.comparing(Cheer::createdAt))
                .toList();
    }
}
