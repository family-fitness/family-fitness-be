package kr.ac.kookmin.familyfitness.identity.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.api.ReviewFamilyCreated;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.User;
import kr.ac.kookmin.familyfitness.identity.domain.WeeklyAvailability.RawSlot;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 심사용 계정 로그인. 부를 때마다 새 계정과 새 체험 가족을 만들고 그 계정으로 로그인시킨다 — 심사위원끼리 서로의 기록을 건드리지 않게.
 * 계정은 (REVIEW, 「review-」 + 무작위)라 같은 계정으로 다시 들어오는 길은 없다. 그 브라우저의 리프레시 토큰(30일)으로만 이어서 본다.
 *
 * <p>체험 가족 「체험 가족」:
 * <ul>
 *   <li>엄마 — 이 계정의 보호자(가족을 만든 사람), 만 38세 여, 참여 방식 FULL
 *   <li>아빠 — 보호자, 만 40세 남, 계정 없음
 *   <li>하윤 — 아이, 만 11세 여(유소년), 계정 없음, 보호자 동의 있음. 사흘 전에 유소년 종목 일곱 가지와 키 · 몸무게 · 허리둘레를
 *       재 둬서 종합 등급(2등급)이 나온다
 *   <li>서준 — 아이, 만 6세 남(유아기), 계정 없음, 보호자 동의 있음. 사흘 전에 유아기 종목 몇 가지만 재 뒀다
 * </ul>
 * 네 사람 모두 운동할 수 있는 시간을 적어 둔다. 분은 FE 가 고를 수 있는 값(10 · 20 · 30 · 40)만 쓴다 — 15분이면 편성 화면이 10분 칩을 켠다. 미션은 만들지 않는다 — 들어와서 오늘 편성을 직접 짜 보게.
 * 측정은 이 모듈이 넣지 않는다. {@link ReviewFamilyCreated} 를 발행하면 측정(fitness)이 같은 트랜잭션에서 넣는다.
 *
 * <p>가족 · 구성원 · 참여 방식 · 운동할 수 있는 시간은 화면이 부르는 서비스를 그대로 거친다. 규칙(나이 · 동의 · 칸 값)이 사람이 만든
 * 가족과 똑같이 걸린다. 한 트랜잭션이라 중간에 실패하면 계정까지 남지 않는다.
 */
@Service
@Transactional
public class ReviewLoginService {
    static final String PROVIDER_USER_PREFIX = "review-";
    static final String FAMILY_NAME = "체험 가족";

    private final ReviewLoginLimiter limiter;
    private final UserRegistrationService registration;
    private final FamilyService families;
    private final ProfileSettingsService settings;
    private final AvailabilityService availability;
    private final AuthService auth;
    private final ApplicationEventPublisher events;
    private final IdentityClock clock;

    public ReviewLoginService(
            ReviewLoginLimiter limiter,
            UserRegistrationService registration,
            FamilyService families,
            ProfileSettingsService settings,
            AvailabilityService availability,
            AuthService auth,
            ApplicationEventPublisher events,
            IdentityClock clock) {
        this.limiter = limiter;
        this.registration = registration;
        this.families = families;
        this.settings = settings;
        this.availability = availability;
        this.auth = auth;
        this.events = events;
        this.clock = clock;
    }

    /** 같은 IP 가 한 시간에 30번, 또는 모두 합쳐 300번을 넘기면 계정을 만들기 전에 429 TOO_MANY({@link ReviewLoginLimiter}). */
    public AuthResult login(String clientIp) {
        limiter.acquire(clientIp);
        User user = registration.registerOrGet(User.PROVIDER_REVIEW, PROVIDER_USER_PREFIX + UUID.randomUUID(), null);
        createFamily(user.id());
        return auth.startSession(user.id());
    }

    private void createFamily(UUID userId) {
        LocalDate today = clock.today();
        CreatedFamily family = families.createFamily(userId, FAMILY_NAME, "엄마", bornAged(today, 38, 5), Sex.F);
        UUID familyId = family.familyId();
        UUID mom = family.ownerProfile().profileId();
        settings.changeSupportMode(userId, mom, SupportMode.FULL);

        GuardianConsent consent = new GuardianConsent(true, true);
        UUID dad = families.addMember(
                        userId, familyId, "아빠", bornAged(today, 40, 2), Sex.M, ProfileRole.PARENT, null, null, null)
                .profileId();
        ProfileSummary hayun = families.addMember(
                userId,
                familyId,
                "하윤",
                bornAged(today, 11, 4),
                Sex.F,
                ProfileRole.CHILD,
                new BigDecimal("148.0"),
                new BigDecimal("40.0"),
                consent);
        ProfileSummary seojun = families.addMember(
                userId,
                familyId,
                "서준",
                bornAged(today, 6, 4),
                Sex.M,
                ProfileRole.CHILD,
                new BigDecimal("118.0"),
                new BigDecimal("21.5"),
                consent);

        availability.replace(userId, mom, weekdaysAnd("19:00", 20, "SAT", "10:00", 30));
        availability.replace(
                userId,
                dad,
                List.of(
                        slot("TUE", "20:00", 20),
                        slot("THU", "20:00", 20),
                        slot("SAT", "10:00", 30),
                        slot("SUN", "10:00", 30)));
        availability.replace(userId, hayun.profileId(), everyDay("18:30", 20, "10:00", 30));
        availability.replace(userId, seojun.profileId(), everyDay("18:30", 10, "10:00", 20));

        events.publishEvent(new ReviewFamilyCreated(familyId, userId, hayun.profileId(), seojun.profileId()));
    }

    /**
     * 오늘 만 {@code years} 세 {@code months} 개월이 되는 생일. 측정일(사흘 전)에도 같은 만 나이라 연령대가 바뀌지 않는다.
     */
    static LocalDate bornAged(LocalDate today, int years, int months) {
        return today.minusYears(years).minusMonths(months);
    }

    private static List<RawSlot> weekdaysAnd(
            String weekdayStart, int weekdayMinutes, String weekendDay, String weekendStart, int weekendMinutes) {
        return List.of(
                slot("MON", weekdayStart, weekdayMinutes),
                slot("TUE", weekdayStart, weekdayMinutes),
                slot("WED", weekdayStart, weekdayMinutes),
                slot("THU", weekdayStart, weekdayMinutes),
                slot("FRI", weekdayStart, weekdayMinutes),
                slot(weekendDay, weekendStart, weekendMinutes));
    }

    private static List<RawSlot> everyDay(
            String weekdayStart, int weekdayMinutes, String weekendStart, int weekendMinutes) {
        return List.of(
                slot("MON", weekdayStart, weekdayMinutes),
                slot("TUE", weekdayStart, weekdayMinutes),
                slot("WED", weekdayStart, weekdayMinutes),
                slot("THU", weekdayStart, weekdayMinutes),
                slot("FRI", weekdayStart, weekdayMinutes),
                slot("SAT", weekendStart, weekendMinutes),
                slot("SUN", weekendStart, weekendMinutes));
    }

    private static RawSlot slot(String day, String start, int minutes) {
        return new RawSlot(day, start, BigDecimal.valueOf(minutes));
    }
}
