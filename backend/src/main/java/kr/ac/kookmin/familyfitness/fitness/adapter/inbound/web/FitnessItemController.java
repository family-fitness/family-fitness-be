package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import java.time.LocalDate;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.fitness.application.FitnessTestService;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/fitness/items — 로그인. 연령대 측정 항목표.
 * <ul>
 *   <li>{@code profileId}(+ {@code testedOn}) 를 주면 서버가 그 프로필의 측정일 기준 연령대를 정한다. 등록 검사와 같은 셈이다.
 *       testedOn 이 없으면 오늘(KST). 권한 · 날짜 규칙은 등록과 같다(보호자만 · 미래 400 · 만 4세 미만 422). 이때 ageGroup 은 보지 않는다.
 *   <li>없으면 지금처럼 {@code ageGroup} 으로 준다. testedOn 만 오면 연령대를 정할 수 없어 400.
 * </ul>
 * `sex` 는 검증만 하고 항목을 바꾸지 않는다(양쪽 공통).
 */
@RestController
@RequestMapping("/api/v1/fitness")
public class FitnessItemController {
    private final FitnessTestService service;

    public FitnessItemController(FitnessTestService service) {
        this.service = service;
    }

    @GetMapping("/items")
    public FitnessItemsResponse items(
            CurrentUser user,
            @RequestParam(required = false) @Nullable String ageGroup,
            @RequestParam(required = false) @Nullable UUID profileId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate testedOn,
            @RequestParam(required = false) @Nullable String sex) {
        if (sex != null) Sex.valueOf(sex);
        if (profileId != null) return FitnessItemsResponse.of(service.ageGroupOn(user.userId(), profileId, testedOn));
        if (testedOn != null) throw new IllegalArgumentException("testedOn 은 profileId 와 같이 보내야 합니다");
        if (ageGroup == null) throw new IllegalArgumentException("ageGroup 이나 profileId 가 있어야 합니다");
        return FitnessItemsResponse.of(AgeGroup.fromLabel(ageGroup));
    }
}
