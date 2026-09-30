package kr.ac.kookmin.familyfitness.identity.application;

import java.util.List;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 뒤 프론트가 갈 화면. 판정 순서: 프로필 있음 → (초대로 붙은 보호자가 참여 방식을 아직 안 골랐으면 SUPPORT_MODE, 아니면 HOME)
 * · 프로필 0개 + 코드 없음 → 가족 만들기 · 0개 + 코드 있음 → 초대 코드 입력.
 */
public enum NextStep {
    CREATE_FAMILY,
    CLAIM,
    HOME,
    SUPPORT_MODE;

    /** @param profiles 이 계정에 붙은 프로필(한 계정 한 가족이라 0~1개) */
    public static NextStep afterLogin(List<ProfileSummary> profiles, @Nullable String claimCode) {
        if (!profiles.isEmpty()) return choosingSupportMode(profiles.getFirst()) ? SUPPORT_MODE : HOME;
        if (claimCode == null || claimCode.isBlank()) return CREATE_FAMILY;
        return CLAIM;
    }

    /**
     * 초대 코드로 붙은 보호자(inviteStatus CLAIMED)가 참여 방식을 고르기 전에 앱을 닫았다(QA CT-08). 초대를 쓴 응답만 SUPPORT_MODE 를
     * 줬기 때문에 다시 열면 묻지 않고 홈으로 갔다. 가족을 만든 보호자는 초대로 붙지 않았으므로(NONE) supportMode 가 null 이어도 묻지 않는다.
     */
    private static boolean choosingSupportMode(ProfileSummary self) {
        return self.isParent() && self.inviteStatus() == InviteStatus.CLAIMED && self.supportMode() == null;
    }
}
