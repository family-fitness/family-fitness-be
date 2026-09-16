package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessMapService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/families/{familyId}/fitness-map — 로그인(가족 구성원). 홈 화면 한 번의 조회(보드 F0 `/home` 가족 체력 지도). */
@RestController
@RequestMapping("/api/v1/families/{familyId}/fitness-map")
public class FitnessMapController {
    private final FitnessMapService service;

    public FitnessMapController(FitnessMapService service) {
        this.service = service;
    }

    @GetMapping
    public FitnessMapResponse fitnessMap(CurrentUser user, @PathVariable UUID familyId) {
        return FitnessMapResponse.of(service.of(user.userId(), familyId));
    }
}
