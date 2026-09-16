package kr.ac.kookmin.familyfitness.shared.domain;

import java.time.LocalDate;
import java.time.Period;

/** 만 나이·개월 계산과 연령 규칙을 한곳에 둔다. */
public final class Ages {
    public static final int MEASURABLE_FROM_YEARS = 4;
    public static final int GUARDIAN_CONSENT_UNDER_YEARS = 14;

    private Ages() {}

    public static int fullYears(LocalDate birthDate, LocalDate on) {
        return Math.max(Period.between(birthDate, on).getYears(), 0);
    }

    public static int fullMonths(LocalDate birthDate, LocalDate on) {
        return Math.max((int) Period.between(birthDate, on).toTotalMonths(), 0);
    }

    /** 만 4세 미만은 규준이 없어 측정 대상이 아니다. */
    public static boolean isMeasurable(LocalDate birthDate, LocalDate on) {
        return fullYears(birthDate, on) >= MEASURABLE_FROM_YEARS;
    }

    /** 만 14세 미만은 보호자 동의가 있어야 저장된다. */
    public static boolean requiresGuardianConsent(LocalDate birthDate, LocalDate on) {
        return fullYears(birthDate, on) < GUARDIAN_CONSENT_UNDER_YEARS;
    }
}
