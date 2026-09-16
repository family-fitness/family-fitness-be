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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 측정 회차 등록·최신 조회 — 로그인(같은 가족). actor 는 토큰의 계정, 대상은 경로의 profileId. */
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

    @GetMapping("/latest")
    public LatestFitnessResponse latest(CurrentUser user, @PathVariable UUID profileId) {
        return LatestFitnessResponse.of(service.latest(user.userId(), profileId));
    }
}
