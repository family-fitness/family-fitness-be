package kr.ac.kookmin.familyfitness.identity.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyThankedException;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import org.jspecify.annotations.Nullable;

public interface CheerRepository {
    /**
     * 곧바로 DB 에 적는다. 같은 스티커에 THANKS 가 두 번 들어와 유니크 인덱스가 막으면 {@link AlreadyThankedException}.
     * 사전 검사({@link #existsReplyTo})를 동시 요청 둘이 함께 지나친 경우다.
     */
    Cheer save(Cheer cheer);

    @Nullable
    Cheer findById(UUID cheerId);

    /** 이 응원에 답한 THANKS 가 있나. */
    boolean existsReplyTo(UUID cheerId);

    /** 같은 보내는 쪽→받는 쪽 조합으로 {@code after} 보다 뒤(초과)에 보낸 횟수(과다 호출 판정). */
    int countFromTo(UUID fromProfileId, UUID toProfileId, Instant after);

    /** [from, to) 구간의 가족 응원 수. */
    int countInFamily(UUID familyId, Instant from, Instant to);

    /** 가족 응원 최근 것부터 {@code limit} 건. null 인 거르기 값은 거르지 않는다. */
    List<Cheer> findInFamily(
            UUID familyId,
            @Nullable UUID toProfileId,
            @Nullable UUID fromProfileId,
            @Nullable UUID missionId,
            int limit);

    /** 이 프로필이 [from, to) 구간에 받은 응원. createdAt 오름차순. */
    List<Cheer> findReceived(UUID toProfileId, Instant from, Instant to);
}
