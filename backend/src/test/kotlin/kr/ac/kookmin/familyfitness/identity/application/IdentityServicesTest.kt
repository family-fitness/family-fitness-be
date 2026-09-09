package kr.ac.kookmin.familyfitness.identity.application

import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyClaimedException
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyInFamilyException
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyMemberException
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeExpiredException
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeNotFoundException
import kr.ac.kookmin.familyfitness.identity.domain.FamilyAccessDeniedException
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsentRequiredException
import kr.ac.kookmin.familyfitness.identity.domain.NotFamilyMemberException
import kr.ac.kookmin.familyfitness.identity.domain.NotOwnProfileException
import kr.ac.kookmin.familyfitness.identity.domain.SelfCheerException
import kr.ac.kookmin.familyfitness.identity.domain.SupportModeNotApplicableException
import kr.ac.kookmin.familyfitness.identity.domain.TooManyCheersException
import kr.ac.kookmin.familyfitness.shared.config.AppProperties
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** 유스케이스 서비스 규칙. 저장소는 인메모리 가짜, 시각은 고정. */
class IdentityServicesTest {
    private val clock = MutableClock(Instant.parse("2026-09-08T10:00:00Z"))
    private val identityClock = clock.identityClock()
    private val families = InMemoryFamilyRepository()
    private val cheers = InMemoryCheerRepository()
    private val summaries = ProfileSummaries(identityClock)
    private val props = AppProperties(frontendBaseUrl = "https://app.example.com/")

    private val familyService = FamilyService(families, summaries, identityClock)
    private val inviteService = InviteService(families, props, identityClock)
    private val settingsService = ProfileSettingsService(families, summaries, identityClock)
    private val cheerService = CheerService(families, cheers, identityClock)

    private val parentUser = UUID.randomUUID()
    private val today = LocalDate.of(2026, 9, 8)

    private fun createFamily() = familyService.createFamily(parentUser, "우리 가족", "엄마", LocalDate.of(1988, 3, 1), Sex.F)

    private fun addChild(
        familyId: UUID,
        consent: GuardianConsent? = GuardianConsent(true, true),
        birthDate: LocalDate = LocalDate.of(2018, 5, 20),
        name: String = "첫째",
    ) = familyService.addMember(parentUser, familyId, name, birthDate, Sex.M, ProfileRole.CHILD, consent)

    @Nested
    inner class CreateFamily {
        @Test
        fun `가족을 만들면 owner PARENT 요약이 돌아오고 저장된다`() {
            val created = createFamily()

            assertThat(created.familyName).isEqualTo("우리 가족")
            val owner = created.ownerProfile
            assertThat(owner.familyId).isEqualTo(created.familyId)
            assertThat(owner.role).isEqualTo(ProfileRole.PARENT)
            assertThat(owner.hasAccount).isTrue()
            assertThat(owner.inviteStatus).isEqualTo(InviteStatus.NONE)
            assertThat(owner.ageGroup).isEqualTo(AgeGroup.ADULT)
            assertThat(owner.consentRequired).isFalse()
            assertThat(owner.consentGiven).isTrue()
            assertThat(owner.measurable).isTrue()
            assertThat(owner.supportMode).isNull()
            assertThat(families.findById(created.familyId)?.profiles).hasSize(1)
        }

        @Test
        fun `이미 프로필이 붙은 계정은 가족을 또 만들 수 없다`() {
            createFamily()

            assertThrows<AlreadyInFamilyException> { createFamily() }
            assertThat(families.families).hasSize(1)
        }

        @Test
        fun `미래 생년월일은 거부한다`() {
            assertThrows<IllegalArgumentException> {
                familyService.createFamily(parentUser, "우리 가족", "엄마", today.plusDays(1), Sex.F)
            }
        }
    }

