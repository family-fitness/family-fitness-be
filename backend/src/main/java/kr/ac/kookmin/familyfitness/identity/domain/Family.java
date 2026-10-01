package kr.ac.kookmin.familyfitness.identity.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import kr.ac.kookmin.familyfitness.identity.api.CannotActAsProfileException;
import kr.ac.kookmin.familyfitness.identity.api.NotAParentException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.shared.domain.Ages;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

/**
 * 가족 애그리게잇 루트. 프로필의 생성·초대·동의·참여 수준 변경은 전부 여기를 거친다.
 * 불변식: PARENT 가 최소 한 명(만든 사람이 owner PARENT). PARENT 는 만 14세 이상이다(새로 만들거나 고칠 때 본다).
 * 권한 판단은 HTTP 요청 값이 아니라 저장된 프로필로 한다.
 * 동의를 바꿀 때마다 {@link ConsentEvent} 를 하나씩 모아 두고, 저장소가 저장할 때 {@link #drainConsentEvents} 로 꺼내 이력에 넣는다.
 */
public class Family {
    private final UUID id;
    private final String name;
    private final List<Profile> members;
    private final List<ConsentEvent> pendingConsentEvents = new ArrayList<>();

    private Family(UUID id, String name, List<Profile> members) {
        if (name.isBlank()) throw new IllegalArgumentException("가족 이름은 비어 있을 수 없다");
        this.id = id;
        this.name = name;
        this.members = members;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Profile> getProfiles() {
        return List.copyOf(members);
    }

    public @Nullable Profile memberOf(UUID userId) {
        return members.stream()
                .filter(it -> userId.equals(it.getUserId()))
                .findFirst()
                .orElse(null);
    }

    public @Nullable Profile profileOrNull(UUID profileId) {
        return members.stream()
                .filter(it -> it.getId().equals(profileId))
                .findFirst()
                .orElse(null);
    }

    public Profile profile(UUID profileId) {
        Profile profile = profileOrNull(profileId);
        if (profile == null) throw new ProfileNotFoundException(profileId);
        return profile;
    }

    /** 이 가족의 PARENT 계정이 이 가족의 프로필을 다룰 수 있다. */
    public boolean canManageProfile(UUID userId, UUID profileId) {
        Profile actor = memberOf(userId);
        return actor != null && actor.isParent() && profileOrNull(profileId) != null;
    }

    /**
     * 이 계정이 이 프로필 이름으로 행동할 수 있다(응원 보내기 등).
     * 본인 계정에 붙은 프로필이면 된다. 아니면 {@link #canManageProfile} 을 좁혀, 계정 없는 CHILD 프로필만 대신한다.
     * 계정이 있는 아이는 자기 계정으로 보낸다. 계정 없는 부모 자리를 대신하게 두면 한 보호자가 다른 보호자 이름으로 보낼 수 있다.
     * 그래서 둘 다 대신하지 않는다.
     */
    public boolean canActAs(UUID userId, UUID profileId) {
        Profile target = profileOrNull(profileId);
        if (target == null) return false;
        if (userId.equals(target.getUserId())) return true;
        return canManageProfile(userId, profileId) && target.getRole() == ProfileRole.CHILD && !target.hasAccount();
    }

    /** 구성원이 아니면 {@link FamilyAccessDeniedException}, 구성원이지만 아이면 {@link NotAParentException}. */
    public Profile requireParent(UUID userId) {
        Profile actor = requireMember(userId);
        if (!actor.isParent()) throw new NotAParentException();
        return actor;
    }

    public Profile requireMember(UUID userId) {
        Profile actor = memberOf(userId);
        if (actor == null) throw new FamilyAccessDeniedException();
        return actor;
    }

    /** 아이 프로필 추가 — {@link #addMember} 의 CHILD 편의 메서드. */
    public Profile addChild(
            UUID actorUserId,
            String displayName,
            LocalDate birthDate,
            Sex sex,
            boolean personalConsentGranted,
            boolean healthConsentGranted,
            Instant consentedAt,
            LocalDate today) {
        return addMember(
                actorUserId,
                displayName,
                birthDate,
                sex,
                ProfileRole.CHILD,
                null,
                null,
                new GuardianConsent(personalConsentGranted, healthConsentGranted),
                consentedAt,
                today);
    }

    /**
     * 구성원 추가 — PARENT 만. 만 14세 미만이면 {@code guardianConsent} 가 둘 다 true 여야 하고,
     * 동의 시각·동의자는 서버(여기)가 채운다. 만 4세 미만도 프로필은 만든다(측정만 불가).
     * 만 14세 미만을 PARENT 로 넣지 못한다. 동의를 기록하면 이력(GRANTED)도 하나 남긴다.
     * 키·몸무게는 가입 때 적은 값이고 없으면 null 이다. 범위는 요청 검증이 본다.
     */
    public Profile addMember(
            UUID actorUserId,
            String displayName,
            LocalDate birthDate,
            Sex sex,
            ProfileRole role,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            @Nullable GuardianConsent guardianConsent,
            Instant consentedAt,
            LocalDate today) {
        Profile actor = requireParent(actorUserId);
        if (birthDate.isAfter(today)) throw new IllegalArgumentException("생년월일은 미래일 수 없다");
        if (role == ProfileRole.PARENT) requireGuardianAge(birthDate, today);
        ConsentRecord consent;
        if (guardianConsent != null && guardianConsent.isComplete()) {
            requireGuardianAge(actor.getBirthDate(), today);
            consent = ConsentRecord.granted(consentedAt, actorUserId);
        } else if (GuardianConsent.isRequired(birthDate, today)) {
            throw new GuardianConsentRequiredException();
        } else {
            consent = ConsentRecord.NONE;
        }
        Profile profile = new Profile(
                UUID.randomUUID(),
                id,
                null,
                role,
                false,
                displayName,
                birthDate,
                sex,
                heightCm,
                weightKg,
                null,
                null,
                null,
                null,
                consent);
        members.add(profile);
        if (consent.isGiven()) {
            pendingConsentEvents.add(ConsentEvent.of(
                    profile.getId(), actorUserId, Objects.requireNonNull(guardianConsent), consentedAt));
        }
        return profile;
    }

    /**
     * 초대 발급 — PARENT 만. 계정이 이미 붙은 프로필에는 발급하지 않는다.
     * 살아 있는 코드(만료 전 · 안 씀)가 있으면 그 코드를 그대로 돌려준다 — 새로 만들면 먼저 보낸 코드가 죽는다.
     * 없을 때만 {@code newCode} 로 만들고, 보낸 보호자 프로필을 같이 남긴다.
     */
    public ClaimCode issueInvite(UUID actorUserId, UUID profileId, Instant now, Supplier<ClaimCode> newCode) {
        Profile actor = requireParent(actorUserId);
        Profile profile = profile(profileId);
        if (profile.hasAccount()) throw new AlreadyClaimedException();
        ClaimCode live = profile.liveClaimCode(now);
        if (live != null) return live;
        ClaimCode code = newCode.get();
        profile.issueInvite(code, actor.getId());
        return code;
    }

    /**
     * 가족 초대 발급(초대 먼저). PARENT 만 낸다. 역할만 정하고, 이름과 생년월일은 코드로 들어온 사람이 넣는다.
     * CHILD 초대는 들어올 아이가 만 14세 미만일 수 있어 보호자 동의(둘 다 true)를 미리 받는다. 없거나 하나라도 false 면
     * 422 CONSENT_REQUIRED, 동의하는 보호자가 만 14세 미만이면 422 UNDER_14_NOT_ALLOWED 다. PARENT 초대는 동의를 받지 않는다(보내도
     * 남기지 않는다). 판정 차례: 구성원 아님 403 NOT_SAME_FAMILY, 아이 계정 403 NOT_A_PARENT, 동의, 동의하는 보호자 나이.
     * 코드는 판정을 다 지난 뒤에만 {@code newCode} 로 만든다(중복 검사가 DB 를 읽는다).
     */
    public FamilyInvite issueFamilyInvite(
            UUID actorUserId,
            ProfileRole role,
            @Nullable GuardianConsent guardianConsent,
            Instant now,
            LocalDate today,
            Supplier<ClaimCode> newCode) {
        Profile actor = requireParent(actorUserId);
        GuardianConsent consent = null;
        UUID consentBy = null;
        if (role == ProfileRole.CHILD) {
            if (guardianConsent == null || !guardianConsent.isComplete()) throw new GuardianConsentRequiredException();
            requireGuardianAge(actor.getBirthDate(), today);
            consent = guardianConsent;
            consentBy = actorUserId;
        }
        return new FamilyInvite(newCode.get(), id, role, consent, consentBy, actor.getId(), now, null, null);
    }

    /**
     * 초대 코드 사용 전 규칙 검사. 실제 계정 연결은 조건부 UPDATE(동시성)로 저장소가 하므로 여기서는 상태를 바꾸지 않는다.
     * 순서: 코드 없음 → 이미 사용 → 만료 → 이미 이 가족 구성원 → 다른 가족에 프로필이 있음(한 계정 한 가족).
     * {@code accountHasProfile} 은 이 계정에 붙은 프로필이 어느 가족에든 있는가다. 이 가족이면 앞에서 ALREADY_MEMBER 로 끝난다.
     */
    public Profile prepareClaim(UUID profileId, UUID userId, Instant now, boolean accountHasProfile) {
        Profile profile = claimableSeat(profileId, now);
        if (memberOf(userId) != null) throw new AlreadyMemberException();
        if (accountHasProfile) throw new AlreadyInFamilyException("다른 가족에 이미 프로필이 있습니다");
        return profile;
    }

    /** 미리 보기 판정 — 코드 사용과 같되 구성원 검사는 하지 않는다. 순서: 코드 없음 → 이미 사용 → 만료. */
    public Profile claimableSeat(UUID profileId, Instant now) {
        Profile profile = profile(profileId);
        ClaimCode code = profile.getClaimCode();
        if (code == null) throw new ClaimCodeNotFoundException();
        if (profile.hasAccount()) throw new AlreadyClaimedException();
        if (code.isExpired(now)) throw new ClaimCodeExpiredException();
        return profile;
    }

    /** 참여 수준 변경 — 본인 계정에 붙은 PARENT 프로필만. */
    public Profile changeSupportMode(UUID actorUserId, UUID profileId, SupportMode mode) {
        Profile profile = profile(profileId);
        if (!actorUserId.equals(profile.getUserId())) throw new NotOwnProfileException();
        profile.changeSupportMode(mode);
        return profile;
    }

    /**
     * 동의 변경 — 이 가족의 PARENT 만, 대상은 CHILD 만. 판정 순서: 구성원 · 보호자 → 대상 있음 → 자기 프로필 아님 → 대상이 CHILD
     * → 행위자 만 14세 이상. 부여든 철회든 한 번에 이력 한 줄을 남긴다. 재동의는 지금 상태의 철회 시각을 걷지만 이력의 철회 줄은 그대로다.
     */
    public Profile updateConsent(
            UUID actorUserId, UUID profileId, GuardianConsent decision, Instant at, LocalDate today) {
        Profile actor = requireParent(actorUserId);
        Profile profile = profile(profileId);
        if (actor.getId().equals(profile.getId())) throw new SelfConsentException();
        if (profile.isParent()) throw new ConsentNotApplicableException();
        requireGuardianAge(actor.getBirthDate(), today);
        profile.recordConsent(decision, actorUserId, at);
        pendingConsentEvents.add(ConsentEvent.of(profileId, actorUserId, decision, at));
        return profile;
    }

    /**
     * 이름 · 생년월일 · 성별 고치기 — 이 가족의 PARENT 만. 고칠 수 있는 프로필은 계정 없는 프로필과 행위자 자기 프로필이다
     * (계정이 붙은 다른 사람의 이름 · 생일은 그 사람 것이라 대신 고치지 않는다).
     * 판정 순서: 구성원 · 보호자 → 대상 있음 → 고칠 수 있는 프로필 → 생년월일이 미래 아님 → PARENT 는 만 14세 이상.
     * 생일을 고쳐 만 14세 미만이 되면 동의가 필요한 상태가 되고, 동의 기록이 없으면 바로 막힌다(consentGiven=false).
     */
    public Profile editProfile(UUID actorUserId, UUID profileId, ProfileEdit edit, LocalDate today) {
        requireParent(actorUserId);
        Profile profile = profile(profileId);
        if (profile.hasAccount() && !actorUserId.equals(profile.getUserId())) {
            throw new NotOwnProfileException("계정이 붙은 다른 사람의 프로필은 고칠 수 없습니다");
        }
        LocalDate birthDate = edit.birthDate() != null ? edit.birthDate() : profile.getBirthDate();
        if (birthDate.isAfter(today)) throw new IllegalArgumentException("생년월일은 미래일 수 없다");
        if (profile.isParent()) requireGuardianAge(birthDate, today);
        profile.edit(edit);
        return profile;
    }

    /**
     * 가족을 만든 보호자(profiles.is_owner). 가족마다 한 사람이다. 구성원이 빠질 때 그 사람을 가리키던 기록(미션을 만든 사람, 쉬는 날
     * 카드를 쓴 사람 등)을 이 프로필로 돌린다. 오너가 없는 가족은 만들 수 없으므로 없으면 데이터가 깨진 것이다.
     */
    public Profile owner() {
        return members.stream()
                .filter(Profile::isOwner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("오너가 없는 가족: " + id));
    }

    /**
     * 구성원 내보내기 판정. 내보낼 프로필을 돌려준다. 차례: 구성원 아님 403 NOT_SAME_FAMILY → 오너 아님 403 NOT_FAMILY_OWNER →
     * 이 가족 프로필 아님 404 PROFILE_NOT_FOUND → 자기 프로필 409 CANNOT_REMOVE_SELF(자기는 탈퇴로 나간다).
     */
    public Profile memberToRemove(UUID actorUserId, UUID profileId) {
        Profile actor = requireMember(actorUserId);
        if (!actor.isOwner()) throw new NotFamilyOwnerException();
        Profile target = profile(profileId);
        if (target.getId().equals(actor.getId())) throw new CannotRemoveSelfException();
        return target;
    }

    /**
     * 이 프로필의 계정이 탈퇴할 때 가족도 지우는가. 오너가 아니면 그 사람만 빠지고 가족은 남는다(false). 오너는 혼자 남았을 때만
     * 가족까지 지우고(true), 다른 프로필이 하나라도 있으면 409 FAMILY_NOT_EMPTY 다. 계정 없는 아이 프로필도 다른 프로필로 센다.
     */
    public boolean leavesNothingBehind(Profile leaving) {
        if (!leaving.isOwner()) return false;
        if (members.size() > 1) throw new FamilyNotEmptyException();
        return true;
    }

    /** 모아 둔 동의 이력을 꺼내고 비운다. 저장소가 저장할 때 한 번 부른다. */
    public List<ConsentEvent> drainConsentEvents() {
        List<ConsentEvent> drained = List.copyOf(pendingConsentEvents);
        pendingConsentEvents.clear();
        return drained;
    }

    /** 만 14세 미만은 보호자 자리에 서거나 보호자 동의를 하지 못한다. */
    private static void requireGuardianAge(LocalDate birthDate, LocalDate today) {
        if (Ages.requiresGuardianConsent(birthDate, today)) throw new Under14NotAllowedException();
    }

    /**
     * 응원 규칙: 보내는 프로필은 내가 그 이름으로 행동할 수 있는 것({@link #canActAs}),
     * 받는 프로필은 같은 가족의 다른 사람.
     */
    public void validateCheer(UUID actorUserId, UUID fromProfileId, UUID toProfileId) {
        requireMember(actorUserId);
        if (!canActAs(actorUserId, fromProfileId)) {
            throw new CannotActAsProfileException("fromProfileId 는 내 프로필이나 계정 없는 아이 프로필이어야 합니다");
        }
        if (fromProfileId.equals(toProfileId)) throw new SelfCheerException();
        if (profileOrNull(toProfileId) == null) throw new NotFamilyMemberException();
    }

    /**
     * 가족 생성. 만든 사람은 항상 owner PARENT 이고 본인 계정에 바로 연결된다.
     * 생년월일이 미래면 거절하고, 만 14세 미만이면 가족을 만들지 못한다(아이 혼자 가입 불가).
     */
    public static Family createWithParent(
            UUID parentUserId, String familyName, String parentName, LocalDate birthDate, Sex sex, LocalDate today) {
        if (birthDate.isAfter(today)) throw new IllegalArgumentException("생년월일은 미래일 수 없다");
        requireGuardianAge(birthDate, today);
        UUID familyId = UUID.randomUUID();
        Profile owner = new Profile(
                UUID.randomUUID(),
                familyId,
                parentUserId,
                ProfileRole.PARENT,
                true,
                parentName,
                birthDate,
                sex,
                null,
                null,
                null,
                null,
                null,
                null,
                ConsentRecord.NONE);
        return new Family(familyId, familyName, new ArrayList<>(List.of(owner)));
    }

    /** 저장소가 저장된 상태를 되살릴 때 쓴다. */
    public static Family restore(UUID id, String name, List<Profile> profiles) {
        if (!profiles.stream().allMatch(it -> it.getFamilyId().equals(id))) {
            throw new IllegalArgumentException("다른 가족의 프로필이 섞여 있다");
        }
        return new Family(id, name, new ArrayList<>(profiles));
    }
}
