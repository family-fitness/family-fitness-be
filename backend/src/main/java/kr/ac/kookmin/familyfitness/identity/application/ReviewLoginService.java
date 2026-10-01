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
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 심사용 계정 로그인. 부를 때마다 새 계정을 만들고 그 계정으로 로그인시킨다 — 심사위원끼리 서로의 기록을 건드리지 않게.
 * 계정은 (REVIEW, 「review-」 + 무작위)라 같은 계정으로 다시 들어오는 길은 없다. 그 브라우저의 리프레시 토큰(30일)으로만 이어서 본다.
 *
 * <p>kind({@link ReviewLoginKind})마다 만드는 것:
 * <ul>
 *   <li>FAMILY — 새 계정 + 이 계정이 보호자(엄마)인 체험 가족. nextStep HOME
 *   <li>FRESH — 새 계정만. nextStep CREATE_FAMILY. 가족 · 아이 · 측정은 심사위원이 평소 가입 흐름으로 넣는다
 *   <li>INVITED — 가짜 보호자 계정(REVIEW, 「review-guardian-」 + 무작위, 아무도 이 계정으로 로그인하지 않는다)이 엄마인 체험 가족 +
 *       새 계정. 체험 가족의 아빠 자리 초대코드를 가짜 보호자 이름으로 내고, 새 계정의 nextStep 은 CLAIM 이다. 코드로 합류하면
 *       심사위원이 아빠(보호자)가 된다 — 개발용 「초대받은 계정」(시드의 demo-parent-2 + 초대코드 K7M2QT)과 같은 흐름이다
 * </ul>
 * 세 kind 모두 계정이 심사용 계정이라 한도 · 체험 리그 방이 똑같이 걸린다.
 *
 * <p>체험 가족 「체험 가족」:
 * <ul>
 *   <li>엄마 — 가족을 만든 보호자(FAMILY 는 이 계정, INVITED 는 가짜 보호자), 만 38세 여, 참여 방식 FULL
 *   <li>아빠 — 보호자, 만 40세 남, 계정 없음(INVITED 는 이 자리에 초대코드가 나 있다)
 *   <li>하윤 — 아이, 만 11세 여(유소년), 계정 없음, 보호자 동의 있음. 사흘 전에 유소년 종목 일곱 가지와 키 · 몸무게 · 허리둘레를
 *       재 둬서 종합 등급(2등급)이 나온다
 *   <li>서준 — 아이, 만 6세 남(유아기), 계정 없음, 보호자 동의 있음. 사흘 전에 유아기 종목 몇 가지만 재 뒀다
 * </ul>
 * 네 사람 모두 운동할 수 있는 시간을 적어 둔다. 분은 FE 가 고를 수 있는 값(10 · 20 · 30 · 40)만 쓴다 — 15분이면 편성 화면이 10분 칩을 켠다.
 * 측정과 지난 2주 기록은 이 모듈이 넣지 않는다. {@link ReviewFamilyCreated} 를 발행하면 듣는 모듈이 같은 트랜잭션에서 넣는다.
 * FAMILY 와 INVITED 는 둘 다 이 가족을 꾸미므로 둘 다 기록이 찬다. 오늘 하윤 미션은 끝내지 않은 채로 남는다.
 *
 * <p>새 계정을 모두 합쳐 한 시간에 만드는 수가 한도에 닿으면 새로 만들지 않고, 그 한 시간에 만든 심사용 계정 하나로 들인다
 * ({@link ReviewLoginLimiter}) — 이때만 심사위원끼리 계정이 겹칠 수 있다.
 *
 * <p>가족 · 구성원 · 참여 방식 · 운동할 수 있는 시간은 화면이 부르는 서비스를 그대로 거친다. 규칙(나이 · 동의 · 칸 값)이 사람이 만든
 * 가족과 똑같이 걸린다. 한 트랜잭션이라 중간에 실패하면 계정까지 남지 않는다.
 */
@Service
@Transactional
public class ReviewLoginService {
    static final String PROVIDER_USER_PREFIX = "review-";
    /** INVITED 의 가짜 보호자 계정. 심사위원이 아니고 아무도 이 계정으로 로그인하지 않는다. */
    static final String GUARDIAN_USER_PREFIX = "review-guardian-";

    static final String FAMILY_NAME = "체험 가족";

    private static final Logger log = LoggerFactory.getLogger(ReviewLoginService.class);

    private final ReviewLoginLimiter limiter;
    private final UserRegistrationService registration;
    private final FamilyService families;
    private final ProfileSettingsService settings;
    private final AvailabilityService availability;
    private final AuthService auth;
    private final InviteService invites;
    private final ApplicationEventPublisher events;
    private final IdentityClock clock;
    private final AppProperties.ReviewLogin window;

