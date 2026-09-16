package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;

/** 로그인 뒤 프론트가 갈 화면. 프로필 0개 + 코드 없음 → 가족 만들기, 0개 + 코드 있음 → 초대 코드 입력, 있음 → 홈. */
public enum NextStep {
    CREATE_FAMILY,
    CLAIM,
    HOME,
    SUPPORT_MODE;

    public static NextStep afterLogin(List<ProfileSummary> profiles, @Nullable String claimCode) {
        if (!profiles.isEmpty()) return HOME;
        if (claimCode == null || claimCode.isBlank()) return CREATE_FAMILY;
        return CLAIM;
    }
}
