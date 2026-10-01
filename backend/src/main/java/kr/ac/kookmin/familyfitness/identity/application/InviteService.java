package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyInviteRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyClaimedException;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
import kr.ac.kookmin.familyfitness.identity.domain.FamilyInvite;
import kr.ac.kookmin.familyfitness.identity.domain.NewMember;
import kr.ac.kookmin.familyfitness.identity.domain.Profile;
import kr.ac.kookmin.familyfitness.shared.config.AppProperties;
import kr.ac.kookmin.familyfitness.shared.domain.ProfileRole;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 코드 발급 · 미리 보기 · 사용. 사용은 조건부 UPDATE 한 문장으로 동시 요청을 가른다.
 * 미리 보기와 사용은 없는 코드를 넣은 횟수를 계정마다 같이 센다({@link ClaimAttemptLimiter}).
 */
@Service
@Transactional
public class InviteService {
    private final FamilyRepository families;
    private final FamilyInviteRepository familyInvites;
    private final InviteCodes codes;
    private final AppProperties props;
    private final IdentityClock clock;
    private final ClaimAttemptLimiter attempts;

    public InviteService(
            FamilyRepository families,
            FamilyInviteRepository familyInvites,
            InviteCodes codes,
            AppProperties props,
            IdentityClock clock,
            ClaimAttemptLimiter attempts) {
        this.families = families;
        this.familyInvites = familyInvites;
        this.codes = codes;
        this.props = props;
        this.clock = clock;
        this.attempts = attempts;
    }

    /**
     * 살아 있는 코드가 있으면 그 코드를, 없으면 새 코드를 준다. 새 코드는 필요할 때만 만든다(중복 검사가 DB 를 읽는다).
     * 새 코드는 가족 초대코드와도 겹치지 않는다({@link InviteCodes}).
     */
    public Invitation issueInvite(UUID userId, UUID profileId) {
        Family family = families.findByProfileId(profileId);
        if (family == null) throw new ProfileNotFoundException(profileId);
        Instant now = clock.now();
        ClaimCode code = family.issueInvite(userId, profileId, now, () -> codes.fresh(now));
        families.save(family);
        return new Invitation(code, shareUrlOf(code));
    }

    /**
     * 판정은 사용과 같되 구성원 검사는 하지 않는다: 시도 초과 429, 없음 404, 이미 사용 409, 만료 410 차례다. 가족 초대코드(kind
     * FAMILY)는 자리가 없어 자리 이름과 연령대가 null 이다.
     */
    @Transactional(readOnly = true)
    public InvitePreview preview(UUID userId, String rawCode) {
        return switch (find(userId, rawCode)) {
            case Seat seat -> previewOf(seat);
            case FamilyCode familyCode -> previewOf(familyCode);
        };
    }

    private InvitePreview previewOf(Seat seat) {
        Profile profile = seat.family().claimableSeat(seat.profile().getId(), clock.now());
        ClaimCode code = Objects.requireNonNull(profile.getClaimCode());
        return new InvitePreview(
                InviteKind.PROFILE,
                seat.family().getName(),
                profile.getDisplayName(),
                profile.getRole(),
                profile.ageGroup(clock.today()),
                issuerName(seat.family(), profile),
                code.expiresAt());
    }

    private InvitePreview previewOf(FamilyCode target) {
        FamilyInvite invite = target.invite();
        invite.requireClaimable(clock.now());
        Profile issuer = target.family().profileOrNull(invite.issuedByProfileId());
        return new InvitePreview(
                InviteKind.FAMILY,
                target.family().getName(),
                null,
                invite.role(),
                null,
                issuer == null ? null : issuer.getDisplayName(),
                invite.code().expiresAt());
    }

    /** 자리 초대코드만 쓰는 곳(가족 초대코드면 400 BAD_REQUEST). {@link #claim(UUID, String, NewMember)} 와 같다. */
    public ClaimResult claim(UUID userId, String rawCode) {
        return claim(userId, rawCode, null);
    }

    /**
     * 판정 순서: 시도 초과 429 → 없음 404 → 이미 사용 409 → 만료 410 → 이 가족 구성원 409 → 다른 가족 409 ALREADY_IN_FAMILY.
     * 가족 초대코드는 그 뒤에 정보 없음 400 BAD_REQUEST, PARENT 초대인데 만 14세 미만 422 UNDER_14_NOT_ALLOWED 가 붙는다.
     * 사전 검사를 함께 지나친 동시 요청은 profiles.user_id 유니크 인덱스가 막고 저장소가 ALREADY_IN_FAMILY 로 바꾼다.
     *
     * @param member 가족 초대코드로 들어온 사람이 넣은 정보. 자리 초대코드는 보지 않는다
     */
    public ClaimResult claim(UUID userId, String rawCode, @Nullable NewMember member) {
        return switch (find(userId, rawCode)) {
            case Seat seat -> claimSeat(userId, seat);
            case FamilyCode familyCode -> join(userId, familyCode, member);
        };
    }

