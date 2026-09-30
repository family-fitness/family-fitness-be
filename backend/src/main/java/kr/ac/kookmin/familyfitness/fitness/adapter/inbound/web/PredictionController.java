package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.PredictCommand;
import kr.ac.kookmin.familyfitness.fitness.application.PredictionService;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/profiles/{profileId}/predictions — 내 프로필이나, 보호자가 대신하는 계정 없는 아이 프로필만.
 * 같은 가족이어도 계정이 붙은 다른 식구면 403 FORBIDDEN, 다른 가족이면 403 NOT_SAME_FAMILY.
 */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/predictions")
public class PredictionController {
    private final PredictionService service;

    public PredictionController(PredictionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PredictionResponse predict(
            CurrentUser user,
            @PathVariable UUID profileId,
            @Valid @RequestBody(required = false) @Nullable PredictRequest request) {
        PredictCommand command = new PredictCommand(
                request == null ? null : request.fitnessTestId(),
                request == null || request.horizonYears() == null
                        ? PredictCommand.DEFAULT_HORIZON_YEARS
                        : request.horizonYears(),
                request == null || request.itemCode() == null ? PredictCommand.DEFAULT_ITEM_CODE : request.itemCode());
        return PredictionResponse.of(service.predict(user.userId(), profileId, command));
    }
}
