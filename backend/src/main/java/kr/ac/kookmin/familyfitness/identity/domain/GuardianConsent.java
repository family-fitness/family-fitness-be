package kr.ac.kookmin.familyfitness.identity.domain;

import java.time.LocalDate;
import kr.ac.kookmin.familyfitness.shared.domain.Ages;

/**
 * 보호자 동의 판정 값 객체. 만 14세 미만이면 개인정보·건강정보 둘 다 true 여야 저장된다.
 * 서버가 동의를 자동으로 찍지 않는다 — 부모가 보낸 값을 그대로 판정만 한다.
 */
public record GuardianConsent(boolean personalData, boolean healthData) {
    public boolean isComplete() {
        return personalData && healthData;
    }

    public static boolean isRequired(LocalDate birthDate, LocalDate today) {
        return Ages.requiresGuardianConsent(birthDate, today);
    }
}