    private ClaimResult claimSeat(UUID userId, Seat seat) {
        Family family = seat.family();
        Profile profile = seat.profile();
        Instant now = clock.now();
        family.prepareClaim(
                profile.getId(), userId, now, !families.profilesOfUser(userId).isEmpty());
        if (!families.attachUserIfUnclaimed(profile.getId(), userId, now)) {
            throw new AlreadyClaimedException("다른 계정이 먼저 사용했습니다");
        }
        return resultOf(family, profile);
    }

    /**
     * 가족 초대코드로 들어온다. 판정은 도메인({@link Family#join})이 하고, 초대를 쓴 표시는 조건부 UPDATE 한 문장이다. 같은 코드를
     * 함께 쓴 두 요청 가운데 늦은 쪽은 0행이라 409 ALREADY_CLAIMED 다. 프로필 저장이 실패하면 쓴 표시도 함께 되돌아간다.
     */
    private ClaimResult join(UUID userId, FamilyCode target, @Nullable NewMember member) {
        Family family = target.family();
        FamilyInvite invite = target.invite();
        Instant now = clock.now();
        Profile profile =
                family.join(invite, userId, !families.profilesOfUser(userId).isEmpty(), member, now, clock.today());
        if (!familyInvites.markClaimedIfUnclaimed(invite.code().code(), userId, now)) {
            throw new AlreadyClaimedException("다른 계정이 먼저 사용했습니다");
        }
        families.save(family);
        return resultOf(family, profile);
    }

    /** 보호자는 참여 방식을 고르러(SUPPORT_MODE), 아이는 홈으로 간다. */
    private static ClaimResult resultOf(Family family, Profile profile) {
        NextStep nextStep = profile.getRole() == ProfileRole.PARENT ? NextStep.SUPPORT_MODE : NextStep.HOME;
        return new ClaimResult(profile.getId(), family.getId(), profile.getRole(), nextStep);
    }

    /**
     * 막혔으면 찾아보지 않는다. 가족 초대코드를 먼저 보고, 없으면 자리 초대코드를 본다. 새 코드는 두 표 어디에도 없는 것만 뽑으므로
     * ({@link InviteCodes}) 보통 한쪽에만 있다. 형식이 틀렸거나 둘 다 없으면 셈에 더하고 404 다.
     */
    private Target find(UUID userId, String rawCode) {
        attempts.check(userId);
        String code = ClaimCode.normalize(rawCode);
        if (ClaimCode.isWellFormed(code)) {
            FamilyInvite invite = familyInvites.findByCode(code);
            Family invited = invite == null ? null : families.findById(invite.familyId());
            if (invite != null && invited != null) return new FamilyCode(invited, invite);
            Family family = families.findByClaimCode(code);
            Profile profile = family == null ? null : seatOf(family, code);
            if (family != null && profile != null) return new Seat(family, profile);
        }
        attempts.recordFailure(userId);
        throw new ClaimCodeNotFoundException();
    }

    private static @Nullable Profile seatOf(Family family, String code) {
        return family.getProfiles().stream()
                .filter(it ->
                        it.getClaimCode() != null && it.getClaimCode().code().equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 보낸 보호자는 같은 가족 안의 프로필이다. 발급자를 남기기 전에 만든 코드면 null. */
    private static @Nullable String issuerName(Family family, Profile seat) {
        UUID issuedBy = seat.getClaimCodeIssuedBy();
        if (issuedBy == null) return null;
        Profile issuer = family.profileOrNull(issuedBy);
        return issuer == null ? null : issuer.getDisplayName();
    }

    private String shareUrlOf(ClaimCode code) {
        return trimTrailingSlashes(props.frontendBaseUrl()) + "/claim?code=" + code.code();
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    /** 코드가 가리키는 것. */
    private sealed interface Target permits Seat, FamilyCode {}

    /** 자리 초대코드: 보호자가 정보를 넣어 만든 프로필 자리. */
    private record Seat(Family family, Profile profile) implements Target {}

    /** 가족 초대코드: 그 가족과 역할만 정해진 초대. */
    private record FamilyCode(Family family, FamilyInvite invite) implements Target {}
}
