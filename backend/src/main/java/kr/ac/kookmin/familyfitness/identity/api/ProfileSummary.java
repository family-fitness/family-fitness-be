package kr.ac.kookmin.familyfitness.identity.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

/**
 * identity 가 밖으로 내보내는 유일한 공개 언어(Published Language).
 * 다른 모듈은 Profile 엔티티를 받지 않고 이 요약만 본다. API 응답에도 이 모양이 그대로 나간다.
 */
public record ProfileSummary(
        UUID profileId,
        UUID familyId,
        String name,
        ProfileRole role,
        AgeGroup ageGroup,
        boolean hasAccount,
        InviteStatus inviteStatus,
        @Nullable SupportMode supportMode,
        /** 만 4세 이상이고 (동의 불필요이거나) 동의가 살아 있는가 */
        boolean measurable,
        /** 만 14세 미만인가 */
        boolean consentRequired,
        /** 동의가 살아 있는가. 동의 불필요(성인)면 true */
        boolean consentGiven) {
    @JsonIgnore
    public boolean isParent() {
        return role == ProfileRole.PARENT;
    }
}
