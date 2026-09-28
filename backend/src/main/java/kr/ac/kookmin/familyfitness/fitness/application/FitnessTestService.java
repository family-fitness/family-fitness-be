package kr.ac.kookmin.familyfitness.fitness.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered;
import kr.ac.kookmin.familyfitness.fitness.api.FitnessTestRegistered.Round;
import kr.ac.kookmin.familyfitness.fitness.application.port.FitnessTestRepository;
import kr.ac.kookmin.familyfitness.fitness.domain.ConsentRequiredException;
import kr.ac.kookmin.familyfitness.fitness.domain.DuplicateDateException;
import kr.ac.kookmin.familyfitness.fitness.domain.FitnessTest;
import kr.ac.kookmin.familyfitness.fitness.domain.FutureTestDateException;
import kr.ac.kookmin.familyfitness.fitness.domain.NotMeasurableException;
import kr.ac.kookmin.familyfitness.fitness.domain.PercentileCalculator;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Ages;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 측정 회차 등록·조회.
 * <ul>
 *   <li>등록: 호출 계정이 대상 프로필 가족의 PARENT 여야 한다 — 부모가 자기 것과 아이 것을 입력한다. CHILD 계정은 자기 것도
 *       403 NOT_A_PARENT(FE 는 측정 화면을 부모 화면에만 둔다). 규칙 순서: 같은 가족 → 보호자 → 미래 날짜 → 만 4세 미만 →
 *       보호자 동의 → 같은 날짜 중복 → 항목 규칙(애그리거트 — 항목 · 연령대 · 범위).
 *   <li>조회(latest · 이력): 같은 가족이면 된다. 호출 계정이 CHILD 면 {@code parentScope=false} 로 돌려주고, 웹 어댑터가
 *       부모만 볼 값(등급 · 요인별 백분위 · 가장 낮은 · 높은 항목 · 코치 방향 · 체중 · 「상위 n%」 문구)을 비운다.
 *       overallPercentile 은 남긴다(아이 화면의 「신체 점수」). 부모 계정은 아이 모드여도 다 받는다 — 서버가 화면을 알 수 없다.
 * </ul>
 * 등록한 뒤 {@link FitnessTestRegistered} 를 발행한다.
 */
@Service
public class FitnessTestService {
    /**
     * 측정 이력 한 번에 주는 최대 회차 수. 영상 목록(VideoService.MAX_PAGE_SIZE)과 같은 값.
     * 바꾸면 FitnessTestEntity 의 항목 배치 크기(ITEMS_BATCH_SIZE)도 같이 맞춘다.
     */
    public static final int HISTORY_MAX_SIZE = 100;

    private final FitnessTestRepository tests;
    private final NormCatalog norms;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId zone;

