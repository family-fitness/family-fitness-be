package kr.ac.kookmin.familyfitness.identity.application.port;

import java.time.Instant;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;

public interface CheerRepository {
    Cheer save(Cheer cheer);

    /** 같은 보내는 쪽→받는 쪽 조합으로 {@code after} 보다 뒤(초과)에 보낸 횟수(과다 호출 판정). */
    int countFromTo(UUID fromProfileId, UUID toProfileId, Instant after);

    /** [from, to) 구간의 가족 응원 수. */
    int countInFamily(UUID familyId, Instant from, Instant to);
}