    public ReviewLoginService(
            ReviewLoginLimiter limiter,
            UserRegistrationService registration,
            FamilyService families,
            ProfileSettingsService settings,
            AvailabilityService availability,
            AuthService auth,
            InviteService invites,
            ApplicationEventPublisher events,
            IdentityClock clock,
            AppProperties properties) {
        this.limiter = limiter;
        this.registration = registration;
        this.families = families;
        this.settings = settings;
        this.availability = availability;
        this.auth = auth;
        this.invites = invites;
        this.events = events;
        this.clock = clock;
        this.window = properties.auth().reviewLogin();
    }

    /**
     * 오늘(app.timezone) 심사용 계정 로그인을 받는지. 끝나는 날(app.auth.review-login.until)이 지났으면 켜져 있어도 받지 않는다 —
     * 컨트롤러가 꺼진 것과 같게 404 를 준다. 운영은 기본 2026-10-31 이다(APP_AUTH_REVIEW_LOGIN_UNTIL).
     */
    public boolean isOpen() {
        return window.openOn(clock.today());
    }

    /**
     * 같은 IP 가 한 시간에 60번을 넘기면(kind 세 가지를 합쳐 센다) 계정을 만들기 전에 429 TOO_MANY. 모두 합쳐 한 시간에 새 계정 300개를 넘기면
     * 그 IP 가 이 한 시간에 같은 kind 로 만든 계정으로 들이고, 그런 계정이 없으면 새로 만든다({@link ReviewLoginLimiter}). 어느 IP 로
     * 셌는지 끝자리를 가려 로그에 남긴다({@link ReviewLoginLimiter#maskedForLog}) — 배포 뒤 X-Forwarded-For 가 제대로 오는지 이 줄로
     * 본다(README). 보관 기간은 README 「로그」.
     */
    public ReviewLoginResult login(String clientIp, ReviewLoginKind kind) {
        ReviewLoginLimiter.Admission admission = limiter.acquire(clientIp, kind);
        UUID reuse = admission.reuse();
        if (reuse != null) {
            log.info(
                    "심사용 계정 로그인: IP {} · {} · 새 계정 한도가 차 이 IP 가 만든 계정 {} 로 들인다",
                    ReviewLoginLimiter.maskedForLog(clientIp),
                    kind,
                    reuse);
            return started(reuse, admission.inviteCode());
        }
        UUID userId = newReviewUser(PROVIDER_USER_PREFIX);
        String inviteCode =
                switch (kind) {
                    case FAMILY -> {
                        createFamily(userId);
                        yield null;
                    }
                    case FRESH -> null;
                    case INVITED -> invitedFamily();
                };
        afterCommit(() -> limiter.remember(clientIp, kind, userId, inviteCode));
        log.info("심사용 계정 로그인: IP {} · {} · 새 계정 {}", ReviewLoginLimiter.maskedForLog(clientIp), kind, userId);
        return started(userId, inviteCode);
    }

    /**
     * 초대코드를 실어 로그인시킨다. 가족이 없는 계정이면 nextStep CLAIM 이다. 다시 준 INVITED 계정이 이미 합류했으면 가족이 있어
     * CLAIM 이 아니고, 이미 쓴 코드는 싣지 않는다.
     */
    private ReviewLoginResult started(UUID userId, @Nullable String inviteCode) {
        AuthResult result = auth.startSession(userId, inviteCode);
        return new ReviewLoginResult(result, result.session().nextStep() == NextStep.CLAIM ? inviteCode : null);
    }

    private UUID newReviewUser(String prefix) {
        return registration
                .registerOrGet(User.PROVIDER_REVIEW, prefix + UUID.randomUUID(), null)
                .id();
    }

    /** 가짜 보호자 계정으로 체험 가족을 꾸미고, 아빠 자리 초대코드를 가짜 보호자 이름으로 낸다. */
    private String invitedFamily() {
        UUID guardian = newReviewUser(GUARDIAN_USER_PREFIX);
        UUID dad = createFamily(guardian);
        return invites.issueInvite(guardian, dad).claimCode().code();
    }

    /** 커밋된 뒤에 돌린다 — 만들다 되돌린 계정을 나눠 줄 후보로 적지 않게. 트랜잭션 밖이면 곧바로. */
    private static void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /** 체험 가족을 꾸민다. 돌려주는 값은 아빠 프로필 id — INVITED 가 이 자리에 초대코드를 낸다. */
    private UUID createFamily(UUID userId) {
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
        return dad;
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
