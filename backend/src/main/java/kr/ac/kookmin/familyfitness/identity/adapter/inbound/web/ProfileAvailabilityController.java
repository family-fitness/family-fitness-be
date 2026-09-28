package kr.ac.kookmin.familyfitness.identity.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.identity.application.AvailabilityService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운동할 수 있는 시간(FE 요청서 3장). 보기는 같은 가족 누구나, 바꾸기는 보호자만. 둘 다 그 사람의 한 주 모양으로 답한다.
 * 운동을 막는 데 쓰지 않는다 — AI 편성의 「몇 분」 기본값과 「이번 주 며칠」 목표의 출처다.
 */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/availability")
public class ProfileAvailabilityController {
    private final AvailabilityService service;

    public ProfileAvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping
    public AvailabilityResponse get(CurrentUser user, @PathVariable UUID profileId) {
        return AvailabilityResponse.of(profileId, service.get(user.userId(), profileId));
    }

    @PutMapping
    public AvailabilityResponse replace(
            CurrentUser user, @PathVariable UUID profileId, @Valid @RequestBody AvailabilityRequest request) {
        return AvailabilityResponse.of(profileId, service.replace(user.userId(), profileId, request.toRaw()));
    }
}
