package kr.ac.kookmin.familyfitness.progress.adapter.inbound.web;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.progress.application.ProgressQueryService;
import kr.ac.kookmin.familyfitness.progress.application.ProgressView;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 레벨 · 경험치 · 업적 · 이어서 한 날 — 로그인(같은 가족). 아이 · 부모 프로필 모두 답한다. */
@RestController
public class ProgressController {
    private final ProgressQueryService service;

    public ProgressController(ProgressQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/profiles/{profileId}/progress")
    public ProgressView progress(CurrentUser user, @PathVariable UUID profileId) {
        return service.view(user.userId(), profileId);
    }
}
