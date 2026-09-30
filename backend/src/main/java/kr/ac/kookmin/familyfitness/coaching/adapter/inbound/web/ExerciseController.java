package kr.ac.kookmin.familyfitness.coaching.adapter.inbound.web;

import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.ExerciseFavoriteCommand;
import kr.ac.kookmin.familyfitness.coaching.application.ExerciseFavoriteView;
import kr.ac.kookmin.familyfitness.coaching.application.ExerciseListQuery;
import kr.ac.kookmin.familyfitness.coaching.application.ExerciseListType;
import kr.ac.kookmin.familyfitness.coaching.application.ExerciseListView;
import kr.ac.kookmin.familyfitness.coaching.application.ExerciseService;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import kr.ac.kookmin.familyfitness.shared.security.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운동 구간(영상 속 한 동작) 목록과 구간 찜. 운동 찾기 · 직접 짜기 · 홈 영상 줄이 쓴다.
 * factor 는 한글 요인 이름(유연성 등, 영문 상수도 받는다), phase 는 WARMUP · MAIN · COOLDOWN, list 는 ALL · FAVORITES.
 * 모르는 값은 400 이다.
 */
@RestController
@RequestMapping("/api/v1/exercises")
public class ExerciseController {
    private final ExerciseService service;

    public ExerciseController(ExerciseService service) {
        this.service = service;
    }

    @GetMapping
    public ExerciseListView list(
            CurrentUser user,
            @RequestParam(required = false) @Nullable String factor,
            @RequestParam(required = false) @Nullable SessionPhase phase,
            @RequestParam(defaultValue = "false") boolean quiet,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "ALL") ExerciseListType list,
            @RequestParam(required = false) @Nullable UUID profileId) {
        String keyword = q == null ? null : q.strip();
        return service.list(
                user.userId(),
                new ExerciseListQuery(
                        factor == null || factor.isBlank() ? null : FitnessFactor.fromLabel(factor.strip()),
                        phase,
                        quiet,
                        keyword == null || keyword.isEmpty() ? null : keyword,
                        list,
                        profileId));
    }

    @PostMapping("/{exerciseId}/favorite")
    public ExerciseFavoriteView favorite(
            CurrentUser user, @PathVariable String exerciseId, @Valid @RequestBody ExerciseFavoriteRequest body) {
        return service.favorite(
                user.userId(),
                exerciseId,
                new ExerciseFavoriteCommand(body.profileId(), Objects.requireNonNull(body.favorited())));
    }
}
