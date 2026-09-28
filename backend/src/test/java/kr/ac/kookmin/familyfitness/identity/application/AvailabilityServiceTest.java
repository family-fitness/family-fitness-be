package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.AvailabilitySlot;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.port.AvailabilityRepository;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyAccessDeniedException;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.InvalidSlotException;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability.RawSlot;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 운동할 수 있는 시간 보기 · 바꾸기. 권한은 FE 목과 같다 — 보기는 같은 가족 누구나, 바꾸기는 보호자만(403 NOT_A_PARENT).
 * 가족은 인메모리 저장소로 실제 흐름(가족 만들기 · 아이 추가 · 초대 수락)을 거쳐 만든다.
 */
class AvailabilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final IdentityClock identityClock = clock.identityClock();
    private final InMemoryFamilyRepository families = new InMemoryFamilyRepository();
    private final ProfileSummaries summaries = new ProfileSummaries(identityClock);
    private final FamilyService familyService = new FamilyService(families, summaries, identityClock);
    private final InviteService inviteService = new InviteService(
            families,
            new AppProperties(
                    "Asia/Seoul",
                    "https://app.example.com/",
                    new AppProperties.Cors(),
                    new AppProperties.Auth(),
                    new AppProperties.Ai()),
            identityClock,
            new ClaimAttemptLimiter(identityClock));
    private final InMemoryAvailability store = new InMemoryAvailability();
    private final AvailabilityService service = new AvailabilityService(families, store, identityClock);

    private final UUID momUser = UUID.randomUUID();
    private final UUID childUser = UUID.randomUUID();
    private final UUID otherParentUser = UUID.randomUUID();
    private UUID momId;
    private UUID kidId;
    private UUID dadId;

    @BeforeEach
    void setUp() {
        CreatedFamily created = familyService.createFamily(momUser, "우리 가족", "엄마", LocalDate.of(1988, 3, 1), Sex.F);
        momId = created.ownerProfile().profileId();
        kidId = addMember(created.familyId(), "첫째", LocalDate.of(2016, 5, 20), ProfileRole.CHILD);
        dadId = addMember(created.familyId(), "아빠", LocalDate.of(1986, 1, 2), ProfileRole.PARENT);
        inviteService.claim(
                childUser, inviteService.issueInvite(momUser, kidId).claimCode().code());
        familyService.createFamily(otherParentUser, "옆집", "옆집 엄마", LocalDate.of(1985, 7, 7), Sex.F);
    }

    private UUID addMember(UUID familyId, String name, LocalDate birthDate, ProfileRole role) {
        ProfileSummary added = familyService.addMember(
                momUser, familyId, name, birthDate, Sex.M, role, null, null, new GuardianConsent(true, true));
        return added.profileId();
    }

    private static RawSlot raw(String day, String start, int minutes) {
        return new RawSlot(day, start, BigDecimal.valueOf(minutes));
    }

    @Test
    @DisplayName("보호자가 아이의 한 주를 적으면 요일 차례로 저장되고, 같은 가족은 아이 계정까지 누구나 본다")
    void parentWritesFamilyReads() {
        List<AvailabilitySlot> saved = service.replace(
                momUser, kidId, List.of(raw("SAT", "10:00", 30), raw("MON", "19:00", 20), raw("WED", "19:00", 20)));

        List<AvailabilitySlot> expected = List.of(
                new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(19, 0), 20),
                new AvailabilitySlot(DayOfWeek.WEDNESDAY, LocalTime.of(19, 0), 20),
                new AvailabilitySlot(DayOfWeek.SATURDAY, LocalTime.of(10, 0), 30));
        assertThat(saved).isEqualTo(expected);
        assertThat(service.get(momUser, kidId)).isEqualTo(expected);
        assertThat(service.get(childUser, kidId)).isEqualTo(expected);
        assertThat(service.slotsOf(kidId)).isEqualTo(expected);
        assertThat(store.savedBy.get(kidId)).isEqualTo(momId);
        assertThat(store.savedAt.get(kidId)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("적어 둔 것이 없으면 빈 목록이다(404 가 아니다)")
    void emptyWhenNothingWritten() {
        assertThat(service.get(childUser, kidId)).isEmpty();
        assertThat(service.slotsOf(kidId)).isEmpty();
    }

    @Test
    @DisplayName("다시 적으면 한 주가 통째로 바뀐다 — 빠진 요일은 지워지고, 빈 목록이면 모두 지운다")
    void replacesWholeWeek() {
        service.replace(momUser, kidId, List.of(raw("MON", "19:00", 20), raw("FRI", "19:00", 20)));

        service.replace(momUser, kidId, List.of(raw("TUE", "18:00", 40)));
        assertThat(service.get(momUser, kidId))
                .containsExactly(new AvailabilitySlot(DayOfWeek.TUESDAY, LocalTime.of(18, 0), 40));

        service.replace(momUser, kidId, List.of());
        assertThat(service.get(momUser, kidId)).isEmpty();
    }

    @Test
    @DisplayName("보호자는 다른 보호자 프로필의 한 주도 적는다(목과 같다)")
    void parentWritesOtherParent() {
        service.replace(momUser, dadId, List.of(raw("SAT", "10:00", 30)));

        assertThat(service.get(momUser, dadId)).hasSize(1);
    }

    @Test
    @DisplayName("아이 계정은 자기 것도 못 바꾼다 403 NOT_A_PARENT — 값이 틀려도 권한을 먼저 본다")
    void childCannotWrite() {
        service.replace(momUser, kidId, List.of(raw("MON", "19:00", 20)));

        assertThatThrownBy(() -> service.replace(childUser, kidId, List.of(raw("TUE", "18:00", 30))))
                .isInstanceOf(NotAParentException.class);
        assertThatThrownBy(() -> service.replace(childUser, kidId, List.of(raw("mon", "25:00", 999))))
                .isInstanceOf(NotAParentException.class);
        assertThat(service.get(momUser, kidId))
                .containsExactly(new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(19, 0), 20));
    }

    @Test
    @DisplayName("틀린 칸이 하나라도 있으면 400 INVALID_SLOT 이고 적어 둔 한 주는 그대로다")
    void invalidSlotKeepsWeek() {
        service.replace(momUser, kidId, List.of(raw("MON", "19:00", 20)));

        assertThatThrownBy(() ->
                        service.replace(momUser, kidId, List.of(raw("TUE", "18:00", 30), raw("WED", "18:00", 121))))
                .isInstanceOf(InvalidSlotException.class);
        assertThat(service.get(momUser, kidId))
                .containsExactly(new AvailabilitySlot(DayOfWeek.MONDAY, LocalTime.of(19, 0), 20));
    }

    @Test
    @DisplayName("다른 가족의 보호자는 보지도 바꾸지도 못한다 403 NOT_SAME_FAMILY")
    void otherFamilyDenied() {
        assertThatThrownBy(() -> service.get(otherParentUser, kidId))
                .isInstanceOf(FamilyAccessDeniedException.class)
                .extracting("code")
                .isEqualTo("NOT_SAME_FAMILY");
        assertThatThrownBy(() -> service.replace(otherParentUser, kidId, List.of(raw("MON", "19:00", 20))))
                .isInstanceOf(FamilyAccessDeniedException.class);
        assertThat(store.weeks).doesNotContainKey(kidId);
    }

    @Test
    @DisplayName("없는 프로필은 404 PROFILE_NOT_FOUND")
    void unknownProfile() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.get(momUser, unknown)).isInstanceOf(ProfileNotFoundException.class);
        assertThatThrownBy(() -> service.replace(momUser, unknown, List.of()))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    /** 프로필마다 마지막으로 적은 한 주만 든다(DB 의 지우고 다시 넣기와 같은 결과). */
    private static final class InMemoryAvailability implements AvailabilityRepository {
        final Map<UUID, WeeklyAvailability> weeks = new HashMap<>();
        final Map<UUID, UUID> savedBy = new HashMap<>();
        final Map<UUID, Instant> savedAt = new HashMap<>();

        @Override
        public List<AvailabilitySlot> findByProfileId(UUID profileId) {
            WeeklyAvailability week = weeks.get(profileId);
            return week == null ? List.of() : week.slots();
        }

        @Override
        public void replace(UUID profileId, WeeklyAvailability week, UUID by, Instant at) {
            weeks.put(profileId, week);
            savedBy.put(profileId, by);
            savedAt.put(profileId, at);
        }
    }
}
