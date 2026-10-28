package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessAgeService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 성인 체력 나이. 보호자만 본다 — 계약 2장 「GET /profiles/{profileId}/fitness-age」. */
@RestController
public class FitnessAgeController {
    private final FitnessAgeService service;

    public FitnessAgeController(FitnessAgeService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/profiles/{profileId}/fitness-age")
    public FitnessAgeResponse fitnessAge(CurrentUser user, @PathVariable UUID profileId) {
        return FitnessAgeResponse.of(service.estimate(user.userId(), profileId));
    }
}
