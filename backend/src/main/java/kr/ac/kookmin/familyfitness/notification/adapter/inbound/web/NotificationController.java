package kr.ac.kookmin.familyfitness.notification.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.notification.application.NotificationListView;
import kr.ac.kookmin.familyfitness.notification.application.NotificationService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림함 — 종 · 알림 화면. 자기 프로필, 또는 보호자가 계정 없는 아이 프로필(아이 모드)만 읽고 읽음 처리한다.
 * profileId 가 없으면 400, 다른 가족 403 NOT_SAME_FAMILY, 대신할 수 없는 프로필 403 FORBIDDEN, 없는 프로필 404.
 */
@RestController
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** 최신 30건(만든 시각이 늦은 것부터)과 그 가운데 안 읽은 수. */
    @GetMapping("/api/v1/notifications")
    public NotificationListView list(CurrentUser user, @RequestParam UUID profileId) {
        return service.list(user.userId(), profileId);
    }

    /** 읽음 처리. 본문 없이 204. */
    @PostMapping("/api/v1/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(CurrentUser user, @Valid @RequestBody MarkReadRequest request) {
        service.markRead(user.userId(), Objects.requireNonNull(request.profileId()), request.upTo());
    }
}