    @Nested
    inner class AddMember {
        @Test
        fun `동의가 있는 아이는 측정 가능하고 계정은 없다`() {
            val family = createFamily()

            val child = addChild(family.familyId)

            assertThat(child.role).isEqualTo(ProfileRole.CHILD)
            assertThat(child.hasAccount).isFalse()
            assertThat(child.ageGroup).isEqualTo(AgeGroup.YOUTH)
            assertThat(child.consentRequired).isTrue()
            assertThat(child.consentGiven).isTrue()
            assertThat(child.measurable).isTrue()
            assertThat(child.inviteStatus).isEqualTo(InviteStatus.NONE)
            assertThat(families.findById(family.familyId)?.profiles).hasSize(2)
        }

        @Test
        fun `만 14세 미만은 동의가 없거나 불완전하면 저장하지 않는다`() {
            val family = createFamily()

            assertThrows<GuardianConsentRequiredException> { addChild(family.familyId, consent = null) }
            assertThrows<GuardianConsentRequiredException> { addChild(family.familyId, consent = GuardianConsent(true, false)) }
            assertThat(families.findById(family.familyId)?.profiles).hasSize(1)
        }

        @Test
        fun `만 4세 미만도 프로필은 생기지만 측정 대상이 아니다`() {
            val family = createFamily()

            val toddler = addChild(family.familyId, birthDate = today.minusYears(3))

            assertThat(toddler.ageGroup).isEqualTo(AgeGroup.TODDLER)
            assertThat(toddler.consentGiven).isTrue()
            assertThat(toddler.measurable).isFalse()
        }

        @Test
        fun `다른 가족 계정이나 아이 계정은 구성원을 추가할 수 없다`() {
            val family = createFamily()
            val stranger = UUID.randomUUID()

            assertThrows<FamilyAccessDeniedException> {
                familyService.addMember(
                    stranger,
                    family.familyId,
                    "아이",
                    LocalDate.of(2018, 5, 20),
                    Sex.M,
                    ProfileRole.CHILD,
                    GuardianConsent(true, true),
                )
            }
            assertThrows<FamilyNotFoundException> {
                familyService.addMember(parentUser, UUID.randomUUID(), "아이", LocalDate.of(2018, 5, 20), Sex.M, ProfileRole.CHILD, null)
            }

            val child = addChild(family.familyId)
            val childUser = UUID.randomUUID()
            families.attachUserIfUnclaimed(child.profileId, childUser, clock.instant)
            assertThrows<NotAParentException> {
                familyService.addMember(
                    childUser,
                    family.familyId,
                    "동생",
                    LocalDate.of(2020, 1, 1),
                    Sex.F,
                    ProfileRole.CHILD,
                    GuardianConsent(true, true),
                )
            }
        }

        @Test
        fun `구성원 목록은 가족 구성원만 본다`() {
            val family = createFamily()
            addChild(family.familyId)

            val listed = familyService.profilesOf(parentUser, family.familyId)
            assertThat(listed.familyName).isEqualTo("우리 가족")
            assertThat(listed.profiles).hasSize(2)

            assertThrows<FamilyAccessDeniedException> { familyService.profilesOf(UUID.randomUUID(), family.familyId) }
        }
    }

