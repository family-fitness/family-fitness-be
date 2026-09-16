package kr.ac.kookmin.familyfitness.identity.application;

import java.security.SecureRandom;
import java.time.Instant;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 초대 코드 발급·사용. 사용은 조건부 UPDATE 한 문장으로 동시 요청을 가른다. */
@Service
@Transactional
public class InviteService {
    private static final int MAX_GENERATE_ATTEMPTS = 10;

    private final FamilyRepository families;
    private final AppProperties props;
    private final IdentityClock clock;
    private final SecureRandom random = new SecureRandom();

    public InviteService(FamilyRepository families, AppProperties props, IdentityClock clock) {
        this.families = families;
        this.props = props;
        this.clock = clock;
    }

    public Invitation issueInvite(UUID userId, UUID profileId) {
        Family family = families.findByProfileId(profileId);
        if (family == null) throw new ProfileNotFoundException(profileId);
        ClaimCode code = freshCode();
        family.issueInvite(userId, profileId, code);
        families.save(family);
        return new Invitation(code, shareUrlOf(code));
    }

    public ClaimResult claim(UUID userId, String rawCode) {
        String code = ClaimCode.normalize(rawCode);
        Family family = families.findByClaimCode(code);
        if (family == null) throw new ClaimCodeNotFoundException();
        Profile profile = family.getProfiles().stream()
                .filter(it ->
                        it.getClaimCode() != null && it.getClaimCode().code().equals(code))
                .findFirst()
                .orElseThrow(ClaimCodeNotFoundException::new);
        Instant now = clock.now();
        family.prepareClaim(profile.getId(), userId, now);
        if (!families.attachUserIfUnclaimed(profile.getId(), userId, now)) {
            throw new AlreadyClaimedException("다른 계정이 먼저 사용했습니다");
        }
        NextStep nextStep = profile.getRole() == ProfileRole.PARENT ? NextStep.SUPPORT_MODE : NextStep.HOME;
        return new ClaimResult(profile.getId(), family.getId(), profile.getRole(), nextStep);
    }

    private ClaimCode freshCode() {
        Instant now = clock.now();
        for (int i = 0; i < MAX_GENERATE_ATTEMPTS; i++) {
            ClaimCode code = ClaimCode.generate(now, random);
            if (!families.isClaimCodeTaken(code.code())) return code;
        }
        throw new IllegalStateException("초대 코드를 만들지 못했습니다");
    }

    private String shareUrlOf(ClaimCode code) {
        return trimTrailingSlashes(props.frontendBaseUrl()) + "/claim?code=" + code.code();
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }
}
