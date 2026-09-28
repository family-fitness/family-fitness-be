package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilityQuery;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.application.port.AvailabilityRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability.RawSlot;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동할 수 있는 시간 보기 · 바꾸기. 누가 무엇을 할 수 있는지는 FE 목(fe:src/mocks/handlers.ts)과 같다 —
 * 보기는 같은 가족 누구나, 바꾸기는 그 가족의 보호자만(아이 · 다른 보호자 프로필 모두).
 * 다른 모듈에는 {@link AvailabilityQuery} 로 읽기만 연다.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService implements AvailabilityQuery {
    private final FamilyRepository families;
    private final AvailabilityRepository availability;
    private final IdentityClock clock;

    public AvailabilityService(FamilyRepository families, AvailabilityRepository availability, IdentityClock clock) {
        this.families = families;
        this.availability = availability;
        this.clock = clock;
    }

    /** 적어 둔 것이 없으면 빈 목록이다(404 가 아니다 — 목과 같다). 가족이 아니면 403 NOT_SAME_FAMILY. */
    public List<AvailabilitySlot> get(UUID userId, UUID profileId) {
        load(profileId).requireMember(userId);
        return availability.findByProfileId(profileId);
    }

    /**
     * 한 주를 통째로 바꾼다. 검사 차례는 목과 같게 권한(403 NOT_A_PARENT) → 값(400 INVALID_SLOT)이다.
     * 지우기와 넣기가 한 트랜잭션이라, 어디서 실패하든 전의 한 주가 그대로 남는다.
     */
    @Transactional
    public List<AvailabilitySlot> replace(UUID userId, UUID profileId, List<? extends @Nullable RawSlot> slots) {
        Profile parent = load(profileId).requireParent(userId);
        WeeklyAvailability week = WeeklyAvailability.parse(slots);
        availability.replace(profileId, week, parent.getId(), clock.now());
        return week.slots();
    }

    @Override
    public List<AvailabilitySlot> slotsOf(UUID profileId) {
        return availability.findByProfileId(profileId);
    }

    private Family load(UUID profileId) {
        Family family = families.findByProfileId(profileId);
        if (family == null) throw new ProfileNotFoundException(profileId);
        return family;
    }
}
