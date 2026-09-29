package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.application.port.ExerciseFavoriteRepository;
import kr.ac.kookmin.familyfitness.coaching.domain.ClipNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.ProfileRequiredException;
import kr.ac.kookmin.familyfitness.identity.api.FamilyAccess;
import kr.ac.kookmin.familyfitness.identity.api.ProfileQuery;
import kr.ac.kookmin.familyfitness.identity.api.ProfileSummary;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운동 구간(클립) 목록과 구간 찜. 운동 찾기 · 직접 짜기 · 홈 영상 줄이 쓴다.
 * 목록 규칙은 FE 목(src/mocks/clips.ts)을 따르고, 목에 없는 연령대 거르기만 더한다(결정 28).
 */
@Service
public class ExerciseService {
    /** 한 번에 보내는 수. FE 목의 PAGE 와 같다. total 은 자르기 전 수다. */
    public static final int PAGE = 40;

    /**
     * 목록 차례: 영상 id → 시작 초. FE 목 카탈로그(clips.json)와 같은 차례다. 영상 id 에 대소문자 · '-' · '_' 가 섞여 있어 DB 콜레이션에
     * 맡기지 않고 자바 문자열 비교(UTF-16 코드 단위, JS 와 같다)로 정한다.
     */
    static final Comparator<ExerciseClip> CATALOG_ORDER =
            Comparator.comparing(ExerciseClip::videoId).thenComparingInt(ExerciseClip::startSec);

    private final ExerciseClipRepository clips;
    private final ExerciseFavoriteRepository favorites;
    private final FamilyAccess familyAccess;
    private final ProfileQuery profiles;
    private final AppTime time;

    public ExerciseService(
            ExerciseClipRepository clips,
            ExerciseFavoriteRepository favorites,
            FamilyAccess familyAccess,
            ProfileQuery profiles,
            AppTime time) {
        this.clips = clips;
        this.favorites = favorites;
        this.familyAccess = familyAccess;
        this.profiles = profiles;
        this.time = time;
    }

    /**
     * 거르는 차례: 켜진 운동 구간 → 보는 프로필의 연령대에 맞는 것({@link ExerciseClip#suits}, 어르신은 성인 구간도) → 요인 · 단계 ·
     * 조용함 · 검색어 → (FAVORITES 면) 찜 → 같은 제목은 하나만(보는 연령대와 같은 구간을 먼저, 그다음 목록 차례로 처음 것) → 목록 차례로
     * 줄 세워 앞 {@link #PAGE} 개. 연령대를 먼저 걸어야 같은 제목의 다른 연령대 구간이 대표로 남지 않는다.
     */
    @Transactional(readOnly = true)
    public ExerciseListView list(UUID userId, ExerciseListQuery query) {
        UUID profileId = query.profileId();
        if (query.list() == ExerciseListType.FAVORITES && profileId == null) throw new ProfileRequiredException();
        AgeGroup ageGroup = viewerAgeGroup(userId, profileId);
        if (ageGroup == null) return ExerciseListView.EMPTY;
        Set<String> favorited = profileId == null ? Set.of() : favorites.clipIdsOf(profileId);

        List<ExerciseClip> hits = distinctByTitle(clips.findAllActive().stream()
                        .filter(ExerciseClip::isExercise)
                        .filter(it -> it.suits(ageGroup))
                        .filter(query::matches)
                        .filter(it -> query.list() != ExerciseListType.FAVORITES || favorited.contains(it.clipId()))
                        .sorted(Comparator.comparing((ExerciseClip it) -> it.ageGroup() != ageGroup)
                                .thenComparing(CATALOG_ORDER))
                        .toList())
                .stream()
                .sorted(CATALOG_ORDER)
                .toList();
        List<ExerciseView> page = hits.stream()
                .limit(PAGE)
                .map(it -> ExerciseView.of(it, favorited.contains(it.clipId())))
                .toList();
        return new ExerciseListView(page, hits.size());
    }

    /**
     * 프로필 하나의 구간 찜을 켜거나 끈다. 같은 값을 다시 보내도 결과가 같다(멱등). 같은 가족이면 보호자가 아이 프로필의 찜을 바꿀 수 있다.
     * 목록에 나오지 않는 구간(없음 · 꺼짐 · 운동 아님)은 404 CLIP_NOT_FOUND.
     */
    @Transactional
    public ExerciseFavoriteView favorite(UUID userId, String exerciseId, ExerciseFavoriteCommand command) {
        UUID profileId = command.profileId();
        if (profileId == null) throw new ProfileRequiredException();
        ExerciseClip clip = clips.findById(exerciseId);
        if (clip == null || !clip.active() || !clip.isExercise()) throw new ClipNotFoundException(exerciseId);
        familyAccess.requireSameFamilyAsProfile(userId, profileId);
        if (command.favorited()) favorites.add(profileId, clip.clipId(), time.now());
        else favorites.remove(profileId, clip.clipId());
        return new ExerciseFavoriteView(clip.clipId(), command.favorited());
    }

    /**
     * 보는 프로필의 연령대. profileId 가 있으면 같은 가족인지 확인하고 그 프로필의 연령대, 없으면 호출한 계정의 자기 프로필(가장 먼저 만든 것) 연령대.
     * 계정에 프로필이 없으면 null — 연령대를 모르니 아무 구간도 내보내지 않는다.
     */
    private @Nullable AgeGroup viewerAgeGroup(UUID userId, @Nullable UUID profileId) {
        if (profileId != null) {
            return familyAccess.requireSameFamilyAsProfile(userId, profileId).ageGroup();
        }
        return profiles.summariesOfUser(userId).stream()
                .findFirst()
                .map(ProfileSummary::ageGroup)
                .orElse(null);
    }

    /** 같은 제목이 여러 영상에 되풀이된다. 들어온 차례에서 처음 나온 구간 하나만 남긴다(FE 목과 같다). */
    private static List<ExerciseClip> distinctByTitle(List<ExerciseClip> ordered) {
        Map<String, ExerciseClip> firstByTitle = new LinkedHashMap<>();
        for (ExerciseClip clip : ordered) firstByTitle.putIfAbsent(clip.title(), clip);
        return List.copyOf(firstByTitle.values());
    }
}
