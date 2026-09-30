package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessTestService;
import kr.ac.kookmin.familyfitness.fitness.application.RegisterFitnessTestCommand;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 측정 회차 등록·이력·최신 조회. actor 는 토큰의 계정, 대상은 경로의 profileId.
 * 등록은 그 가족의 보호자만(403 NOT_A_PARENT), 조회는 같은 가족이면 되고 자녀 계정에는 부모만 볼 값을 비운다.
 */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/fitness-tests")
public class FitnessTestController {
    private final FitnessTestService service;

    public FitnessTestController(FitnessTestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FitnessTestResponse register(
            CurrentUser user, @PathVariable UUID profileId, @Valid @RequestBody RegisterFitnessTestRequest request) {
        RegisterFitnessTestCommand command = new RegisterFitnessTestCommand(
                Objects.requireNonNull(request.testedOn()),
                Objects.requireNonNull(request.source()),
                request.heightCm(),
                request.weightKg(),
                request.measurements());
        return FitnessTestResponse.of(service.register(user.userId(), profileId, command));
    }

    /** 측정 이력. 최근 회차가 먼저. size 기본 20 · 1~100(영상 목록과 같다), 밖이면 400 BAD_REQUEST. */
    @GetMapping
    public FitnessTestHistoryResponse history(
            CurrentUser user, @PathVariable UUID profileId, @RequestParam(defaultValue = "20") int size) {
        return FitnessTestHistoryResponse.of(service.history(user.userId(), profileId, size));
    }

    @GetMapping("/latest")
    public LatestFitnessResponse latest(CurrentUser user, @PathVariable UUID profileId) {
        return LatestFitnessResponse.of(service.latest(user.userId(), profileId));
    }
}
