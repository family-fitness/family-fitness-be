package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.application.ClaimResult;
import kr.ac.kookmin.familyfitness.identity.application.ConsentState;
import kr.ac.kookmin.familyfitness.identity.application.Invitation;
import kr.ac.kookmin.familyfitness.identity.application.InviteService;
import kr.ac.kookmin.familyfitness.identity.application.ProfileSettingsService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 초대·초대 코드 사용·참여 수준·보호자 동의·이름 · 생년월일 · 성별 고치기. */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {
    private final InviteService invites;
    private final ProfileSettingsService settings;

    public ProfileController(InviteService invites, ProfileSettingsService settings) {
        this.invites = invites;
        this.settings = settings;
    }

    /** 살아 있는 코드(만료 전 · 안 씀)가 있으면 그 코드와 만료 시각을 그대로 준다. 없을 때만 새로 만든다. */
    @PostMapping("/{profileId}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteResponse invite(CurrentUser user, @PathVariable UUID profileId) {
        Invitation invitation = invites.issueInvite(user.userId(), profileId);
        return new InviteResponse(
                invitation.claimCode().code(), invitation.claimCode().expiresAt(), invitation.shareUrl());
    }

    /**
     * 초대코드 쓰기. 자리 초대코드는 그 자리에 계정을 붙이고, 가족 초대코드는 함께 보낸 이름, 생년월일, 성별로 초대의 역할을 가진
     * 프로필을 만들어 붙인다. 응답 모양과 nextStep 은 두 코드가 같다(PARENT 는 SUPPORT_MODE, CHILD 는 HOME).
     */
    @PostMapping("/claim")
    public ClaimResponse claim(CurrentUser user, @Valid @RequestBody ClaimRequest request) {
        ClaimResult result = invites.claim(user.userId(), request.claimCode(), request.newMember());
        return new ClaimResponse(result.profileId(), result.familyId(), result.role(), result.nextStep());
    }

    @PatchMapping("/{profileId}/support-mode")
    public ProfileSummary supportMode(
            CurrentUser user, @PathVariable UUID profileId, @Valid @RequestBody SupportModeRequest request) {
        return settings.changeSupportMode(user.userId(), profileId, Objects.requireNonNull(request.supportMode()));
    }

    /**
     * 보호자 동의 주기 · 거두기 — 가족의 PARENT 만(403 NOT_SAME_FAMILY · NOT_A_PARENT). 자기 프로필은 403 SELF_CONSENT,
     * 대상이 보호자(PARENT)면 422 CONSENT_NOT_APPLICABLE, 만 14세 미만 보호자는 422 UNDER_14_NOT_ALLOWED.
     * 거둔 동의는 만 14세가 지나도 다시 동의할 때까지 막힌 채다.
     */
    @PatchMapping("/{profileId}/consent")
    public ConsentResponse consent(
            CurrentUser user, @PathVariable UUID profileId, @Valid @RequestBody ConsentRequest request) {
        ConsentState state = settings.updateConsent(user.userId(), profileId, request.toDomain());
        return new ConsentResponse(state.consentGiven(), state.consentAt(), state.consentBy(), state.measurable());
    }

    /**
     * 이름 · 생년월일 · 성별 고치기 — 가족의 PARENT 만(403 NOT_SAME_FAMILY · NOT_A_PARENT).
     * 계정 없는 프로필과 자기 프로필만 고친다(계정이 붙은 다른 사람이면 403 FORBIDDEN). 응답은 고친 뒤의 {@link ProfileSummary}.
     * PARENT 의 생년월일을 만 14세 미만으로 고치면 422 UNDER_14_NOT_ALLOWED. 아이 생일을 고쳐 만 14세 미만이 되면
     * consentRequired=true 가 되고, 동의 기록이 없으면 consentGiven · measurable 이 바로 false 다. 지난 측정의 백분위는 그대로다.
     */
    @PatchMapping("/{profileId}")
    public ProfileSummary edit(
            CurrentUser user, @PathVariable UUID profileId, @Valid @RequestBody EditProfileRequest request) {
        return settings.editProfile(user.userId(), profileId, request.toDomain());
    }
}