    @Nested
    inner class Invite {
        @Test
        fun `초대 코드는 6자리이고 7일 뒤 만료되며 공유 URL 에 실린다`() {
            val family = createFamily()
            val child = addChild(family.familyId)

            val invitation = inviteService.issueInvite(parentUser, child.profileId)

            assertThat(invitation.claimCode.code).matches("[A-HJ-NP-Z2-9]{6}")
            assertThat(invitation.claimCode.expiresAt).isEqualTo(clock.instant.plus(Duration.ofDays(7)))
            assertThat(invitation.shareUrl).isEqualTo("https://app.example.com/claim?code=" + invitation.claimCode.code)
            assertThat(summaries.summary(families.findByProfileId(child.profileId)!!.profile(child.profileId)).inviteStatus)
                .isEqualTo(InviteStatus.ISSUED)
        }

        @Test
        fun `계정이 붙은 프로필·남의 가족·없는 프로필에는 발급하지 않는다`() {
            val family = createFamily()
            val child = addChild(family.familyId)

            assertThrows<AlreadyClaimedException> { inviteService.issueInvite(parentUser, family.ownerProfile.profileId) }
            assertThrows<FamilyAccessDeniedException> { inviteService.issueInvite(UUID.randomUUID(), child.profileId) }
            assertThrows<ProfileNotFoundException> { inviteService.issueInvite(parentUser, UUID.randomUUID()) }
        }

        @Test
        fun `코드를 쓰면 계정이 붙고 아이는 HOME 부모는 SUPPORT_MODE 로 간다`() {
            val family = createFamily()
            val child = addChild(family.familyId)
            val dad = familyService.addMember(parentUser, family.familyId, "아빠", LocalDate.of(1986, 1, 1), Sex.M, ProfileRole.PARENT, null)
            val childCode = inviteService.issueInvite(parentUser, child.profileId).claimCode.code
            val dadCode = inviteService.issueInvite(parentUser, dad.profileId).claimCode.code
            val childUser = UUID.randomUUID()
            val dadUser = UUID.randomUUID()

            val childClaim = inviteService.claim(childUser, " " + childCode.lowercase() + " ")
            val dadClaim = inviteService.claim(dadUser, dadCode)

            assertThat(childClaim.profileId).isEqualTo(child.profileId)
            assertThat(childClaim.familyId).isEqualTo(family.familyId)
            assertThat(childClaim.role).isEqualTo(ProfileRole.CHILD)
            assertThat(childClaim.nextStep).isEqualTo(NextStep.HOME)
            assertThat(dadClaim.nextStep).isEqualTo(NextStep.SUPPORT_MODE)
            assertThat(families.attachCalls.map { it.second }).containsExactly(childUser, dadUser)
            val claimed = summaries.summary(families.findByProfileId(child.profileId)!!.profile(child.profileId))
            assertThat(claimed.hasAccount).isTrue()
            assertThat(claimed.inviteStatus).isEqualTo(InviteStatus.CLAIMED)
        }

        @Test
        fun `없는 코드·만료·이미 사용·이미 구성원`() {
            val family = createFamily()
            val child = addChild(family.familyId)
            val code = inviteService.issueInvite(parentUser, child.profileId).claimCode

            assertThrows<ClaimCodeNotFoundException> { inviteService.claim(UUID.randomUUID(), "ZZZZZZ") }
            assertThrows<AlreadyMemberException> { inviteService.claim(parentUser, code.code) }

            clock.instant = code.expiresAt
            assertThrows<ClaimCodeExpiredException> { inviteService.claim(UUID.randomUUID(), code.code) }

            clock.instant = code.expiresAt.minusSeconds(1)
            inviteService.claim(UUID.randomUUID(), code.code)
            assertThrows<AlreadyClaimedException> { inviteService.claim(UUID.randomUUID(), code.code) }
        }

        @Test
        fun `조건부 UPDATE 가 0행이면 다른 계정이 먼저 가져간 것이다`() {
            val family = createFamily()
            val child = addChild(family.familyId)
            val code = inviteService.issueInvite(parentUser, child.profileId).claimCode.code
            families.attachSucceeds = false

            assertThrows<AlreadyClaimedException> { inviteService.claim(UUID.randomUUID(), code) }
            assertThat(families.attachCalls).hasSize(1)
        }

        @Test
        fun `재발급하면 이전 코드는 즉시 무효다`() {
            val family = createFamily()
            val child = addChild(family.familyId)
            val first = inviteService.issueInvite(parentUser, child.profileId).claimCode.code
            val second = inviteService.issueInvite(parentUser, child.profileId).claimCode.code

            assertThrows<ClaimCodeNotFoundException> { inviteService.claim(UUID.randomUUID(), first) }
            assertThat(inviteService.claim(UUID.randomUUID(), second).profileId).isEqualTo(child.profileId)
        }
    }

