package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.MissionLookup;
import kr.ac.kookmin.familyfitness.identity.api.ProfileRecordsDeleting;
import kr.ac.kookmin.familyfitness.identity.application.port.IdentityErasureRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 프로필은 남기고 그 사람의 기록만 지운다. 보호자가 아이의 동의를 거두면 {@link ProfileSettingsService#updateConsent} 가 같은
 * 트랜잭션에서 부른다. 개인정보처리방침이 동의를 철회하면 지체 없이 파기한다고 약속해서다.
 *
 * <p>외래 키에 ON DELETE CASCADE 가 없다. 그래서 {@link ProfileRecordsDeleting} 을 발행해 다른 모듈이 자기 행을 먼저 지우게 하고,
 * 이벤트가 돌아오면 identity 가 응원과 그 사람의 운동할 수 있는 시간을 지운다. 프로필 행과 동의 이력은 남긴다. 프로필에 적어 둔
 * 키와 몸무게는 동의를 거둘 때 도메인이 비운다. 어느 단계에서든 실패하면 동의를 거둔 것까지 모두 되돌아간다.
 */
@Component
public class ProfileRecordsEraser {
    private final IdentityErasureRepository erasure;
    private final MissionLookup missions;
    private final ApplicationEventPublisher events;

    public ProfileRecordsEraser(
            IdentityErasureRepository erasure, MissionLookup missions, ApplicationEventPublisher events) {
        this.erasure = erasure;
        this.missions = missions;
        this.events = events;
    }

    public void erase(UUID familyId, UUID profileId) {
        List<UUID> cheers = erasure.cheersOf(profileId);
        events.publishEvent(new ProfileRecordsDeleting(familyId, profileId, cheers));
        erasure.eraseRecords(profileId, cheers);
        DeletedMissionsOnCheers.forget(erasure, missions, familyId);
    }
}
