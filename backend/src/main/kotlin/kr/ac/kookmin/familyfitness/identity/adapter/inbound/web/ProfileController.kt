package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web

import jakarta.validation.Valid
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.identity.application.InviteService
import kr.ac.kookmin.familyfitness.identity.application.ProfileSettingsService
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 초대·초대 코드 사용·참여 수준·보호자 동의. */
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(
    private val invites: InviteService,
    private val settings: ProfileSettingsService,
) {
    @PostMapping("/{profileId}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(
        user: CurrentUser,
        @PathVariable profileId: UUID,
    ): InviteResponse {
        val invitation = invites.issueInvite(user.userId, profileId)
        return InviteResponse(invitation.claimCode.code, invitation.claimCode.expiresAt, invitation.shareUrl)
    }

    @PostMapping("/claim")
    fun claim(
        user: CurrentUser,
        @Valid @RequestBody request: ClaimRequest,
    ): ClaimResponse {
        val result = invites.claim(user.userId, request.claimCode)
        return ClaimResponse(result.profileId, result.familyId, result.role, result.nextStep)
    }

    @PatchMapping("/{profileId}/support-mode")
    fun supportMode(
        user: CurrentUser,
        @PathVariable profileId: UUID,
        @Valid @RequestBody request: SupportModeRequest,
    ): ProfileSummary = settings.changeSupportMode(user.userId, profileId, request.supportMode)

    @PatchMapping("/{profileId}/consent")
    fun consent(
        user: CurrentUser,
        @PathVariable profileId: UUID,
        @Valid @RequestBody request: ConsentRequest,
    ): ConsentResponse {
        val state = settings.updateConsent(user.userId, profileId, request.toDomain())
        return ConsentResponse(state.consentGiven, state.consentAt, state.consentBy, state.measurable)
    }
}
