package kr.ac.kookmin.familyfitness.identity.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.function.Function;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyInviteRepository;
import kr.ac.kookmin.familyfitness.identity.application.port.FamilyRepository;
import kr.ac.kookmin.familyfitness.identity.domain.ClaimCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 새 초대코드를 뽑는다. 자리 초대코드(profiles.claim_code)와 가족 초대코드(family_invites.code)는 같은 입력칸으로 들어오므로 두 표를
 * 함께 보고 어느 쪽에도 없는 코드만 준다. 동시에 두 요청이 같은 코드를 뽑으면 넣을 때 그 표의 유니크가 막는다(409 CONFLICT).
 */
@Component
public class InviteCodes {
    private static final int MAX_GENERATE_ATTEMPTS = 10;

    private final FamilyRepository families;
    private final FamilyInviteRepository familyInvites;
    private final Function<Instant, ClaimCode> generator;

    @Autowired
    public InviteCodes(FamilyRepository families, FamilyInviteRepository familyInvites) {
        this(families, familyInvites, new SecureRandom());
    }

    private InviteCodes(FamilyRepository families, FamilyInviteRepository familyInvites, SecureRandom random) {
        this(families, familyInvites, now -> ClaimCode.generate(now, random));
    }

    /** 시험이 뽑는 차례를 정할 때 쓴다. */
    InviteCodes(
            FamilyRepository families, FamilyInviteRepository familyInvites, Function<Instant, ClaimCode> generator) {
        this.families = families;
        this.familyInvites = familyInvites;
        this.generator = generator;
    }

    /** {@code now} 에서 7일 뒤 만료되는 새 코드. 열 번 뽑아도 모두 쓰이고 있으면 서버 오류다. */
    public ClaimCode fresh(Instant now) {
        for (int i = 0; i < MAX_GENERATE_ATTEMPTS; i++) {
            ClaimCode code = generator.apply(now);
            if (!families.isClaimCodeTaken(code.code()) && !familyInvites.isCodeTaken(code.code())) return code;
        }
        throw new IllegalStateException("초대 코드를 만들지 못했습니다");
    }
}
