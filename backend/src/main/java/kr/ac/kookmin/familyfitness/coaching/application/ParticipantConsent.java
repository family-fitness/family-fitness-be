package kr.ac.kookmin.familyfitness.coaching.application;

import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;

/**
 * 보호자 동의 판정. 동의가 필요한데(consentRequired) 살아 있지 않으면(!consentGiven) 그 프로필은
 * AI 요청 · 대체 편성 · 승인 복사 · 활동 기록에서 뺀다. 측정(FitnessTestService)과 같은 조건이다.
 */
final class ParticipantConsent {
    private ParticipantConsent() {}

    static boolean missing(ProfileSummary profile) {
        return profile.consentRequired() && !profile.consentGiven();
    }

    /** {@link ProfileDetails#consentGiven()} 은 identity 가 「동의 불필요이거나 살아 있음」으로 채운다(Profile.consentGiven). */
    static boolean missing(ProfileDetails profile) {
        return !profile.consentGiven();
    }

    static void require(ProfileSummary profile) {
        if (missing(profile)) throw new ParticipantConsentRequiredException();
    }
}
