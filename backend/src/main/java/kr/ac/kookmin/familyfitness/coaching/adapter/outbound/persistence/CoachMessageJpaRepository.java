package kr.ac.kookmin.familyfitness.coaching.adapter.outbound.persistence;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachMessageJpaRepository extends JpaRepository<CoachMessageEntity, UUID> {
    @Nullable
    CoachMessageEntity findFirstByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
