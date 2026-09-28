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

/** 초대·초대 코드 사용·참여 수준·보호자 동의. */
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

    @PostMapping("/claim")
    public ClaimResponse claim(CurrentUser user, @Valid @RequestBody ClaimRequest request) {
        ClaimResult result = invites.claim(user.userId(), request.claimCode());
        return new ClaimResponse(result.profileId(), result.familyId(), result.role(), result.nextStep());
    }

    @PatchMapping("/{profileId}/support-mode")
    public ProfileSummary supportMode(
            CurrentUser user, @PathVariable UUID profileId, @Valid @RequestBody SupportModeRequest request) {
        return settings.changeSupportMode(user.userId(), profileId, Objects.requireNonNull(request.supportMode()));
    }

    @PatchMapping("/{profileId}/consent")
    public ConsentResponse consent(
            CurrentUser user, @PathVariable UUID profileId, @Valid @RequestBody ConsentRequest request) {
        ConsentState state = settings.updateConsent(user.userId(), profileId, request.toDomain());
        return new ConsentResponse(state.consentGiven(), state.consentAt(), state.consentBy(), state.measurable());
    }
}
