package kr.ac.kookmin.familyfitness.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.CheerKind;
import kr.ac.kookmin.familyfitness.identity.api.CheerSent;
import kr.ac.kookmin.familyfitness.identity.api.CheerView;
import kr.ac.kookmin.familyfitness.identity.api.FamilyNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.identity.api.MissionLookup;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileDetails;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyClaimedException;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyInFamilyException;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyMemberException;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyThankedException;
import kr.ac.kookmin.familyfitness.identity.domain.Cheer;
import kr.ac.kookmin.familyfitness.identity.domain.CheerKindNotAllowedException;
import kr.ac.kookmin.familyfitness.identity.domain.CheerMissionNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.CheerNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeExpiredException;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.ConsentEvent;
import kr.ac.kookmin.familyfitness.identity.domain.ConsentRecord;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyAccessDeniedException;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsent;
import kr.ac.kookmin.familyfitness.identity.domain.GuardianConsentRequiredException;
import kr.ac.kookmin.familyfitness.identity.domain.InvalidCheerException;
import kr.ac.kookmin.familyfitness.identity.domain.NotAReplyTargetException;
import kr.ac.kookmin.familyfitness.identity.domain.NotFamilyMemberException;
import kr.ac.kookmin.familyfitness.identity.domain.NotOwnProfileException;
import kr.ac.kookmin.familyfitness.identity.domain.ProfileEdit;
import kr.ac.kookmin.familyfitness.identity.domain.SelfCheerException;
import kr.ac.kookmin.familyfitness.identity.domain.SelfConsentException;
import kr.ac.kookmin.familyfitness.identity.domain.SupportModeNotApplicableException;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyCheersException;
import kr.ac.kookmin.familyfitness.identity.domain.TooManyClaimAttemptsException;
import kr.ac.kookmin.familyfitness.identity.domain.Under14NotAllowedException;
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
    private final ClaimAttemptLimiter claimAttempts = new ClaimAttemptLimiter(identityClock);
    private final InviteService inviteService = new InviteService(families, props, identityClock, claimAttempts);
    private final ProfileSettingsService settingsService =
            new ProfileSettingsService(families, summaries, identityClock);
    /** 미션 id → 가족 id. coaching 이 구현하는 {@link MissionLookup} 의 가짜. */
    private final Map<UUID, UUID> missionFamilies = new HashMap<>();

    private final MissionLookup missionLookup =
            (familyId, missionId) -> familyId.equals(missionFamilies.get(missionId));
    private final List<Object> published = new ArrayList<>();
    private final CheerService cheerService =
            new CheerService(families, cheers, missionLookup, published::add, identityClock);

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
        return familyService.addMember(
                parentUser, familyId, name, birthDate, Sex.M, ProfileRole.CHILD, null, null, consent);
    }

    /** kind 없이 보낸다(서버가 역할 · 스티커로 정한다). */
    private Cheer send(
            UUID userId, UUID familyId, UUID from, UUID to, @Nullable String message, @Nullable String stickerId) {
        return cheerService.cheer(
                userId, familyId, new SendCheerCommand(from, to, null, message, stickerId, null, null));
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
            assertThat(owner.sex()).isEqualTo(Sex.F);
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

        @Test
        @DisplayName("만 14세 미만은 가족을 만들지 못한다 — UNDER_14_NOT_ALLOWED, 아무것도 저장하지 않는다")
        void 만_14세_미만은_가족을_만들지_못한다() {
            assertThrows(
                    Under14NotAllowedException.class,
                    () -> familyService.createFamily(
                            parentUser, "우리 가족", "아이", today.minusYears(14).plusDays(1), Sex.F));
            assertThat(families.families).isEmpty();

            CreatedFamily created = familyService.createFamily(parentUser, "우리 가족", "큰애", today.minusYears(14), Sex.F);
            assertThat(created.ownerProfile().role()).isEqualTo(ProfileRole.PARENT);
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
            assertThat(child.sex()).isEqualTo(Sex.M);
            assertThat(child.hasAccount()).isFalse();
            assertThat(child.ageGroup()).isEqualTo(AgeGroup.YOUTH);
            assertThat(child.consentRequired()).isTrue();
            assertThat(child.consentGiven()).isTrue();
            assertThat(child.measurable()).isTrue();
            assertThat(child.inviteStatus()).isEqualTo(InviteStatus.NONE);
            assertThat(families.findById(family.familyId()).getProfiles()).hasSize(2);
        }

        @Test
        @DisplayName("구성원을 추가할 때 적은 키 · 몸무게는 저장된 프로필에 남는다")
        void 구성원을_추가할_때_적은_키_몸무게는_저장된_프로필에_남는다() {
            CreatedFamily family = createFamily();

            ProfileSummary child = familyService.addMember(
                    parentUser,
                    family.familyId(),
                    "첫째",
                    LocalDate.of(2018, 5, 20),
                    Sex.M,
                    ProfileRole.CHILD,
                    new BigDecimal("128.5"),
                    new BigDecimal("27.3"),
                    new GuardianConsent(true, true));

            ProfileDetails saved = summaries.details(
                    families.findByProfileId(child.profileId()).profile(child.profileId()));
            assertThat(saved.heightCm()).isEqualByComparingTo("128.5");
            assertThat(saved.weightKg()).isEqualByComparingTo("27.3");
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
        @DisplayName("만 14세 미만을 PARENT 로 넣으면 UNDER_14_NOT_ALLOWED. 넣으면서 준 동의는 이력에 한 줄 남는다")
        void 만_14세_미만을_PARENT_로_넣으면_UNDER_14_NOT_ALLOWED() {
            CreatedFamily family = createFamily();

            assertThrows(
                    Under14NotAllowedException.class,
                    () -> familyService.addMember(
                            parentUser,
                            family.familyId(),
                            "어린 보호자",
                            today.minusYears(13),
                            Sex.M,
                            ProfileRole.PARENT,
                            null,
                            null,
                            new GuardianConsent(true, true)));
            assertThat(families.findById(family.familyId()).getProfiles()).hasSize(1);
            assertThat(families.consentEvents).isEmpty();

            ProfileSummary child = addChild(family.familyId());
            assertThat(families.consentEvents)
                    .containsExactly(new ConsentEvent(
                            child.profileId(), parentUser, ConsentEvent.Kind.GRANTED, true, true, clock.instant()));
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
                            null,
                            null,
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
                            null,
                            null,
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
                            null,
                            null,
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
                    parentUser,
                    family.familyId(),
                    "아빠",
                    LocalDate.of(1986, 1, 1),
                    Sex.M,
                    ProfileRole.PARENT,
                    null,
                    null,
                    null);
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
        @DisplayName("살아 있는 코드가 있으면 다시 발급해도 같은 코드다 — 먼저 보낸 코드가 죽지 않는다")
        void 살아_있는_코드가_있으면_다시_발급해도_같은_코드다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            ClaimCode first =
                    inviteService.issueInvite(parentUser, child.profileId()).claimCode();

            clock.setInstant(clock.instant().plus(Duration.ofDays(3)));
            ClaimCode again =
                    inviteService.issueInvite(parentUser, child.profileId()).claimCode();

            assertThat(again).isEqualTo(first);
            assertThat(inviteService.claim(UUID.randomUUID(), first.code()).profileId())
                    .isEqualTo(child.profileId());
        }

        @Test
        @DisplayName("만료된 코드만 새 코드로 바뀌고, 옛 코드는 없는 코드가 된다")
        void 만료된_코드만_새_코드로_바뀌고_옛_코드는_없는_코드가_된다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            ClaimCode first =
                    inviteService.issueInvite(parentUser, child.profileId()).claimCode();

            clock.setInstant(first.expiresAt());
            ClaimCode second =
                    inviteService.issueInvite(parentUser, child.profileId()).claimCode();

            assertThat(second.code()).isNotEqualTo(first.code());
            assertThat(second.expiresAt()).isEqualTo(first.expiresAt().plus(Duration.ofDays(7)));
            assertThrows(ClaimCodeNotFoundException.class, () -> inviteService.claim(UUID.randomUUID(), first.code()));
            assertThat(inviteService.claim(UUID.randomUUID(), second.code()).profileId())
                    .isEqualTo(child.profileId());
        }

        @Test
        @DisplayName("미리 보기는 가족 이름 · 자리 이름 · 역할 · 연령대 · 보낸 보호자 · 만료를 준다")
        void 미리_보기는_자리와_보낸_보호자를_준다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            ClaimCode code =
                    inviteService.issueInvite(parentUser, child.profileId()).claimCode();

            InvitePreview preview =
                    inviteService.preview(UUID.randomUUID(), " " + code.code().toLowerCase() + " ");

            assertThat(preview)
                    .isEqualTo(new InvitePreview(
                            "우리 가족", "첫째", ProfileRole.CHILD, AgeGroup.YOUTH, "엄마", code.expiresAt()));
            // 구성원인지는 보지 않는다 — 보낸 사람도 볼 수 있다
            assertThat(inviteService.preview(parentUser, code.code()).profileName())
                    .isEqualTo("첫째");
        }

        @Test
        @DisplayName("미리 보기 판정 — 없음 404 → 이미 사용 409 → 만료 410")
        void 미리_보기_판정_없음_이미_사용_만료() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            ClaimCode code =
                    inviteService.issueInvite(parentUser, child.profileId()).claimCode();

            assertThrows(ClaimCodeNotFoundException.class, () -> inviteService.preview(UUID.randomUUID(), "ZZZZZZ"));
            assertThrows(ClaimCodeNotFoundException.class, () -> inviteService.preview(UUID.randomUUID(), "AB"));
            clock.setInstant(code.expiresAt());
            assertThrows(ClaimCodeExpiredException.class, () -> inviteService.preview(UUID.randomUUID(), code.code()));

            clock.setInstant(code.expiresAt().minusSeconds(1));
            inviteService.claim(UUID.randomUUID(), code.code());
            clock.setInstant(code.expiresAt());
            // 쓴 코드는 기한이 지나도 이미 사용이다(수락과 같은 차례)
            assertThrows(AlreadyClaimedException.class, () -> inviteService.preview(UUID.randomUUID(), code.code()));
        }

        @Test
        @DisplayName("다른 가족에 프로필이 있는 계정은 코드를 쓸 수 없다 — ALREADY_IN_FAMILY, 계정은 붙지 않는다")
        void 다른_가족에_프로필이_있는_계정은_코드를_쓸_수_없다() {
            CreatedFamily family = createFamily();
            ProfileSummary dad = familyService.addMember(
                    parentUser,
                    family.familyId(),
                    "아빠",
                    LocalDate.of(1986, 1, 1),
                    Sex.M,
                    ProfileRole.PARENT,
                    null,
                    null,
                    null);
            String code = inviteService
                    .issueInvite(parentUser, dad.profileId())
                    .claimCode()
                    .code();
            UUID otherParent = UUID.randomUUID();
            familyService.createFamily(otherParent, "아빠네", "아빠", LocalDate.of(1986, 1, 1), Sex.M);

            assertThrows(AlreadyInFamilyException.class, () -> inviteService.claim(otherParent, code));
            assertThat(families.attachCalls).isEmpty();
            assertThat(families.findByProfileId(dad.profileId())
                            .profile(dad.profileId())
                            .hasAccount())
                    .isFalse();
            // 미리 보기에는 구성원 · 다른 가족 검사가 없다
            assertThat(inviteService.preview(otherParent, code).profileName()).isEqualTo("아빠");
        }

        @Test
        @DisplayName("없는 코드를 10분에 10번 넣으면 다음은 맞는 코드도 429 이고, 미리 보기와 셈을 같이 쓴다")
        void 없는_코드를_10분에_10번_넣으면_다음은_맞는_코드도_429_다() {
            CreatedFamily family = createFamily();
            ProfileSummary child = addChild(family.familyId());
            String code = inviteService
                    .issueInvite(parentUser, child.profileId())
                    .claimCode()
                    .code();
            UUID guesser = UUID.randomUUID();

            for (int i = 0; i < 5; i++) {
                assertThrows(ClaimCodeNotFoundException.class, () -> inviteService.claim(guesser, "ZZZZZZ"));
                assertThrows(ClaimCodeNotFoundException.class, () -> inviteService.preview(guesser, "ZZZZZ2"));
            }

            assertThrows(TooManyClaimAttemptsException.class, () -> inviteService.preview(guesser, code));
            assertThrows(TooManyClaimAttemptsException.class, () -> inviteService.claim(guesser, code));
            assertThat(families.attachCalls).isEmpty();
            // 다른 계정은 막히지 않는다
            assertThat(inviteService.preview(UUID.randomUUID(), code).profileName())
                    .isEqualTo("첫째");

            // 마지막으로 틀린 때부터 10분이 지나면 풀린다
            clock.setInstant(clock.instant().plus(Duration.ofMinutes(10)));
            assertThat(inviteService.claim(guesser, code).profileId()).isEqualTo(child.profileId());
        }
    }

    @Nested
    class ClaimAttempts {
        private final UUID user = UUID.randomUUID();

        @Test
        @DisplayName("창 안에서 9번 틀렸으면 아직 열려 있고 10번째부터 막힌다")
        void 창_안에서_9번_틀렸으면_아직_열려_있고_10번째부터_막힌다() {
            for (int i = 0; i < 9; i++) claimAttempts.recordFailure(user);
            claimAttempts.check(user);

            claimAttempts.recordFailure(user);
            assertThrows(TooManyClaimAttemptsException.class, () -> claimAttempts.check(user));
        }

        @Test
        @DisplayName("창은 미끄러진다 — 가장 오래된 실패가 10분을 넘기면 하나만큼 풀린다")
        void 창은_미끄러진다() {
            Instant start = clock.instant();
            for (int i = 0; i < 10; i++) {
                clock.setInstant(start.plus(Duration.ofMinutes(i)));
                claimAttempts.recordFailure(user);
            }
            assertThrows(TooManyClaimAttemptsException.class, () -> claimAttempts.check(user));

            // 0분의 실패가 창 밖으로 나가면 9개가 남아 한 번 더 넣을 수 있다
            clock.setInstant(start.plus(Duration.ofMinutes(10)));
            claimAttempts.check(user);
            claimAttempts.recordFailure(user);
            assertThrows(TooManyClaimAttemptsException.class, () -> claimAttempts.check(user));
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

            // 재동의가 지금 상태의 철회 시각을 걷어도 이력의 철회 줄은 남는다
            assertThat(families.consentEvents)
                    .extracting(ConsentEvent::kind, ConsentEvent::occurredAt)
                    .containsExactly(
                            tuple(ConsentEvent.Kind.GRANTED, grantedAt),
                            tuple(ConsentEvent.Kind.REVOKED, grantedAt.plusSeconds(60)),
                            tuple(ConsentEvent.Kind.GRANTED, grantedAt.plusSeconds(120)));
        }

        @Test
        @DisplayName("자기 프로필의 동의는 SELF_CONSENT 로 막는다")
        void 자기_프로필의_동의는_SELF_CONSENT_로_막는다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();

            assertThrows(
                    SelfConsentException.class,
                    () -> settingsService.updateConsent(parentUser, owner, new GuardianConsent(true, true)));
            assertThrows(
                    SelfConsentException.class,
                    () -> settingsService.updateConsent(parentUser, owner, new GuardianConsent(false, false)));
            assertThat(families.consentEvents).isEmpty();
        }

        @Test
        @DisplayName("거둔 동의는 만 14세 생일이 지나도 막힌 채고, 보호자가 다시 동의하면 풀린다")
        void 거둔_동의는_만_14세_생일이_지나도_막힌_채다() {
            CreatedFamily family = createFamily();
            ProfileSummary child =
                    addChild(family.familyId(), today.minusYears(14).plusDays(1));
            settingsService.updateConsent(parentUser, child.profileId(), new GuardianConsent(false, false));

            clock.setInstant(clock.instant().plus(Duration.ofDays(2)));
            ProfileSummary afterBirthday = summaryOf(child.profileId());
            assertThat(afterBirthday.consentRequired()).isTrue();
            assertThat(afterBirthday.consentGiven()).isFalse();
            assertThat(afterBirthday.measurable()).isFalse();

            ConsentState regranted =
                    settingsService.updateConsent(parentUser, child.profileId(), new GuardianConsent(true, true));
            assertThat(regranted.consentGiven()).isTrue();
            assertThat(regranted.measurable()).isTrue();
            assertThat(summaryOf(child.profileId()).consentRequired()).isFalse();
        }

        @Test
        @DisplayName("프로필 고치기 — 응답은 고친 뒤 요약이고, 생일을 고쳐 만 14세 미만이 되면 동의가 필요한 상태로 바로 바뀐다")
        void 프로필_고치기_응답은_고친_뒤_요약이다() {
            CreatedFamily family = createFamily();
            ProfileSummary teen = familyService.addMember(
                    parentUser,
                    family.familyId(),
                    "큰애",
                    LocalDate.of(2006, 3, 2),
                    Sex.F,
                    ProfileRole.CHILD,
                    null,
                    null,
                    null);
            assertThat(teen.ageGroup()).isEqualTo(AgeGroup.ADULT);
            assertThat(teen.consentRequired()).isFalse();
            assertThat(teen.measurable()).isTrue();

            ProfileSummary edited = settingsService.editProfile(
                    parentUser, teen.profileId(), new ProfileEdit("서연", LocalDate.of(2016, 3, 2), Sex.M));

            assertThat(edited.name()).isEqualTo("서연");
            assertThat(edited.sex()).isEqualTo(Sex.M);
            assertThat(edited.ageGroup()).isEqualTo(AgeGroup.YOUTH);
            assertThat(edited.consentRequired()).isTrue();
            assertThat(edited.consentGiven()).isFalse();
            assertThat(edited.measurable()).isFalse();
            assertThat(families.findByProfileId(teen.profileId())
                            .profile(teen.profileId())
                            .getBirthDate())
                    .isEqualTo(LocalDate.of(2016, 3, 2));
            // 고치기는 동의를 바꾸지 않으므로 이력이 없다
            assertThat(families.consentEvents).isEmpty();
        }

        @Test
        @DisplayName("프로필 고치기 — 보호자만, 계정이 붙은 다른 사람은 못 고치고, PARENT 를 만 14세 미만으로 못 고친다")
        void 프로필_고치기_권한과_나이() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            ProfileSummary child = addChild(family.familyId());
            UUID childUser = UUID.randomUUID();
            families.attachUserIfUnclaimed(child.profileId(), childUser, clock.instant());
            ProfileEdit rename = new ProfileEdit("새 이름", null, null);

            assertThrows(
                    NotAParentException.class, () -> settingsService.editProfile(childUser, child.profileId(), rename));
            assertThrows(
                    NotOwnProfileException.class,
                    () -> settingsService.editProfile(parentUser, child.profileId(), rename));
            assertThrows(
                    FamilyAccessDeniedException.class,
                    () -> settingsService.editProfile(UUID.randomUUID(), owner, rename));
            assertThrows(
                    ProfileNotFoundException.class,
                    () -> settingsService.editProfile(parentUser, UUID.randomUUID(), rename));
            assertThrows(
                    Under14NotAllowedException.class,
                    () -> settingsService.editProfile(
                            parentUser, owner, new ProfileEdit(null, today.minusYears(13), null)));

            assertThat(settingsService.editProfile(parentUser, owner, rename).name())
                    .isEqualTo("새 이름");
        }

        private ProfileSummary summaryOf(UUID profileId) {
            return summaries.summary(families.findByProfileId(profileId).profile(profileId));
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

            Cheer cheer = send(parentUser, family.familyId(), owner, child, "힘내!", null);

            assertThat(cheer.familyId()).isEqualTo(family.familyId());
            assertThat(cheer.message()).isEqualTo("힘내!");
            assertThat(cheer.stickerId()).isNull();
            assertThat(cheer.createdAt()).isEqualTo(clock.instant());
            assertThat(cheers.cheers).hasSize(1);

            assertThrows(SelfCheerException.class, () -> send(parentUser, family.familyId(), owner, owner, "나", null));
            assertThrows(
                    NotFamilyMemberException.class,
                    () -> send(parentUser, family.familyId(), owner, UUID.randomUUID(), "?", null));
            assertThrows(
                    FamilyAccessDeniedException.class,
                    () -> send(UUID.randomUUID(), family.familyId(), owner, child, "?", null));
            assertThrows(
                    FamilyNotFoundException.class, () -> send(parentUser, UUID.randomUUID(), owner, child, "?", null));
            assertThrows(
                    IllegalArgumentException.class, () -> send(parentUser, family.familyId(), owner, child, " ", null));
        }

        @Test
        @DisplayName("보호자는 계정 없는 아이 이름으로 보낼 수 있고 계정 있는 아이나 부모 자리 이름으로는 못 보낸다")
        void 보호자는_계정_없는_아이_이름으로_보낼_수_있고_계정_있는_아이나_부모_자리_이름으로는_못_보낸다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            UUID dadSeat = addParentSeat(family.familyId());

            Cheer done = send(parentUser, family.familyId(), child, owner, "다 했어요", null);

            assertThat(done.fromProfileId()).isEqualTo(child);
            assertThat(done.toProfileId()).isEqualTo(owner);
            assertThat(cheers.cheers).hasSize(1);
            assertThrows(
                    CannotActAsProfileException.class,
                    () -> send(parentUser, family.familyId(), dadSeat, child, "?", null));

            UUID childUser = UUID.randomUUID();
            families.attachUserIfUnclaimed(child, childUser, clock.instant());
            assertThrows(
                    CannotActAsProfileException.class,
                    () -> send(parentUser, family.familyId(), child, owner, "?", null));
            send(childUser, family.familyId(), child, owner, "다 했어요", null);
            assertThat(cheers.cheers).hasSize(2);
        }

        @Test
        @DisplayName("같은 대상에게 분당 5회를 넘기면 TOO_MANY")
        void 같은_대상에게_분당_5회를_넘기면_TOO_MANY() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            for (int i = 0; i < 5; i++) {
                send(parentUser, family.familyId(), owner, child, null, "star");
            }

            assertThrows(
                    TooManyCheersException.class,
                    () -> send(parentUser, family.familyId(), owner, child, null, "star"));

            clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
            send(parentUser, family.familyId(), owner, child, null, "star");
            assertThat(cheers.cheers).hasSize(6);
        }

        @Test
        @DisplayName("kind 가 없으면 부모가 보내거나 아이가 받으면 PRAISE, 아이→부모는 스티커가 있으면 THANKS 없으면 DONE")
        void kind_가_없으면_역할과_스티커로_정한다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();

            assertThat(send(parentUser, family.familyId(), owner, child, "최고야", "star")
                            .kind())
                    .isEqualTo(CheerKind.PRAISE);
            assertThat(send(parentUser, family.familyId(), owner, child, "힘내", null)
                            .kind())
                    .isEqualTo(CheerKind.PRAISE);
            assertThat(send(parentUser, family.familyId(), child, owner, "운동 다 했어요!", null)
                            .kind())
                    .isEqualTo(CheerKind.DONE);
            // 전환 기간: 답할 스티커(replyToCheerId) 없이 온 옛 고마워요도 받는다
            Cheer oldThanks = send(parentUser, family.familyId(), child, owner, "고마워요 · 사랑해", "heart");
            assertThat(oldThanks.kind()).isEqualTo(CheerKind.THANKS);
            assertThat(oldThanks.replyToCheerId()).isNull();
        }

        @Test
        @DisplayName("칭찬은 보호자만 아이에게 보내고, 다 했어요 · 고마워요는 아이가 보호자에게만 보낸다")
        void 칭찬은_보호자만_아이에게_보내고_다_했어요_고마워요는_아이가_보호자에게만_보낸다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            UUID second = addChild(family.familyId(), new GuardianConsent(true, true), LocalDate.of(2020, 1, 1), "둘째")
                    .profileId();
            UUID dadSeat = addParentSeat(family.familyId());
            UUID family1 = family.familyId();

            assertThrows(NotAParentException.class, () -> sendAs(child, owner, CheerKind.PRAISE, "star", null));
            // kind 없이 아이가 아이에게 보내면 PRAISE 로 읽혀 막힌다
            assertThrows(NotAParentException.class, () -> send(parentUser, family1, child, second, "잘했어", "star"));
            assertThrows(
                    CheerKindNotAllowedException.class, () -> sendAs(owner, dadSeat, CheerKind.PRAISE, "star", null));
            assertThrows(CheerKindNotAllowedException.class, () -> sendAs(owner, child, CheerKind.DONE, null, null));
            assertThrows(CheerKindNotAllowedException.class, () -> sendAs(child, second, CheerKind.DONE, null, null));
            assertThat(cheers.cheers).isEmpty();
        }

        @Test
        @DisplayName("고마워요는 내가 받은 칭찬 스티커 하나에 한 번, 보낸 사람에게만 돌려보낸다")
        void 고마워요는_내가_받은_칭찬_스티커_하나에_한_번_보낸_사람에게만_돌려보낸다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            UUID dadSeat = addParentSeat(family.familyId());
            UUID sticker = sendAs(owner, child, CheerKind.PRAISE, "star", null).id();
            UUID words = sendAs(owner, child, CheerKind.PRAISE, null, null).id();
            UUID done = sendAs(child, owner, CheerKind.DONE, null, null).id();

            assertThrows(InvalidCheerException.class, () -> sendAs(child, owner, CheerKind.THANKS, "heart", null));
            assertThrows(InvalidCheerException.class, () -> sendAs(child, owner, CheerKind.THANKS, null, sticker));
            assertThrows(InvalidCheerException.class, () -> sendAs(child, owner, CheerKind.DONE, null, sticker));
            assertThrows(
                    CheerNotFoundException.class,
                    () -> sendAs(child, owner, CheerKind.THANKS, "heart", UUID.randomUUID()));
            assertThrows(NotAReplyTargetException.class, () -> sendAs(child, owner, CheerKind.THANKS, "heart", done));
            assertThrows(NotAReplyTargetException.class, () -> sendAs(child, owner, CheerKind.THANKS, "heart", words));
            assertThrows(
                    NotAReplyTargetException.class, () -> sendAs(child, dadSeat, CheerKind.THANKS, "heart", sticker));

            Cheer thanks = sendAs(child, owner, CheerKind.THANKS, "heart", sticker);

            assertThat(thanks.kind()).isEqualTo(CheerKind.THANKS);
            assertThat(thanks.replyToCheerId()).isEqualTo(sticker);
            assertThrows(AlreadyThankedException.class, () -> sendAs(child, owner, CheerKind.THANKS, "clap", sticker));
            // kind 를 안 보내도 replyToCheerId 가 있으면 같은 규칙을 탄다
            assertThrows(
                    AlreadyThankedException.class,
                    () -> cheerService.cheer(
                            parentUser,
                            family.familyId(),
                            new SendCheerCommand(child, owner, null, null, "clap", null, sticker)));
        }

        @Test
        @DisplayName("missionId 는 이 가족의 미션이어야 한다 — 없는 미션 · 다른 가족 미션은 MISSION_NOT_FOUND")
        void missionId_는_이_가족의_미션이어야_한다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            UUID ours = UUID.randomUUID();
            UUID theirs = UUID.randomUUID();
            missionFamilies.put(ours, family.familyId());
            missionFamilies.put(theirs, UUID.randomUUID());

            Cheer done = cheerService.cheer(
                    parentUser,
                    family.familyId(),
                    new SendCheerCommand(child, owner, CheerKind.DONE, "다 했어요", null, ours, null));

            assertThat(done.missionId()).isEqualTo(ours);
            for (UUID missionId : List.of(theirs, UUID.randomUUID())) {
                assertThrows(
                        CheerMissionNotFoundException.class,
                        () -> cheerService.cheer(
                                parentUser,
                                family.familyId(),
                                new SendCheerCommand(owner, child, CheerKind.PRAISE, null, "star", missionId, null)));
            }
            assertThat(cheers.cheers).hasSize(1);
        }

        @Test
        @DisplayName("저장한 응원마다 CheerSent 를 한 번 발행하고, 막힌 응원은 발행하지 않는다")
        void 저장한_응원마다_CheerSent_를_한_번_발행한다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            Cheer praise = sendAs(owner, child, CheerKind.PRAISE, "star", null);
            Cheer thanks = sendAs(child, owner, CheerKind.THANKS, "heart", praise.id());
            assertThrows(
                    AlreadyThankedException.class, () -> sendAs(child, owner, CheerKind.THANKS, "clap", praise.id()));

            assertThat(published)
                    .containsExactly(
                            new CheerSent(
                                    praise.id(),
                                    family.familyId(),
                                    owner,
                                    child,
                                    CheerKind.PRAISE,
                                    "star",
                                    "한마디",
                                    null,
                                    null,
                                    praise.createdAt()),
                            new CheerSent(
                                    thanks.id(),
                                    family.familyId(),
                                    child,
                                    owner,
                                    CheerKind.THANKS,
                                    "heart",
                                    "한마디",
                                    null,
                                    praise.id(),
                                    thanks.createdAt()));
        }

        @Test
        @DisplayName("받은 응원 목록은 최근 것부터 size 건이고 받은 사람 · 보낸 사람 · 미션으로 거른다")
        void 받은_응원_목록은_최근_것부터_size_건이고_받은_사람_보낸_사람_미션으로_거른다() {
            CreatedFamily family = createFamily();
            UUID familyId = family.familyId();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(familyId).profileId();
            UUID mission = UUID.randomUUID();
            missionFamilies.put(mission, familyId);
            Cheer done = cheerService.cheer(
                    parentUser, familyId, new SendCheerCommand(child, owner, null, "다 했어요", null, mission, null));
            clock.setInstant(clock.instant().plusSeconds(1));
            Cheer praise = cheerService.cheer(
                    parentUser, familyId, new SendCheerCommand(owner, child, null, "최고야", "star", mission, null));
            clock.setInstant(clock.instant().plusSeconds(1));
            Cheer other = send(parentUser, familyId, owner, child, "힘내", null);

            List<CheerView> all = cheerService.list(parentUser, familyId, null, null, null, 20);

            assertThat(all).extracting(CheerView::cheerId).containsExactly(other.id(), praise.id(), done.id());
            CheerView praiseView = all.get(1);
            assertThat(praiseView.fromName()).isEqualTo("엄마");
            assertThat(praiseView.kind()).isEqualTo(CheerKind.PRAISE);
            assertThat(praiseView.stickerId()).isEqualTo("star");
            assertThat(praiseView.missionId()).isEqualTo(mission);
            assertThat(cheerService.list(parentUser, familyId, child, null, null, 20))
                    .extracting(CheerView::cheerId)
                    .containsExactly(other.id(), praise.id());
            assertThat(cheerService.list(parentUser, familyId, null, child, null, 20))
                    .extracting(CheerView::cheerId)
                    .containsExactly(done.id());
            assertThat(cheerService.list(parentUser, familyId, null, null, mission, 20))
                    .extracting(CheerView::cheerId)
                    .containsExactly(praise.id(), done.id());
            assertThat(cheerService.list(parentUser, familyId, null, null, null, 1))
                    .extracting(CheerView::cheerId)
                    .containsExactly(other.id());
            assertThat(cheerService.list(parentUser, familyId, UUID.randomUUID(), null, null, 20))
                    .isEmpty();
            assertThrows(
                    IllegalArgumentException.class, () -> cheerService.list(parentUser, familyId, null, null, null, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> cheerService.list(parentUser, familyId, null, null, null, CheerService.MAX_LIST_SIZE + 1));
            assertThrows(
                    FamilyAccessDeniedException.class,
                    () -> cheerService.list(UUID.randomUUID(), familyId, null, null, null, 20));
        }

        private Cheer sendAs(
                UUID from, UUID to, CheerKind kind, @Nullable String stickerId, @Nullable UUID replyToCheerId) {
            UUID familyId =
                    Objects.requireNonNull(families.findByProfileId(from)).getId();
            return cheerService.cheer(
                    parentUser, familyId, new SendCheerCommand(from, to, kind, "한마디", stickerId, null, replyToCheerId));
        }

        private UUID addParentSeat(UUID familyId) {
            return familyService
                    .addMember(
                            parentUser,
                            familyId,
                            "아빠",
                            LocalDate.of(1986, 1, 1),
                            Sex.M,
                            ProfileRole.PARENT,
                            null,
                            null,
                            null)
                    .profileId();
        }
    }

    @Nested
    class Queries {
        private final ProfileQueryService profileQuery = new ProfileQueryService(families, summaries);
        private final FamilyAccessService access = new FamilyAccessService(families, summaries);
        private final CheerQueryService cheerQuery = new CheerQueryService(cheers, families);

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
        @DisplayName("FamilyAccess.requireActingAs 는 본인 프로필과 계정 없는 아이 프로필만 통과시킨다")
        void FamilyAccess_requireActingAs_는_본인_프로필과_계정_없는_아이_프로필만_통과시킨다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            ProfileSummary child = addChild(family.familyId());
            ProfileSummary claimedChild =
                    addChild(family.familyId(), new GuardianConsent(true, true), LocalDate.of(2016, 1, 1), "둘째");
            UUID childUser = UUID.randomUUID();
            families.attachUserIfUnclaimed(claimedChild.profileId(), childUser, clock.instant());

            assertThat(access.requireActingAs(parentUser, owner).profileId()).isEqualTo(owner);
            assertThat(access.requireActingAs(parentUser, child.profileId()).profileId())
                    .isEqualTo(child.profileId());
            assertThat(access.requireActingAs(childUser, claimedChild.profileId())
                            .profileId())
                    .isEqualTo(claimedChild.profileId());

            assertThrows(
                    CannotActAsProfileException.class,
                    () -> access.requireActingAs(parentUser, claimedChild.profileId()));
            assertThrows(CannotActAsProfileException.class, () -> access.requireActingAs(childUser, child.profileId()));
            assertThrows(NotSameFamilyException.class, () -> access.requireActingAs(UUID.randomUUID(), owner));
            assertThrows(ProfileNotFoundException.class, () -> access.requireActingAs(parentUser, UUID.randomUUID()));
        }

        @Test
        @DisplayName("CheerQuery 는 구간 응원 수를 센다")
        void CheerQuery_는_구간_응원_수를_센다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            Instant start = clock.instant();
            send(parentUser, family.familyId(), owner, child, "a", null);
            clock.setInstant(start.plus(Duration.ofDays(1)));
            send(parentUser, family.familyId(), owner, child, "b", null);

            assertThat(cheerQuery.countCheers(family.familyId(), start, start.plus(Duration.ofDays(1))))
                    .isEqualTo(1);
            assertThat(cheerQuery.countCheers(family.familyId(), start, start.plus(Duration.ofDays(2))))
                    .isEqualTo(2);
            assertThat(cheerQuery.countCheers(UUID.randomUUID(), start, start.plus(Duration.ofDays(2))))
                    .isZero();
        }

        @Test
        @DisplayName("CheerQuery 는 한 사람이 구간 안에 받은 응원을 kind · 보낸 사람 이름과 함께 오래된 것부터 준다")
        void CheerQuery_는_한_사람이_구간_안에_받은_응원을_준다() {
            CreatedFamily family = createFamily();
            UUID owner = family.ownerProfile().profileId();
            UUID child = addChild(family.familyId()).profileId();
            Instant start = clock.instant();
            Cheer star = send(parentUser, family.familyId(), owner, child, "최고야", "star");
            send(parentUser, family.familyId(), child, owner, "다 했어요", null);
            clock.setInstant(start.plus(Duration.ofDays(1)));
            Cheer words = send(parentUser, family.familyId(), owner, child, "힘내", null);
            clock.setInstant(start.plus(Duration.ofDays(2)));
            send(parentUser, family.familyId(), owner, child, "내일", null);

            List<CheerView> received = cheerQuery.received(child, start, start.plus(Duration.ofDays(2)));

            assertThat(received).extracting(CheerView::cheerId).containsExactly(star.id(), words.id());
            assertThat(received.getFirst().fromName()).isEqualTo("엄마");
            assertThat(received.getFirst().kind()).isEqualTo(CheerKind.PRAISE);
            assertThat(received.getFirst().stickerId()).isEqualTo("star");
            assertThat(cheerQuery.received(UUID.randomUUID(), start, start.plus(Duration.ofDays(2))))
                    .isEmpty();
        }
    }
}
