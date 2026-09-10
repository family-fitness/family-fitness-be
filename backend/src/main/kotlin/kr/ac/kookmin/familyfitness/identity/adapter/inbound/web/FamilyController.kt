package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web

import jakarta.validation.Valid
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary
import kr.ac.kookmin.familyfitness.identity.application.CheerService
import kr.ac.kookmin.familyfitness.identity.application.FamilyService
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 가족 생성·구성원·응원. actor 는 토큰의 계정이고 대상은 경로·본문의 profileId 다. */
@RestController
@RequestMapping("/api/v1/families")
class FamilyController(
    private val families: FamilyService,
    private val cheers: CheerService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        user: CurrentUser,
        @Valid @RequestBody request: CreateFamilyRequest,
    ): FamilyCreatedResponse {
        val created =
            families.createFamily(
                userId = user.userId,
                familyName = request.familyName,
                ownerName = request.owner.name,
                birthDate = request.owner.birthDate,
                sex = request.owner.sex,
            )
        return FamilyCreatedResponse(created.familyId, created.familyName, created.ownerProfile)
    }

    @PostMapping("/{familyId}/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(
        user: CurrentUser,
        @PathVariable familyId: UUID,
        @Valid @RequestBody request: AddMemberRequest,
    ): ProfileSummary =
        families.addMember(
            userId = user.userId,
            familyId = familyId,
            name = request.name,
            birthDate = request.birthDate,
            sex = request.sex,
            role = request.role,
            guardianConsent = request.guardianConsent?.toDomain(),
        )

    @GetMapping("/{familyId}/profiles")
    fun profiles(
        user: CurrentUser,
        @PathVariable familyId: UUID,
    ): FamilyProfilesResponse {
        val result = families.profilesOf(user.userId, familyId)
        return FamilyProfilesResponse(result.familyId, result.familyName, result.profiles)
    }

    @PostMapping("/{familyId}/cheers")
    @ResponseStatus(HttpStatus.CREATED)
    fun cheer(
        user: CurrentUser,
        @PathVariable familyId: UUID,
        @Valid @RequestBody request: CheerRequest,
    ): CheerResponse =
        CheerResponse.of(
            cheers.cheer(
                userId = user.userId,
                familyId = familyId,
                fromProfileId = request.fromProfileId,
                toProfileId = request.toProfileId,
                message = request.message,
                emoji = request.emoji,
                missionId = request.missionId,
            ),
        )
}
