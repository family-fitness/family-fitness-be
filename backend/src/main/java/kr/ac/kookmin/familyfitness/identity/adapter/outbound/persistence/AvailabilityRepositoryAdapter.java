package kr.ac.kookmin.familyfitness.identity.adapter.outbound.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import kr.ac.kookmin.familyfitness.identity.application.port.AvailabilityRepository;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AvailabilityRepositoryAdapter implements AvailabilityRepository {
    private final AvailabilitySlotJpaRepository jpa;

    public AvailabilityRepositoryAdapter(AvailabilitySlotJpaRepository jpa) {
        this.jpa = jpa;
    }

    /** 요일 문자열(MON …)은 알파벳 차례라 DB 에서 정렬하지 않고, 읽은 뒤 요일 차례(월 → 일)로 줄 세운다. */
    @Override
    public List<AvailabilitySlot> findByProfileId(UUID profileId) {
        return jpa.findByIdProfileId(profileId).stream()
                .map(AvailabilityRepositoryAdapter::toSlot)
                .sorted(Comparator.comparing(AvailabilitySlot::day))
                .toList();
    }

    /**
     * 프로필 행을 먼저 잠가(select … for update) 같은 프로필의 바꾸기를 차례대로 한다. 잠그지 않으면 PostgreSQL 의
     * READ COMMITTED 에서 두 요청이 서로 넣은 행을 보지 못한 채 지우기를 끝내, 요일이 다르면 두 요청의 칸이 합쳐져 남는다
     * (요일이 같으면 뒤 요청이 기본 키 위반으로 409 가 된다). 잠근 뒤에는 뒤 요청이 앞 요청의 행까지 지우고 자기 한 주를 넣는다.
     * 끝에 flush 해 제약 위반이 커밋 때가 아니라 여기서 난다.
     */
    @Override
    @Transactional
    public void replace(UUID profileId, WeeklyAvailability week, UUID savedBy, Instant savedAt) {
        jpa.lockProfile(profileId);
        jpa.deleteByProfileId(profileId);
        jpa.saveAll(week.slots().stream()
                .map(it -> toEntity(profileId, it, savedBy, savedAt))
                .toList());
        jpa.flush();
    }

    private static AvailabilitySlot toSlot(AvailabilitySlotEntity entity) {
        return new AvailabilitySlot(
                WeeklyAvailability.dayOf(entity.getId().getDayOfWeek()), entity.getStartTime(), entity.getMinutes());
    }

    private static AvailabilitySlotEntity toEntity(
            UUID profileId, AvailabilitySlot slot, UUID savedBy, Instant savedAt) {
        return new AvailabilitySlotEntity(
                new AvailabilitySlotId(profileId, WeeklyAvailability.dayCode(slot.day())),
                slot.start(),
                slot.minutes(),
                savedBy,
                savedAt);
    }
}
