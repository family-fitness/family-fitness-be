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
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
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
 * ageGroup 은 ALL(모든 나이) 또는 나이대 이름(유아기 등, 영문 상수도 받는다)이고, 없으면 보는 프로필의 나이대다. cursor 는 앞 응답의 nextCursor,
 * size 는 1~100(기본 40)이다. 모르는 값은 400 이다.
 *
 * <p>{@code /clips} 는 전환기 별칭이다 — 지금 FE(fe:src/lib/api/queries.ts useClips · useToggleClipFavorite)가 부르는 이름이라
 * 같은 핸들러로 받는다. 문서에는 deprecated 로 싣고(OpenApiConfig.TRANSITIONAL_ALIASES), FE 가 {@code /exercises} 로 옮기면
 * 걷는다.
 */
@RestController
@RequestMapping({"/api/v1/exercises", "/api/v1/clips"})
public class ExerciseController {
    /** ageGroup 에 이 값을 주면 나이대로 거르지 않는다. */
    static final String ALL_AGES = "ALL";

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
            @RequestParam(required = false) @Nullable UUID profileId,
            @RequestParam(required = false) @Nullable String ageGroup,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "" + ExerciseService.PAGE) int size) {
        String factorLabel = blankToNull(factor);
        String age = blankToNull(ageGroup);
        boolean allAges = ALL_AGES.equals(age);
        return service.list(
                user.userId(),
                new ExerciseListQuery(
                        factorLabel == null ? null : FitnessFactor.fromLabel(factorLabel),
                        phase,
                        quiet,
                        blankToNull(q),
                        list,
                        profileId,
                        age == null || allAges ? null : AgeGroup.fromLabel(age),
                        allAges,
                        blankToNull(cursor),
                        size));
    }

    @PostMapping("/{exerciseId}/favorite")
    public ExerciseFavoriteView favorite(
            CurrentUser user, @PathVariable String exerciseId, @Valid @RequestBody ExerciseFavoriteRequest body) {
        return service.favorite(
                user.userId(),
                exerciseId,
                new ExerciseFavoriteCommand(body.profileId(), Objects.requireNonNull(body.favorited())));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
