package kr.ac.kookmin.familyfitness.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.AlreadyClaimedException;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCodeNotFoundException;
import kr.ac.kookmin.familyfitness.identity.domain.Family;
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
    private final InviteCodes codes;
    private final AppProperties props;
    private final IdentityClock clock;
    private final ClaimAttemptLimiter attempts;

    public InviteService(
            FamilyRepository families,
            InviteCodes codes,
            AppProperties props,
            IdentityClock clock,
            ClaimAttemptLimiter attempts) {
        this.families = families;
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

    /** 판정은 사용과 같되 구성원 검사는 하지 않는다: 시도 초과 429 → 없음 404 → 이미 사용 409 → 만료 410. */
    @Transactional(readOnly = true)
    public InvitePreview preview(UUID userId, String rawCode) {
        Seat seat = findSeat(userId, rawCode);
        Profile profile = seat.family().claimableSeat(seat.profile().getId(), clock.now());
        ClaimCode code = Objects.requireNonNull(profile.getClaimCode());
        return new InvitePreview(
                seat.family().getName(),
                profile.getDisplayName(),
                profile.getRole(),
                profile.ageGroup(clock.today()),
                issuerName(seat.family(), profile),
                code.expiresAt());
    }

    /**
     * 판정 순서: 시도 초과 429 → 없음 404 → 이미 사용 409 → 만료 410 → 이 가족 구성원 409 → 다른 가족 409 ALREADY_IN_FAMILY.
     * 사전 검사를 함께 지나친 동시 요청은 profiles.user_id 유니크 인덱스가 막고 저장소가 ALREADY_IN_FAMILY 로 바꾼다.
     */
    public ClaimResult claim(UUID userId, String rawCode) {
        Seat seat = findSeat(userId, rawCode);
        Family family = seat.family();
        Profile profile = seat.profile();
        Instant now = clock.now();
        family.prepareClaim(
                profile.getId(), userId, now, !families.profilesOfUser(userId).isEmpty());
        if (!families.attachUserIfUnclaimed(profile.getId(), userId, now)) {
            throw new AlreadyClaimedException("다른 계정이 먼저 사용했습니다");
        }
        NextStep nextStep = profile.getRole() == ProfileRole.PARENT ? NextStep.SUPPORT_MODE : NextStep.HOME;
        return new ClaimResult(profile.getId(), family.getId(), profile.getRole(), nextStep);
    }

    /** 막혔으면 찾아보지 않는다. 형식이 틀렸거나 없는 코드면 셈에 더하고 404 다. */
    private Seat findSeat(UUID userId, String rawCode) {
        attempts.check(userId);
        String code = ClaimCode.normalize(rawCode);
        Family family = ClaimCode.isWellFormed(code) ? families.findByClaimCode(code) : null;
        Profile profile = family == null
                ? null
                : family.getProfiles().stream()
                        .filter(it -> it.getClaimCode() != null
                                && it.getClaimCode().code().equals(code))
                        .findFirst()
                        .orElse(null);
        if (family == null || profile == null) {
            attempts.recordFailure(userId);
            throw new ClaimCodeNotFoundException();
        }
        return new Seat(family, profile);
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

    private record Seat(Family family, Profile profile) {}
}
