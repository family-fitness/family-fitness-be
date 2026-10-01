package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.FamilyInviteService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가족 초대(초대 먼저). 그 가족 보호자만 부른다. 코드 미리 보기는 {@code GET /invites/{code}}, 코드로 들어오기는
 * {@code POST /profiles/claim} 이다(자리 초대코드와 같은 주소).
 */
@RestController
@RequestMapping("/api/v1/families/{familyId}/invites")
public class FamilyInviteController {
    private final FamilyInviteService invites;

    public FamilyInviteController(FamilyInviteService invites) {
        this.invites = invites;
    }

    /**
     * 판정 차례: 본문 400 BAD_REQUEST, 가족 없음 404 FAMILY_NOT_FOUND, 구성원 아님 403 NOT_SAME_FAMILY, 아이 계정 403
     * NOT_A_PARENT, CHILD 인데 동의 없음 422 CONSENT_REQUIRED.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyInviteResponse create(
            CurrentUser user, @PathVariable UUID familyId, @Valid @RequestBody CreateFamilyInviteRequest request) {
        GuardianConsentRequest consent = request.guardianConsent();
        return FamilyInviteResponse.of(invites.create(
                user.userId(),
                familyId,
                Objects.requireNonNull(request.role()),
                consent == null ? null : consent.toDomain()));
    }
}
