
package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web

import jakarta.validation.Valid
import kr.ac.kookmin.familyfitness.fitness.application.FitnessTestService
import kr.ac.kookmin.familyfitness.fitness.application.PredictCommand
import kr.ac.kookmin.familyfitness.fitness.application.PredictionService
import kr.ac.kookmin.familyfitness.fitness.application.RegisterFitnessTestCommand
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup
import kr.ac.kookmin.familyfitness.shared.domain.Sex
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** GET /api/v1/fitness/items?ageGroup=&sex= — 로그인. `sex` 는 검증만 하고 항목을 바꾸지 않는다(양쪽 공통). */
@RestController
@RequestMapping("/api/v1/fitness")
class FitnessItemController {
    @GetMapping("/items")
    fun items(
        @RequestParam ageGroup: String,
        @RequestParam(required = false) sex: String?,
    ): FitnessItemsResponse {
        sex?.let { Sex.valueOf(it) }
        return FitnessItemsResponse.of(AgeGroup.fromLabel(ageGroup))
    }
}

/** 측정 회차 등록·최신 조회 — 로그인(같은 가족). actor 는 토큰의 계정, 대상은 경로의 profileId. */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/fitness-tests")
class FitnessTestController(
    private val service: FitnessTestService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        user: CurrentUser,
        @PathVariable profileId: UUID,
        @Valid @RequestBody request: RegisterFitnessTestRequest,
    ): FitnessTestResponse {
        val command =
            RegisterFitnessTestCommand(
                testedOn = request.testedOn!!,
                source = request.source!!,
                heightCm = request.heightCm,
                weightKg = request.weightKg,
                measurements = request.measurements(),
            )
        return FitnessTestResponse.of(service.register(user.userId, profileId, command))
    }

    @GetMapping("/latest")
    fun latest(
        user: CurrentUser,
        @PathVariable profileId: UUID,
    ): LatestFitnessResponse = LatestFitnessResponse.of(service.latest(user.userId, profileId))
}

/** POST /api/v1/profiles/{profileId}/predictions — 로그인(같은 가족). */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/predictions")
class PredictionController(
    private val service: PredictionService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun predict(
        user: CurrentUser,
        @PathVariable profileId: UUID,
        @Valid @RequestBody(required = false) request: PredictRequest?,
    ): PredictionResponse {
        val command =
            PredictCommand(
                fitnessTestId = request?.fitnessTestId,
                horizonYears = request?.horizonYears ?: PredictCommand.DEFAULT_HORIZON_YEARS,
                itemCode = request?.itemCode ?: PredictCommand.DEFAULT_ITEM_CODE,
            )
        return PredictionResponse.of(service.predict(user.userId, profileId, command))
    }
}