    public FitnessTestService(
            FitnessTestRepository tests,
            NormCatalog norms,
            FamilyAccess familyAccess,
            ProfileQuery profileQuery,
            ApplicationEventPublisher events,
            Clock clock,
            ZoneId zone) {
        this.tests = tests;
        this.norms = norms;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public FitnessTest register(UUID actorId, UUID profileId, RegisterFitnessTestCommand command) {
        familyAccess.requireParentOfProfile(actorId, profileId);
        ProfileSummary summary = familyAccess.requireSameFamilyAsProfile(actorId, profileId);
        ProfileDetails details = details(profileId);

        int ageAtTest = measurableAgeOn(details, command.testedOn());
        int ageMonthsAtTest = Ages.fullMonths(details.birthDate(), command.testedOn());
        requireConsent(summary);

        if (tests.existsByProfileIdAndTestedOn(profileId, command.testedOn())) {
            throw new DuplicateDateException(command.testedOn());
        }

        FitnessTest earliestBefore = tests.findEarliestByProfileId(profileId);
        PercentileCalculator calculator = norms.calculator();
        FitnessTest test = FitnessTest.register(
                UUID.randomUUID(),
                profileId,
                command.testedOn(),
                command.source(),
                ageAtTest,
                command.heightCm(),
                command.weightKg(),
                command.measurements(),
                (item, value) ->
                        calculator.percentile(item, details.sex(), ageAtTest, value.doubleValue(), ageMonthsAtTest),
                clock.instant());
        FitnessTest saved = tests.save(test);
        events.publishEvent(new FitnessTestRegistered(
                profileId, saved.getId(), saved.getTestedOn(), remeasuredBy(earliestBefore, saved)));
        return saved;
    }

    /**
     * 이 등록으로 새로 「다시 잰 회차」 가 된 회차. 다시 잰 회차 = testedOn 이 가장 이른 회차를 뺀 전부(FE 목 규칙).
     * 같은 날짜 회차는 막혀 있고 회차를 지우거나 날짜를 고치는 길이 없어서, 가장 이른 회차는 더 이른 날로만 바뀐다.
     * 그래서 등록 한 번에 다시 잰 회차가 되는 것은 정확히 하나다.
     * ① 저장 전 회차가 없으면 없음 ② 저장 전 가장 이른 회차가 새 회차보다 이르면 새 회차
     * ③ 새 회차가 더 이르면(지난 날짜를 나중에 적음) 저장 전 가장 이르던 회차.
     */
    private static @Nullable Round remeasuredBy(@Nullable FitnessTest earliestBefore, FitnessTest saved) {
        if (earliestBefore == null) return null;
        FitnessTest remeasured = earliestBefore.getTestedOn().isBefore(saved.getTestedOn()) ? saved : earliestBefore;
        return new Round(remeasured.getId(), remeasured.getTestedOn());
    }

    /** 최신 회차. 이력이 없으면 test 가 null — 웹 어댑터가 빈 응답(200)으로 바꾼다. */
    @Transactional(readOnly = true)
    public LatestFitnessView latest(UUID actorId, UUID profileId) {
        ProfileSummary target = familyAccess.requireSameFamilyAsProfile(actorId, profileId);
        return new LatestFitnessView(tests.findLatestByProfileId(profileId), parentScope(actorId, target));
    }

    /**
     * 측정 이력. testedOn 이 늦은 회차부터 최대 size 개, 없으면 빈 목록. 권한은 latest 와 같다(같은 가족).
     * size 범위는 영상 목록(/videos)과 같다 — 기본 20(컨트롤러) · 1~{@link #HISTORY_MAX_SIZE}, 밖이면 400.
     */
    @Transactional(readOnly = true)
    public FitnessHistoryView history(UUID actorId, UUID profileId, int size) {
        if (size < 1 || size > HISTORY_MAX_SIZE) {
            throw new IllegalArgumentException("size 는 1~" + HISTORY_MAX_SIZE + " 이어야 합니다");
        }
        ProfileSummary target = familyAccess.requireSameFamilyAsProfile(actorId, profileId);
        return new FitnessHistoryView(tests.findRecentByProfileId(profileId, size), parentScope(actorId, target));
    }

    /**
     * 측정 폼이 받을 항목표의 연령대 — 그 프로필의 {@code testedOn} 기준 만 나이로 정한다. 등록 검사와 같은 셈이라
     * 생일이 연령대 경계(7 · 13 · 19 · 65세)를 넘은 뒤 지난 날짜 결과지를 옮겨 적어도 폼과 검사가 어긋나지 않는다.
     * 권한 · 날짜 규칙도 등록과 같다: 보호자만(403 NOT_A_PARENT), testedOn 이 없으면 오늘(KST), 미래면 400, 만 4세 미만이면
     * 422 NOT_MEASURABLE.
     */
    @Transactional(readOnly = true)
    public AgeGroup ageGroupOn(UUID actorId, UUID profileId, @Nullable LocalDate testedOn) {
        familyAccess.requireParentOfProfile(actorId, profileId);
        LocalDate on = testedOn != null ? testedOn : today();
        return AgeGroup.ofAge(measurableAgeOn(details(profileId), on));
    }

    private ProfileDetails details(UUID profileId) {
        ProfileDetails details = profileQuery.findDetails(profileId);
        if (details == null) throw new ProfileNotFoundException(profileId);
        return details;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /** testedOn 기준 만 나이. 미래 날짜는 400, 만 4세 미만은 규준이 없어 422 NOT_MEASURABLE. */
    private int measurableAgeOn(ProfileDetails details, LocalDate testedOn) {
        if (testedOn.isAfter(today())) throw new FutureTestDateException(testedOn);
        int age = Ages.fullYears(details.birthDate(), testedOn);
        if (age < Ages.MEASURABLE_FROM_YEARS) throw new NotMeasurableException();
        return age;
    }

    /** 호출 계정의 그 가족 안 프로필이 PARENT 면 부모만 볼 값까지 준다. CHILD 계정이면 false. */
    private boolean parentScope(UUID actorId, ProfileSummary target) {
        return familyAccess.requireMember(actorId, target.familyId()).isParent();
    }

    /** 만 14세 미만(consentRequired)인데 동의가 없거나 철회됐으면 저장하지 않는다. */
    private void requireConsent(ProfileSummary summary) {
        if (summary.consentRequired() && !summary.consentGiven()) throw new ConsentRequiredException();
    }
}
