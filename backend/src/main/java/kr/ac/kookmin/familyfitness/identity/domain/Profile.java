package kr.ac.kookmin.familyfitness.identity.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.InviteStatus;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Ages;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.domain.SupportMode;
import org.jspecify.annotations.Nullable;

/**
 * 가족 구성원. 로그인 계정({@code userId})과 다르다 — 아이는 계정 없이 부모가 대리 관리한다.
 * 역할은 생성 시 확정되고 초대받는 쪽이 못 고친다. 상태 변경은 애그리게잇 루트({@link Family})를 통해서만 한다.
 */
public class Profile {
    private final UUID id;
    private final UUID familyId;
    private final ProfileRole role;
    private final boolean isOwner;
    private String displayName;
    private LocalDate birthDate;
    private Sex sex;

    @Nullable
    private final BigDecimal heightCm;

    @Nullable
    private final BigDecimal weightKg;

    @Nullable
    private UUID userId;

    @Nullable
    private SupportMode supportMode;

    @Nullable
    private ClaimCode claimCode;

    @Nullable
    private Instant claimCodeClaimedAt;

    /** 지금 코드를 보낸 보호자 프로필. 이 칸이 생기기 전에 발급된 코드는 null 이다. */
    @Nullable
    private UUID claimCodeIssuedBy;

    private ConsentRecord consent;

    public Profile(
            UUID id,
            UUID familyId,
            @Nullable UUID userId,
            ProfileRole role,
            boolean isOwner,
            String displayName,
            LocalDate birthDate,
            Sex sex,
            @Nullable BigDecimal heightCm,
            @Nullable BigDecimal weightKg,
            @Nullable SupportMode supportMode,
            @Nullable ClaimCode claimCode,
            @Nullable Instant claimCodeClaimedAt,
            @Nullable UUID claimCodeIssuedBy,
            ConsentRecord consent) {
        if (displayName.isBlank()) throw new IllegalArgumentException("이름은 비어 있을 수 없다");
        this.id = id;
        this.familyId = familyId;
        this.userId = userId;
        this.role = role;
        this.isOwner = isOwner;
        this.displayName = displayName;
        this.birthDate = birthDate;
        this.sex = sex;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.supportMode = supportMode;
        this.claimCode = claimCode;
        this.claimCodeClaimedAt = claimCodeClaimedAt;
        this.claimCodeIssuedBy = claimCodeIssuedBy;
        this.consent = consent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public @Nullable UUID getUserId() {
        return userId;
    }

    public ProfileRole getRole() {
        return role;
    }

    public boolean isOwner() {
        return isOwner;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Sex getSex() {
        return sex;
    }

    public @Nullable BigDecimal getHeightCm() {
        return heightCm;
    }

    public @Nullable BigDecimal getWeightKg() {
        return weightKg;
    }

    public @Nullable SupportMode getSupportMode() {
        return supportMode;
    }

    public @Nullable ClaimCode getClaimCode() {
        return claimCode;
    }

    public @Nullable Instant getClaimCodeClaimedAt() {
        return claimCodeClaimedAt;
    }

    public @Nullable UUID getClaimCodeIssuedBy() {
        return claimCodeIssuedBy;
    }

    public ConsentRecord getConsent() {
        return consent;
    }

    public boolean isParent() {
        return role == ProfileRole.PARENT;
    }

    public boolean hasAccount() {
        return userId != null;
    }

    public AgeGroup ageGroup(LocalDate today) {
        return AgeGroup.of(birthDate, today);
    }

    /**
     * 보호자 동의가 있어야 하는가 — 만 14세 미만이거나, 동의를 거둔 채다.
     * 거둔 동의는 만 14세 생일이 지나도 저절로 풀리지 않는다. 보호자가 다시 동의해야 풀린다.
     * 다른 모듈은 {@code consentRequired && !consentGiven} 으로 막으므로 거둔 채인 14세 이상도 여기서 true 여야 막힌다.
     */
    public boolean consentRequired(LocalDate today) {
        return GuardianConsent.isRequired(birthDate, today) || consent.isRevoked();
    }

    /** 동의가 살아 있는가. 동의가 필요 없으면(만 14세 이상이고 거둔 적이 없거나 다시 동의함) true */
    public boolean consentGiven(LocalDate today) {
        return !consentRequired(today) || consent.isGiven();
    }

    /** 만 4세 이상이고 (동의 불필요이거나) 동의가 살아 있는가 */
    public boolean measurable(LocalDate today) {
        return Ages.isMeasurable(birthDate, today) && consentGiven(today);
    }

    public InviteStatus inviteStatus(Instant now) {
        ClaimCode code = claimCode;
        if (code == null) return InviteStatus.NONE;
        if (claimCodeClaimedAt != null) return InviteStatus.CLAIMED;
        if (code.isExpired(now)) return InviteStatus.EXPIRED;
        return InviteStatus.ISSUED;
    }

    /** 살아 있는 코드 — 발급됐고 아직 안 썼고 만료 전. 없으면 null. */
    public @Nullable ClaimCode liveClaimCode(Instant now) {
        return inviteStatus(now) == InviteStatus.ISSUED ? claimCode : null;
    }

    void issueInvite(ClaimCode code, UUID issuedBy) {
        if (hasAccount()) throw new AlreadyClaimedException();
        claimCode = code;
        claimCodeClaimedAt = null;
        claimCodeIssuedBy = issuedBy;
    }

    /** 계정을 붙인다. 코드는 CLAIMED 상태를 파생하기 위해 남겨 둔다. */
    public void claim(UUID userId, Instant at) {
        if (hasAccount()) throw new AlreadyClaimedException();
        this.userId = userId;
        this.claimCodeClaimedAt = at;
    }

    void changeSupportMode(SupportMode mode) {
        if (!isParent()) throw new SupportModeNotApplicableException();
        supportMode = mode;
    }

    /** 둘 다 true 면 새로 동의(시각·동의자 갱신), 하나라도 false 면 철회(과거 시각은 유지). */
    void recordConsent(GuardianConsent decision, UUID byUserId, Instant at) {
        consent = decision.isComplete() ? ConsentRecord.granted(at, byUserId) : consent.revoke(at);
    }

    /**
     * 이름 · 생년월일 · 성별을 고친다. null 인 칸은 그대로 둔다. 규칙(누가 · 어느 프로필 · 나이)은 {@link Family#editProfile} 이 본다.
     * 지난 측정의 나이 · 백분위는 측정 때 굳혀 저장했으므로 여기서 바뀌지 않는다.
     */
    void edit(ProfileEdit edit) {
        String name = edit.name();
        if (name != null && name.isBlank()) throw new IllegalArgumentException("이름은 비어 있을 수 없다");
        if (name != null) displayName = name;
        if (edit.birthDate() != null) birthDate = edit.birthDate();
        if (edit.sex() != null) sex = edit.sex();
    }
}