    @Nested
    inner class Settings {
        @Test
        fun `참여 수준은 본인 PARENT 프로필만 바꾼다`() {
            val family = createFamily()
            val child = addChild(family.familyId)
            val owner = family.ownerProfile.profileId

            val changed = settingsService.changeSupportMode(parentUser, owner, SupportMode.WEEKEND)
            assertThat(changed.supportMode).isEqualTo(SupportMode.WEEKEND)

            assertThrows<NotOwnProfileException> { settingsService.changeSupportMode(parentUser, child.profileId, SupportMode.FULL) }
            assertThrows<NotOwnProfileException> { settingsService.changeSupportMode(UUID.randomUUID(), owner, SupportMode.FULL) }
            assertThrows<ProfileNotFoundException> { settingsService.changeSupportMode(parentUser, UUID.randomUUID(), SupportMode.FULL) }

            val childUser = UUID.randomUUID()
            families.attachUserIfUnclaimed(child.profileId, childUser, clock.instant)
            assertThrows<SupportModeNotApplicableException> {
                settingsService.changeSupportMode(childUser, child.profileId, SupportMode.FULL)
            }
        }

        @Test
        fun `동의 철회와 재동의`() {
            val family = createFamily()
            val child = addChild(family.familyId)
            val grantedAt = clock.instant

            clock.instant = grantedAt.plusSeconds(60)
            val revoked = settingsService.updateConsent(parentUser, child.profileId, GuardianConsent(true, false))
            assertThat(revoked.consentGiven).isFalse()
            assertThat(revoked.consentAt).isNull()
            assertThat(revoked.consentBy).isNull()
            assertThat(revoked.measurable).isFalse()
            val record = families.findByProfileId(child.profileId)!!.profile(child.profileId).consent
            assertThat(record.personalAt).isEqualTo(grantedAt)
            assertThat(record.revokedAt).isEqualTo(clock.instant)

            clock.instant = grantedAt.plusSeconds(120)
            val regranted = settingsService.updateConsent(parentUser, child.profileId, GuardianConsent(true, true))
            assertThat(regranted.consentGiven).isTrue()
            assertThat(regranted.consentAt).isEqualTo(clock.instant)
            assertThat(regranted.consentBy).isEqualTo(parentUser)
            assertThat(regranted.measurable).isTrue()

            assertThrows<FamilyAccessDeniedException> {
                settingsService.updateConsent(UUID.randomUUID(), child.profileId, GuardianConsent(true, true))
            }
        }
    }

    @Nested
    inner class Cheers {
        @Test
        fun `응원은 내 프로필에서 같은 가족의 다른 사람에게만 보낸다`() {
            val family = createFamily()
            val owner = family.ownerProfile.profileId
            val child = addChild(family.familyId).profileId

            val cheer = cheerService.cheer(parentUser, family.familyId, owner, child, "힘내!", null, null)

            assertThat(cheer.familyId).isEqualTo(family.familyId)
            assertThat(cheer.message).isEqualTo("힘내!")
            assertThat(cheer.emoji).isNull()
            assertThat(cheer.createdAt).isEqualTo(clock.instant)
            assertThat(cheers.cheers).hasSize(1)

            assertThrows<SelfCheerException> { cheerService.cheer(parentUser, family.familyId, owner, owner, "나", null, null) }
            assertThrows<NotFamilyMemberException> {
                cheerService.cheer(
                    parentUser,
                    family.familyId,
                    owner,
                    UUID.randomUUID(),
                    "?",
                    null,
                    null,
                )
            }
            assertThrows<NotOwnProfileException> { cheerService.cheer(parentUser, family.familyId, child, owner, "?", null, null) }
            assertThrows<FamilyAccessDeniedException> {
                cheerService.cheer(
                    UUID.randomUUID(),
                    family.familyId,
                    owner,
                    child,
                    "?",
                    null,
                    null,
                )
            }
            assertThrows<FamilyNotFoundException> { cheerService.cheer(parentUser, UUID.randomUUID(), owner, child, "?", null, null) }
            assertThrows<IllegalArgumentException> { cheerService.cheer(parentUser, family.familyId, owner, child, " ", null, null) }
        }

        @Test
        fun `같은 대상에게 분당 5회를 넘기면 TOO_MANY`() {
            val family = createFamily()
            val owner = family.ownerProfile.profileId
            val child = addChild(family.familyId).profileId
            repeat(5) { cheerService.cheer(parentUser, family.familyId, owner, child, null, "👍", null) }

            assertThrows<TooManyCheersException> { cheerService.cheer(parentUser, family.familyId, owner, child, null, "👍", null) }

            clock.instant = clock.instant.plus(Duration.ofMinutes(1))
            cheerService.cheer(parentUser, family.familyId, owner, child, null, "👍", null)
            assertThat(cheers.cheers).hasSize(6)
        }
    }

