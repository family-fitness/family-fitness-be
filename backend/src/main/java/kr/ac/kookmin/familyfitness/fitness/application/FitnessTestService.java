package kr.ac.kookmin.familyfitness.fitness.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
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
import kr.ac.kookmin.familyfitness.shared.domain.Ages;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 측정 회차 등록·조회. 호출 계정(actor)은 대상 프로필과 같은 가족이어야 한다 — 부모가 아이 기록을 대리 입력한다.
 * 규칙 순서: 같은 가족 → 미래 날짜 → 만 4세 미만 → 보호자 동의 → 같은 날짜 중복 → 항목 규칙(애그리거트).
 */
@Service
public class FitnessTestService {
    private final FitnessTestRepository tests;
    private final NormCatalog norms;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profileQuery;
    private final Clock clock;
    private final ZoneId zone;

    public FitnessTestService(
            FitnessTestRepository tests,
            NormCatalog norms,
            FamilyAccess familyAccess,
            ProfileQuery profileQuery,
            Clock clock,
            ZoneId zone) {
        this.tests = tests;
        this.norms = norms;
        this.familyAccess = familyAccess;
        this.profileQuery = profileQuery;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public FitnessTest register(UUID actorId, UUID profileId, RegisterFitnessTestCommand command) {
        ProfileSummary summary = familyAccess.requireSameFamilyAsProfile(actorId, profileId);
        ProfileDetails details = profileQuery.findDetails(profileId);
        if (details == null) throw new ProfileNotFoundException(profileId);

        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (command.testedOn().isAfter(today)) throw new FutureTestDateException(command.testedOn());

        int ageAtTest = Ages.fullYears(details.birthDate(), command.testedOn());
        int ageMonthsAtTest = Ages.fullMonths(details.birthDate(), command.testedOn());
        if (ageAtTest < Ages.MEASURABLE_FROM_YEARS) throw new NotMeasurableException();
        requireConsent(summary);

        if (tests.existsByProfileIdAndTestedOn(profileId, command.testedOn())) {
            throw new DuplicateDateException(command.testedOn());
        }

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
        return tests.save(test);
    }

    /** 최신 회차. 이력이 없으면 null — 웹 어댑터가 빈 응답(200)으로 바꾼다. */
    @Transactional(readOnly = true)
    public @Nullable FitnessTest latest(UUID actorId, UUID profileId) {
        familyAccess.requireSameFamilyAsProfile(actorId, profileId);
        return tests.findLatestByProfileId(profileId);
    }

    /** 만 14세 미만(consentRequired)인데 동의가 없거나 철회됐으면 저장하지 않는다. */
    private void requireConsent(ProfileSummary summary) {
        if (summary.consentRequired() && !summary.consentGiven()) throw new ConsentRequiredException();
    }
}
