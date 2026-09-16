package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyClaimedException;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyInFamilyException;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyMemberException;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeExpiredException;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.ConsentRecord;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyAccessDeniedException;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsentRequiredException;
import kr.ac.kookmin.familyfitness.identity.domain.NotFamilyMemberException;
import kr.ac.kookmin.familyfitness.identity.domain.NotOwnProfileException;
import kr.ac.kookmin.familyfitness.identity.domain.SelfCheerException;
import kr.ac.kookmin.familyfitness.identity.domain.SupportModeNotApplicableException;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyCheersException;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 유스케이스 서비스 규칙. 저장소는 인메모리 가짜, 시각은 고정. */
class IdentityServicesTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-08T10:00:00Z"));
    private final IdentityClock identityClock = clock.identityClock();
    private final InMemoryFamilyRepository families = new InMemoryFamilyRepository();
    private final InMemoryCheerRepository cheers = new InMemoryCheerRepository();
    private final ProfileSummaries summaries = new ProfileSummaries(identityClock);
    private final AppProperties props = new AppProperties(
            "Asia/Seoul",
            "https://app.example.com/",
            new AppProperties.Cors(),
            new AppProperties.Auth(),
            new AppProperties.Ai());

    private final FamilyService familyService = new FamilyService(families, summaries, identityClock);
    private final InviteService inviteService = new InviteService(families, props, identityClock);
    private final ProfileSettingsService settingsService =
            new ProfileSettingsService(families, summaries, identityClock);
    private final CheerService cheerService = new CheerService(families, cheers, identityClock);

    private final UUID parentUser = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 9, 8);

    private CreatedFamily createFamily() {
        return familyService.createFamily(parentUser, "우리 가족", "엄마", LocalDate.of(1988, 3, 1), Sex.F);
    }

    private ProfileSummary addChild(UUID familyId) {
        return addChild(familyId, new GuardianConsent(true, true), LocalDate.of(2018, 5, 20), "첫째");
    }

    private ProfileSummary addChild(UUID familyId, @Nullable GuardianConsent consent) {
        return addChild(familyId, consent, LocalDate.of(2018, 5, 20), "첫째");
    }

    private ProfileSummary addChild(UUID familyId, LocalDate birthDate) {
        return addChild(familyId, new GuardianConsent(true, true), birthDate, "첫째");
    }

    private ProfileSummary addChild(
            UUID familyId, @Nullable GuardianConsent consent, LocalDate birthDate, String name) {
        return familyService.addMember(parentUser, familyId, name, birthDate, Sex.M, ProfileRole.CHILD, consent);
    }

    @Nested
    class CreateFamily {
        @Test
        @DisplayName("가족을 만들면 owner PARENT 요약이 돌아오고 저장된다")
        void 가족을_만들면_owner_PARENT_요약이_돌아오고_저장된다() {
            CreatedFamily created = createFamily();

            assertThat(created.familyName()).isEqualTo("우리 가족");
            ProfileSummary owner = created.ownerProfile();
            assertThat(owner.familyId()).isEqualTo(created.familyId());
            assertThat(owner.role()).isEqualTo(ProfileRole.PARENT);
            assertThat(owner.hasAccount()).isTrue();
            assertThat(owner.inviteStatus()).isEqualTo(InviteStatus.NONE);
            assertThat(owner.ageGroup()).isEqualTo(AgeGroup.ADULT);
            assertThat(owner.consentRequired()).isFalse();
            assertThat(owner.consentGiven()).isTrue();
            assertThat(owner.measurable()).isTrue();
            assertThat(owner.supportMode()).isNull();
            assertThat(families.findById(created.familyId()).getProfiles()).hasSize(1);
        }

        @Test
        @DisplayName("이미 프로필이 붙은 계정은 가족을 또 만들 수 없다")
        void 이미_프로필이_붙은_계정은_가족을_또_만들_수_없다() {
            createFamily();

            assertThrows(AlreadyInFamilyException.class, () -> createFamily());
            assertThat(families.families).hasSize(1);
        }

        @Test
        @DisplayName("미래 생년월일은 거부한다")
        void 미래_생년월일은_거부한다() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> familyService.createFamily(parentUser, "우리 가족", "엄마", today.plusDays(1), Sex.F));
        }
    }

    @Nested
    class AddMember {
        @Test
        @DisplayName("동의가 있는 아이는 측정 가능하고 계정은 없다")
        void 동의가_있는_아이는_측정_가능하고_계정은_없다() {
            CreatedFamily family = createFamily();

            ProfileSummary child = addChild(family.familyId());

            assertThat(child.role()).isEqualTo(ProfileRole.CHILD);
            assertThat(child.hasAccount()).isFalse();
            assertThat(child.ageGroup()).isEqualTo(AgeGroup.YOUTH);
            assertThat(child.consentRequired()).isTrue();
            assertThat(child.consentGiven()).isTrue();
            assertThat(child.measurable()).isTrue();
            assertThat(child.inviteStatus()).isEqualTo(InviteStatus.NONE);
            assertThat(families.findById(family.familyId()).getProfiles()).hasSize(2);
        }

        @Test
        @DisplayName("만 14세 미만은 동의가 없거나 불완전하면 저장하지 않는다")
        void 만_14세_미만은_동의가_없거나_불완전하면_저장하지_않는다() {
            CreatedFamily family = createFamily();

            assertThrows(
                    GuardianConsentRequiredException.class, () -> addChild(family.familyId(), (GuardianConsent) null));
            assertThrows(
                    GuardianConsentRequiredException.class,
                    () -> addChild(family.familyId(), new GuardianConsent(true, false)));
            assertThat(families.findById(family.familyId()).getProfiles()).hasSize(1);
        }

        @Test
        @DisplayName("만 4세 미만도 프로필은 생기지만 측정 대상이 아니다")
        void 만_4세_미만도_프로필은_생기지만_측정_대상이_아니다() {
            CreatedFamily family = createFamily();

            ProfileSummary toddler = addChild(family.familyId(), today.minusYears(3));

            assertThat(toddler.ageGroup()).isEqualTo(AgeGroup.TODDLER);
            assertThat(toddler.consentGiven()).isTrue();
            assertThat(toddler.measurable()).isFalse();
        }

        @Test
        @DisplayName("다른 가족 계정이나 아이 계정은 구성원을 추가할 수 없다")
        void 다른_가족_계정이나_아이_계정은_구성원을_추가할_수_없다() {
            CreatedFamily family = createFamily();
            UUID stranger = UUID.randomUUID();

            assertThrows(
                    FamilyAccessDeniedException.class,
                    () -> familyService.addMember(
                            stranger,
                            family.familyId(),
                            "아이",
                            LocalDate.of(2018, 5, 20),
                            Sex.M,
                            ProfileRole.CHILD,
                            new GuardianConsent(true, true)));
            assertThrows(
                    FamilyNotFoundException.class,
                    () -> familyService.addMember(
                            parentUser,
                            UUID.randomUUID(),
                            "아이",
                            LocalDate.of(2018, 5, 20),
                            Sex.M,
                            ProfileRole.CHILD,
                            null));

            ProfileSummary child = addChild(family.familyId());
            UUID childUser = UUID.randomUUID();
            families.attachUserIfUnclaimed(child.profileId(), childUser, clock.instant());
            assertThrows(
                    NotAParentException.class,
                    () -> familyService.addMember(
                            childUser,
                            family.familyId(),
                            "동생",
                            LocalDate.of(2020, 1, 1),
                            Sex.F,
                            ProfileRole.CHILD,
                            new GuardianConsent(true, true)));
        }

        @Test
        @DisplayName("구성원 목록은 가족 구성원만 본다")
        void 구성원_목록은_가족_구성원만_본다() {
            CreatedFamily family = createFamily();
            addChild(family.familyId());

            FamilyProfiles listed = familyService.profilesOf(parentUser, family.familyId());
            assertThat(listed.familyName()).isEqualTo("우리 가족");
            assertThat(listed.profiles()).hasSize(2);

            assertThrows(
                    FamilyAccessDeniedException.class,
                    () -> familyService.profilesOf(UUID.randomUUID(), family.familyId()));
        }
    }

    @Nested
    class Invite {
        @Test
        @DisplayName("초대 코드는 6자리이고 7일 뒤 만료되며 공유 URL 에 실린다")
        void 초대_코드는_6자리이고_7일_뒤_만료되며_공유_URL_에_실린다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());

            Invitation invitation = inviteService.issueInvite(parentUser, child.profileId());

            assertThat(invitation.claimCode().code()).matches("[A-HJ-NP-Z2-9]{6}");
            assertThat(invitation.claimCode().expiresAt())
                    .isEqualTo(clock.instant().plus(Duration.ofDays(7)));
            assertThat(invitation.shareUrl())
                    .isEqualTo("https://app.example.com/claim?code="
                            + invitation.claimCode().code());
            assertThat(summaries
                            .summary(families.findByProfileId(child.profileId()).profile(child.profileId()))
                            .inviteStatus())
                    .isEqualTo(InviteStatus.ISSUED);
        }

        @Test
        @DisplayName("계정이 붙은 프로필·남의 가족·없는 프로필에는 발급하지 않는다")
        void 계정이_붙은_프로필_남의_가족_없는_프로필에는_발급하지_않는다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());

            assertThrows(
                    AlreadyClaimedException.class,
                    () -> inviteService.issueInvite(
                            parentUser, family.ownerProfile().profileId()));
            assertThrows(
                    FamilyAccessDeniedException.class,
                    () -> inviteService.issueInvite(UUID.randomUUID(), child.profileId()));
            assertThrows(
                    ProfileNotFoundException.class, () -> inviteService.issueInvite(parentUser, UUID.randomUUID()));
        }

        @Test
        @DisplayName("코드를 쓰면 계정이 붙고 아이는 HOME 부모는 SUPPORT_MODE 로 간다")
        void 코드를_쓰면_계정이_붙고_아이는_HOME_부모는_SUPPORT_MODE_로_간다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            ProfileSummary dad = familyService.addMember(
                    parentUser, family.familyId(), "아빠", LocalDate.of(1986, 1, 1), Sex.M, ProfileRole.PARENT, null);
            String childCode = inviteService
                    .issueInvite(parentUser, child.profileId())
                    .claimCode()
                    .code();
            String dadCode = inviteService
                    .issueInvite(parentUser, dad.profileId())
                    .claimCode()
                    .code();
            UUID childUser = UUID.randomUUID();
            UUID dadUser = UUID.randomUUID();

            ClaimResult childClaim = inviteService.claim(childUser, " " + childCode.toLowerCase() + " ");
            ClaimResult dadClaim = inviteService.claim(dadUser, dadCode);

            assertThat(childClaim.profileId()).isEqualTo(child.profileId());
            assertThat(childClaim.familyId()).isEqualTo(family.familyId());
            assertThat(childClaim.role()).isEqualTo(ProfileRole.CHILD);
            assertThat(childClaim.nextStep()).isEqualTo(NextStep.HOME);
            assertThat(dadClaim.nextStep()).isEqualTo(NextStep.SUPPORT_MODE);
            assertThat(families.attachCalls.stream()
                            .map(InMemoryFamilyRepository.AttachCall::userId)
                            .toList())
                    .containsExactly(childUser, dadUser);
            ProfileSummary claimed = summaries.summary(
                    families.findByProfileId(child.profileId()).profile(child.profileId()));
            assertThat(claimed.hasAccount()).isTrue();
            assertThat(claimed.inviteStatus()).isEqualTo(InviteStatus.CLAIMED);
        }

        @Test
        @DisplayName("없는 코드·만료·이미 사용·이미 구성원")
        void 없는_코드_만료_이미_사용_이미_구성원() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            ClaimCode code =
                    inviteService.issueInvite(parentUser, child.profileId()).claimCode();

            assertThrows(ClaimCodeNotFoundException.class, () -> inviteService.claim(UUID.randomUUID(), "ZZZZZZ"));
            assertThrows(AlreadyMemberException.class, () -> inviteService.claim(parentUser, code.code()));

            clock.setInstant(code.expiresAt());
            assertThrows(ClaimCodeExpiredException.class, () -> inviteService.claim(UUID.randomUUID(), code.code()));

            clock.setInstant(code.expiresAt().minusSeconds(1));
            inviteService.claim(UUID.randomUUID(), code.code());
            assertThrows(AlreadyClaimedException.class, () -> inviteService.claim(UUID.randomUUID(), code.code()));
        }

        @Test
        @DisplayName("조건부 UPDATE 가 0행이면 다른 계정이 먼저 가져간 것이다")
        void 조건부_UPDATE_가_0행이면_다른_계정이_먼저_가져간_것이다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            String code = inviteService
                    .issueInvite(parentUser, child.profileId())
                    .claimCode()
                    .code();
            families.attachSucceeds = false;

            assertThrows(AlreadyClaimedException.class, () -> inviteService.claim(UUID.randomUUID(), code));
            assertThat(families.attachCalls).hasSize(1);
        }

        @Test
        @DisplayName("재발급하면 이전 코드는 즉시 무효다")
        void 재발급하면_이전_코드는_즉시_무효다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            String first = inviteService
                    .issueInvite(parentUser, child.profileId())
                    .claimCode()
                    .code();
            String second = inviteService
                    .issueInvite(parentUser, child.profileId())
                    .claimCode()
                    .code();

            assertThrows(ClaimCodeNotFoundException.class, () -> inviteService.claim(UUID.randomUUID(), first));
            assertThat(inviteService.claim(UUID.randomUUID(), second).profileId())
                    .isEqualTo(child.profileId());
        }
    }

    @Nested
    class Settings {
        @Test
        @DisplayName("참여 수준은 본인 PARENT 프로필만 바꾼다")
        void 참여_수준은_본인_PARENT_프로필만_바꾼다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            UUID owner = family.ownerProfile().profileId();

            ProfileSummary changed = settingsService.changeSupportMode(parentUser, owner, SupportMode.WEEKEND);
            assertThat(changed.supportMode()).isEqualTo(SupportMode.WEEKEND);

            assertThrows(
                    NotOwnProfileException.class,
                    () -> settingsService.changeSupportMode(parentUser, child.profileId(), SupportMode.FULL));
            assertThrows(
                    NotOwnProfileException.class,
                    () -> settingsService.changeSupportMode(UUID.randomUUID(), owner, SupportMode.FULL));
            assertThrows(
                    ProfileNotFoundException.class,
                    () -> settingsService.changeSupportMode(parentUser, UUID.randomUUID(), SupportMode.FULL));

            UUID childUser = UUID.randomUUID();
            families.attachUserIfUnclaimed(child.profileId(), childUser, clock.instant());
            assertThrows(
                    SupportModeNotApplicableException.class,
                    () -> settingsService.changeSupportMode(childUser, child.profileId(), SupportMode.FULL));
        }

        @Test
        @DisplayName("동의 철회와 재동의")
        void 동의_철회와_재동의() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            Instant grantedAt = clock.instant();

            clock.setInstant(grantedAt.plusSeconds(60));
            ConsentState revoked =
                    settingsService.updateConsent(parentUser, child.profileId(), new GuardianConsent(true, false));
            assertThat(revoked.consentGiven()).isFalse();
            assertThat(revoked.consentAt()).isNull();
            assertThat(revoked.consentBy()).isNull();
            assertThat(revoked.measurable()).isFalse();
            ConsentRecord record = families.findByProfileId(child.profileId())
                    .profile(child.profileId())
                    .getConsent();
            assertThat(record.personalAt()).isEqualTo(grantedAt);
            assertThat(record.revokedAt()).isEqualTo(clock.instant());

            clock.setInstant(grantedAt.plusSeconds(120));
            ConsentState regranted =
                    settingsService.updateConsent(parentUser, child.profileId(), new GuardianConsent(true, true));
            assertThat(regranted.consentGiven()).isTrue();
            assertThat(regranted.consentAt()).isEqualTo(clock.instant());
            assertThat(regranted.consentBy()).isEqualTo(parentUser);
            assertThat(regranted.measurable()).isTrue();

            assertThrows(
                    FamilyAccessDeniedException.class,
                    () -> settingsService.updateConsent(
                            UUID.randomUUID(), child.profileId(), new GuardianConsent(true, true)));
        }
    }

    @Nested
    class Cheers {
        @Test
        @DisplayName("응원은 내 프로필에서 같은 가족의 다른 사람에게만 보낸다")
        void 응원은_내_프로필에서_같은_가족의_다른_사람에게만_보낸다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();

            Cheer cheer = cheerService.cheer(parentUser, family.familyId(), owner, child, "힘내!", null, null);

            assertThat(cheer.familyId()).isEqualTo(family.familyId());
            assertThat(cheer.message()).isEqualTo("힘내!");
            assertThat(cheer.emoji()).isNull();
            assertThat(cheer.createdAt()).isEqualTo(clock.instant());
            assertThat(cheers.cheers).hasSize(1);

            assertThrows(
                    SelfCheerException.class,
                    () -> cheerService.cheer(parentUser, family.familyId(), owner, owner, "나", null, null));
            assertThrows(
                    NotFamilyMemberException.class,
                    () -> cheerService.cheer(parentUser, family.familyId(), owner, UUID.randomUUID(), "?", null, null));
            assertThrows(
                    NotOwnProfileException.class,
                    () -> cheerService.cheer(parentUser, family.familyId(), child, owner, "?", null, null));
            assertThrows(
                    FamilyAccessDeniedException.class,
                    () -> cheerService.cheer(UUID.randomUUID(), family.familyId(), owner, child, "?", null, null));
            assertThrows(
                    FamilyNotFoundException.class,
                    () -> cheerService.cheer(parentUser, UUID.randomUUID(), owner, child, "?", null, null));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> cheerService.cheer(parentUser, family.familyId(), owner, child, " ", null, null));
        }

        @Test
        @DisplayName("같은 대상에게 분당 5회를 넘기면 TOO_MANY")
        void 같은_대상에게_분당_5회를_넘기면_TOO_MANY() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            for (int i = 0; i < 5; i++) {
                cheerService.cheer(parentUser, family.familyId(), owner, child, null, "👍", null);
            }

            assertThrows(
                    TooManyCheersException.class,
                    () -> cheerService.cheer(parentUser, family.familyId(), owner, child, null, "👍", null));

            clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
            cheerService.cheer(parentUser, family.familyId(), owner, child, null, "👍", null);
            assertThat(cheers.cheers).hasSize(6);
        }
    }

    @Nested
    class Queries {
        private final ProfileQueryService profileQuery = new ProfileQueryService(families, summaries);
        private final FamilyAccessService access = new FamilyAccessService(families, summaries);
        private final CheerQueryService cheerQuery = new CheerQueryService(cheers);

        @Test
        @DisplayName("ProfileQuery 는 요약과 상세를 준다")
        void ProfileQuery_는_요약과_상세를_준다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());

            assertThat(profileQuery.findSummary(child.profileId())).isEqualTo(child);
            assertThat(profileQuery.findSummary(UUID.randomUUID())).isNull();
            ProfileDetails details = profileQuery.findDetails(child.profileId());
            assertThat(details.birthDate()).isEqualTo(LocalDate.of(2018, 5, 20));
            assertThat(details.sex()).isEqualTo(Sex.M);
            assertThat(details.userId()).isNull();
            assertThat(details.consentGiven()).isTrue();
            assertThat(profileQuery.summariesOfFamily(family.familyId())).hasSize(2);
            assertThat(profileQuery.detailsOfFamily(family.familyId())).hasSize(2);
            assertThat(profileQuery.summariesOfUser(parentUser))
                    .singleElement()
                    .extracting(ProfileSummary::profileId)
                    .isEqualTo(family.ownerProfile().profileId());
            assertThat(profileQuery.familyName(family.familyId())).isEqualTo("우리 가족");
            assertThat(profileQuery.familyName(UUID.randomUUID())).isNull();
        }

        @Test
        @DisplayName("FamilyAccess 는 저장된 프로필로 권한을 판단한다")
        void FamilyAccess_는_저장된_프로필로_권한을_판단한다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            UUID childUser = UUID.randomUUID();
            families.attachUserIfUnclaimed(child.profileId(), childUser, clock.instant());
            UUID stranger = UUID.randomUUID();

            assertThat(access.requireMember(parentUser, family.familyId()).profileId())
                    .isEqualTo(family.ownerProfile().profileId());
            assertThat(access.requireParent(parentUser, family.familyId()).isParent())
                    .isTrue();
            assertThat(access.memberOf(childUser, family.familyId()).role()).isEqualTo(ProfileRole.CHILD);
            assertThat(access.memberOf(stranger, family.familyId())).isNull();
            assertThrows(NotSameFamilyException.class, () -> access.requireMember(stranger, family.familyId()));
            assertThrows(NotAParentException.class, () -> access.requireParent(childUser, family.familyId()));
            assertThrows(FamilyNotFoundException.class, () -> access.requireMember(parentUser, UUID.randomUUID()));

            assertThat(access.requireSameFamilyAsProfile(
                                    childUser, family.ownerProfile().profileId())
                            .profileId())
                    .isEqualTo(family.ownerProfile().profileId());
            assertThrows(
                    NotSameFamilyException.class, () -> access.requireSameFamilyAsProfile(stranger, child.profileId()));
            assertThrows(
                    ProfileNotFoundException.class,
                    () -> access.requireSameFamilyAsProfile(parentUser, UUID.randomUUID()));
            assertThat(access.requireParentOfProfile(parentUser, child.profileId())
                            .profileId())
                    .isEqualTo(family.ownerProfile().profileId());
            assertThrows(NotAParentException.class, () -> access.requireParentOfProfile(childUser, child.profileId()));
        }

        @Test
        @DisplayName("CheerQuery 는 구간 응원 수를 센다")
        void CheerQuery_는_구간_응원_수를_센다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            Instant start = clock.instant();
            cheerService.cheer(parentUser, family.familyId(), owner, child, "a", null, null);
            clock.setInstant(start.plus(Duration.ofDays(1)));
            cheerService.cheer(parentUser, family.familyId(), owner, child, "b", null, null);

            assertThat(cheerQuery.countCheers(family.familyId(), start, start.plus(Duration.ofDays(1))))
                    .isEqualTo(1);
            assertThat(cheerQuery.countCheers(family.familyId(), start, start.plus(Duration.ofDays(2))))
                    .isEqualTo(2);
            assertThat(cheerQuery.countCheers(UUID.randomUUID(), start, start.plus(Duration.ofDays(2))))
                    .isZero();
        }
    }
}