    @Nested
    inner class Queries {
        private val profileQuery = ProfileQueryService(families, summaries)
        private val access = FamilyAccessService(families, summaries)
        private val cheerQuery = CheerQueryService(cheers)

        @Test
        fun `ProfileQuery 는 요약과 상세를 준다`() {
            val family = createFamily()
            val child = addChild(family.familyId)

            assertThat(profileQuery.findSummary(child.profileId)).isEqualTo(child)
            assertThat(profileQuery.findSummary(UUID.randomUUID())).isNull()
            val details = profileQuery.findDetails(child.profileId)!!
            assertThat(details.birthDate).isEqualTo(LocalDate.of(2018, 5, 20))
            assertThat(details.sex).isEqualTo(Sex.M)
            assertThat(details.userId).isNull()
            assertThat(details.consentGiven).isTrue()
            assertThat(profileQuery.summariesOfFamily(family.familyId)).hasSize(2)
            assertThat(profileQuery.detailsOfFamily(family.familyId)).hasSize(2)
            assertThat(profileQuery.summariesOfUser(parentUser).single().profileId).isEqualTo(family.ownerProfile.profileId)
            assertThat(profileQuery.familyName(family.familyId)).isEqualTo("우리 가족")
            assertThat(profileQuery.familyName(UUID.randomUUID())).isNull()
        }

        @Test
        fun `FamilyAccess 는 저장된 프로필로 권한을 판단한다`() {
            val family = createFamily()
            val child = addChild(family.familyId)
            val childUser = UUID.randomUUID()
            families.attachUserIfUnclaimed(child.profileId, childUser, clock.instant)
            val stranger = UUID.randomUUID()

            assertThat(access.requireMember(parentUser, family.familyId).profileId).isEqualTo(family.ownerProfile.profileId)
            assertThat(access.requireParent(parentUser, family.familyId).isParent).isTrue()
            assertThat(access.memberOf(childUser, family.familyId)?.role).isEqualTo(ProfileRole.CHILD)
            assertThat(access.memberOf(stranger, family.familyId)).isNull()
            assertThrows<NotSameFamilyException> { access.requireMember(stranger, family.familyId) }
            assertThrows<NotAParentException> { access.requireParent(childUser, family.familyId) }
            assertThrows<FamilyNotFoundException> { access.requireMember(parentUser, UUID.randomUUID()) }

            assertThat(
                access.requireSameFamilyAsProfile(childUser, family.ownerProfile.profileId).profileId,
            ).isEqualTo(family.ownerProfile.profileId)
            assertThrows<NotSameFamilyException> { access.requireSameFamilyAsProfile(stranger, child.profileId) }
            assertThrows<ProfileNotFoundException> { access.requireSameFamilyAsProfile(parentUser, UUID.randomUUID()) }
            assertThat(access.requireParentOfProfile(parentUser, child.profileId).profileId).isEqualTo(family.ownerProfile.profileId)
            assertThrows<NotAParentException> { access.requireParentOfProfile(childUser, child.profileId) }
        }

        @Test
        fun `CheerQuery 는 구간 응원 수를 센다`() {
            val family = createFamily()
            val owner = family.ownerProfile.profileId
            val child = addChild(family.familyId).profileId
            val start = clock.instant
            cheerService.cheer(parentUser, family.familyId, owner, child, "a", null, null)
            clock.instant = start.plus(Duration.ofDays(1))
            cheerService.cheer(parentUser, family.familyId, owner, child, "b", null, null)

            assertThat(cheerQuery.countCheers(family.familyId, start, start.plus(Duration.ofDays(1)))).isEqualTo(1)
            assertThat(cheerQuery.countCheers(family.familyId, start, start.plus(Duration.ofDays(2)))).isEqualTo(2)
            assertThat(cheerQuery.countCheers(UUID.randomUUID(), start, start.plus(Duration.ofDays(2)))).isZero()
        }
    }
}
