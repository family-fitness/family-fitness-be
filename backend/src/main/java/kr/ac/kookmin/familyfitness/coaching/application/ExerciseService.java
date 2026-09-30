package kr.ac.kookmin.familyfitness.coaching.application;

import java.util.ArrayList;
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
import kr.ac.kookmin.familyfitness.coaching.domain.InvalidInputException;
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
    /** size 를 주지 않았을 때 한 쪽에 싣는 수. FE 목의 PAGE 와 같다. total 은 자르기 전 수다. */
    public static final int PAGE = 40;

    /** 한 쪽에 실을 수 있는 가장 큰 수. 영상 목록(/videos)과 같다. */
    public static final int MAX_SIZE = 100;

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
     * 거르는 차례: 켜진 운동 구간 → 나이대({@link ExerciseClip#suits}, 어르신은 성인 구간도. ageGroup=ALL 이면 거르지 않는다) → 요인,
     * 단계, 조용함, 검색어 → (FAVORITES 면) 찜 → 보는 나이대 구간을 먼저, 그다음 목록 차례로 세움 → 같은 구간(clipId)은 하나만(처음 것)
     * → 유튜브 구간과 공단 영상을 번갈아({@link #alternateSources}) 세우고 cursor 뒤에서 size 개.
     *
     * <p>한 항목은 구간 하나다. 공단 영상은 한 편이 구간 하나이고, 유튜브는 한 편을 동작마다 자른 구간 하나하나다. 예전에는 같은 제목을 하나로
     * 묶어 실린 구간 1,103개 가운데 유소년이 157개만 받았다. 공단 영상 한 편은 AI 표가 나이대, 요인, 단계마다 한 줄씩 줘서 켜진 목록에 여러 번
     * 들어오므로 clipId 로 한 번만 남긴다. 보는 나이대 줄이 먼저 서므로 그 줄의 요인과 단계가 실린다.
     *
     * <p>cursor 는 앞 쪽 마지막 구간의 clipId 다. 목록을 다시 세운 뒤 그 구간 바로 뒤부터 준다. 그사이 그 구간이 목록에서 빠졌으면(찜을 풀었거나
     * 새 판에서 꺼짐) 어디서 이어야 할지 몰라 400 을 돌려준다. 첫 쪽부터 다시 받으면 된다.
     */
    @Transactional(readOnly = true)
    public ExerciseListView list(UUID userId, ExerciseListQuery query) {
        if (query.size() < 1 || query.size() > MAX_SIZE) {
            throw new InvalidInputException("size 는 1~" + MAX_SIZE + " 이어야 합니다");
        }
        UUID profileId = query.profileId();
        if (query.list() == ExerciseListType.FAVORITES && profileId == null) throw new ProfileRequiredException();
        AgeGroup viewer = viewerAgeGroup(userId, profileId);
        AgeGroup target = query.allAges() ? null : query.ageGroup() != null ? query.ageGroup() : viewer;
        if (!query.allAges() && target == null) return ExerciseListView.EMPTY;
        AgeGroup first = query.allAges() ? viewer : target;
        Set<String> favorited = profileId == null ? Set.of() : favorites.clipIdsOf(profileId);

        List<ExerciseClip> hits = alternateSources(distinctByClip(clips.findAllActive().stream()
                .filter(ExerciseClip::isExercise)
                .filter(it -> target == null || it.suits(target))
                .filter(query::matches)
                .filter(it -> query.list() != ExerciseListType.FAVORITES || favorited.contains(it.clipId()))
                .sorted(viewerFirst(first).thenComparing(CATALOG_ORDER))
                .toList()));
        int from = startAfter(hits, query.cursor());
        List<ExerciseClip> page = hits.subList(from, Math.min(hits.size(), from + query.size()));
        String nextCursor = from + page.size() < hits.size() ? page.getLast().clipId() : null;
        return new ExerciseListView(
                page.stream()
                        .map(it -> ExerciseView.of(it, favorited.contains(it.clipId())))
                        .toList(),
                hits.size(),
                nextCursor);
    }

    /** 보는 사람이 받는 구간을 먼저, 그 안에서 나이대가 같은 구간을 먼저 세운다(어르신은 어르신 구간, 그다음 성인 구간). 모르면 차례를 바꾸지 않는다. */
    private static Comparator<ExerciseClip> viewerFirst(@Nullable AgeGroup viewer) {
        if (viewer == null) return (a, b) -> 0;
        return Comparator.comparing((ExerciseClip it) -> !it.suits(viewer))
                .thenComparing(it -> it.ageGroup() != viewer);
    }

    /** cursor 로 받은 구간 바로 다음 자리. cursor 가 없으면 0, 목록에 없으면 400. */
    private static int startAfter(List<ExerciseClip> hits, @Nullable String cursor) {
        if (cursor == null) return 0;
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).clipId().equals(cursor)) return i + 1;
        }
        throw new InvalidInputException("cursor " + cursor + " 가 목록에 없습니다. 첫 쪽부터 다시 받아 주세요");
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

    /**
     * 유튜브 구간과 공단 영상을 하나씩 번갈아 세운다(유튜브 먼저). 출처 안에서는 들어온 차례(보는 연령대 먼저, 그다음 목록 차례)를 지킨다.
     * 한쪽이 먼저 떨어지면 남은 쪽을 그대로 잇는다. 영상 id 차례로만 세우면 공단 영상 id(0AUDLJ08S_…)가 유튜브 id 대부분보다 앞서, 첫 쪽
     * {@link #PAGE} 개가 모두 공단 영상이었다. 출처 안에서 영상 id 차례로 다시 세우면 성인(공통) 공단 영상 id 가 어르신
     * 영상보다 앞서, 어르신이 보는 첫 쪽에 어르신 영상이 하나도 없었다(V163 판 때 일이다. V164 부터 어르신 영상은 싣지 않는다).
     */
    static List<ExerciseClip> alternateSources(List<ExerciseClip> clips) {
        List<ExerciseClip> youtube =
                clips.stream().filter(it -> it.media().mediaUrl() == null).toList();
        List<ExerciseClip> kspo =
                clips.stream().filter(it -> it.media().mediaUrl() != null).toList();
        List<ExerciseClip> merged = new ArrayList<>(clips.size());
        for (int i = 0; i < Math.max(youtube.size(), kspo.size()); i++) {
            if (i < youtube.size()) merged.add(youtube.get(i));
            if (i < kspo.size()) merged.add(kspo.get(i));
        }
        return List.copyOf(merged);
    }

    /** 공단 영상 한 편은 나이대, 요인, 단계 줄마다 한 번씩 들어온다. 들어온 차례에서 처음 나온 줄 하나만 남긴다. */
    private static List<ExerciseClip> distinctByClip(List<ExerciseClip> ordered) {
        Map<String, ExerciseClip> firstById = new LinkedHashMap<>();
        for (ExerciseClip clip : ordered) firstById.putIfAbsent(clip.clipId(), clip);
        return List.copyOf(firstById.values());
    }
}
