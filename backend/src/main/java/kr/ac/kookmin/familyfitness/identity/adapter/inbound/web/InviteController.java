package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import kr.ac.kookmin.familyfitness.identity.application.InvitePreview;
import kr.ac.kookmin.familyfitness.identity.application.InviteService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 초대코드 미리 보기. 로그인한 계정만 부른다(공개 경로가 아니다) — 코드만으로 아이 이름 · 가족 이름이 보이므로
 * 계정마다 없는 코드를 넣은 횟수를 세어 막는다. 코드 사용({@code POST /profiles/claim})과 셈을 같이 쓴다.
 */
@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {
    private final InviteService invites;

    public InviteController(InviteService invites) {
        this.invites = invites;
    }

    /** 없음 404 CODE_NOT_FOUND · 이미 사용 409 ALREADY_CLAIMED · 만료 410 CODE_EXPIRED · 너무 많이 틀림 429 TOO_MANY. */
    @GetMapping("/{claimCode}")
    public InvitePreviewResponse preview(CurrentUser user, @PathVariable String claimCode) {
        InvitePreview preview = invites.preview(user.userId(), claimCode);
        return new InvitePreviewResponse(
                preview.familyName(),
                preview.profileName(),
                preview.role(),
                preview.ageGroup(),
                preview.invitedByName(),
                preview.expiresAt());
    }
}
