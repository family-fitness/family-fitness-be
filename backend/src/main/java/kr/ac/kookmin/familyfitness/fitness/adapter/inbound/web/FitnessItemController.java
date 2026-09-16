package kr.ac.kookmin.familyfitness.fitness.adapter.inbound.web;

import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.Sex;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/fitness/items?ageGroup=&sex= — 로그인. `sex` 는 검증만 하고 항목을 바꾸지 않는다(양쪽 공통). */
@RestController
@RequestMapping("/api/v1/fitness")
public class FitnessItemController {
    @GetMapping("/items")
    public FitnessItemsResponse items(
            @RequestParam String ageGroup, @RequestParam(required = false) @Nullable String sex) {
        if (sex != null) Sex.valueOf(sex);
        return FitnessItemsResponse.of(AgeGroup.fromLabel(ageGroup));
    }
}
