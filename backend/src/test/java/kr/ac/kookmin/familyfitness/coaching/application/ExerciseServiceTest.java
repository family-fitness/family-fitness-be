package kr.ac.kookmin.familyfitness.coaching.application;

import static kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseClipRepository.clip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import kr.ac.kookmin.familyfitness.coaching.domain.ClipNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseClip;
import kr.ac.kookmin.familyfitness.coaching.domain.ProfileRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.SessionPhase;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseClipRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseFavoriteRepository;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.identity.api.ProfileNotFoundException;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import kr.ac.kookmin.familyfitness.shared.domain.FitnessFactor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 가족: 엄마(성인) · 민준(11세, 유소년) · 응원만 하는 아빠. 다른 가족 하나. */
class ExerciseServiceTest {
    private final Family family = new Family();
    private final Family other = new Family();
    private final FakeIdentity identity = new FakeIdentity(family, other);
    private final InMemoryExerciseClipRepository clips = new InMemoryExerciseClipRepository();
    private final InMemoryExerciseFavoriteRepository favorites = new InMemoryExerciseFavoriteRepository();
    private final ExerciseService service = new ExerciseService(clips, favorites, identity, identity, Fixed.time());
    private final UUID childId = family.child.profileId();
    private final UUID parentId = family.parent.profileId();

    private void add(ExerciseClip... added) {
        for (ExerciseClip it : added) clips.clips.put(it.clipId(), it);
    }

    private static ExerciseClip youth(String videoId, int startSec, String title) {
        return clip(videoId, startSec, startSec + 30, title, SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.YOUTH);
    }

    /** 이름 · 조용함 · 운동 여부 · 켜짐만 바꾼 유소년 구간. */
    private static ExerciseClip youth(
            String videoId,
            int startSec,
            String nameOnVideo,
            @Nullable String exerciseName,
            boolean quiet,
            boolean isExercise,
            boolean active) {
        return new ExerciseClip(
                ExerciseClip.idOf(videoId, startSec),
                videoId,
                1,
                nameOnVideo,
                exerciseName,
                exerciseName != null ? exerciseName : nameOnVideo,
                FitnessFactor.STRENGTH,
                SessionPhase.MAIN,
                startSec,
                startSec + 30,
                true,
                quiet,
                false,
                isExercise,
                AgeGroup.YOUTH,
                "llm",
                active);
    }

    private ExerciseListView list(UUID userId, @Nullable UUID profileId) {
        return listBy(userId, new ExerciseListQuery(null, null, false, null, ExerciseListType.ALL, profileId));
    }

    private ExerciseListView listBy(UUID userId, ExerciseListQuery query) {
        return service.list(userId, query);
    }

    private static List<String> ids(ExerciseListView view) {
        return view.clips().stream().map(ExerciseView::clipId).toList();
    }

    @Test
    @DisplayName("보는 프로필과 같은 연령대 구간만 주고, 같은 제목은 연령대를 거른 뒤 처음 것 하나만 싣는다")
    void 보는_프로필과_같은_연령대_구간만_주고_같은_제목은_연령대를_거른_뒤_처음_것_하나만_싣는다() {
        add(
                clip("aaa", 10, 70, "엎드려 버티기", SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.ADULT),
                youth("bbb", 10, "엎드려 버티기"),
                youth("bbb", 50, "엎드려 버티기"),
                youth("ccc", 5, "버피"),
                clip("ddd", 0, 40, "거북이 스트레칭", SessionPhase.WARMUP, null, AgeGroup.TODDLER));

        ExerciseListView child = list(family.parentUser, childId);
        assertThat(ids(child)).containsExactly("bbb-10", "ccc-5");
        assertThat(child.total()).isEqualTo(2);

        assertThat(ids(list(family.parentUser, parentId))).containsExactly("aaa-10");
    }

    @Test
    @DisplayName("profileId 가 없으면 호출한 계정의 자기 프로필 연령대로 거르고, 프로필이 없는 계정에는 아무것도 주지 않는다")
    void profileId_가_없으면_호출한_계정의_자기_프로필_연령대로_거르고_프로필이_없는_계정에는_아무것도_주지_않는다() {
        add(
                clip("aaa", 10, 70, "팔 굽혀 펴기", SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.ADULT),
                youth("bbb", 10, "버피"));

        assertThat(ids(list(family.childUser, null))).containsExactly("bbb-10");
        assertThat(ids(list(family.parentUser, null))).containsExactly("aaa-10");
        ExerciseListView nobody = list(family.outsiderUser, null);
        assertThat(nobody.clips()).isEmpty();
        assertThat(nobody.total()).isZero();
    }

    @Test
    @DisplayName("다른 가족의 프로필이나 없는 프로필로는 목록을 볼 수 없다")
    void 다른_가족의_프로필이나_없는_프로필로는_목록을_볼_수_없다() {
        add(youth("bbb", 10, "버피"));

        assertThrows(NotSameFamilyException.class, () -> list(other.parentUser, childId));
        assertThrows(ProfileNotFoundException.class, () -> list(family.parentUser, UUID.randomUUID()));
    }

    @Test
    @DisplayName("요인 · 단계 · 조용함 · 검색어로 거르고, 운동이 아니거나 꺼진 구간은 늘 뺀다")
    void 요인_단계_조용함_검색어로_거르고_운동이_아니거나_꺼진_구간은_늘_뺀다() {
        add(
                clip("aaa", 0, 40, "고양이 자세", SessionPhase.WARMUP, FitnessFactor.FLEXIBILITY, AgeGroup.YOUTH),
                youth("aaa", 50, "스쿼트"),
                youth("aaa", 90, "점프 스쿼트", "앉았다 일어서면서 점프하기", false, true, true),
                youth("aaa", 130, "휴식", null, true, false, true),
                youth("aaa", 170, "와이드 스쿼트", null, true, true, false));

        assertThat(ids(listBy(family.childUser, query(FitnessFactor.FLEXIBILITY, null, false, null))))
                .containsExactly("aaa-0");
        assertThat(ids(listBy(family.childUser, query(null, SessionPhase.MAIN, false, null))))
                .containsExactly("aaa-50", "aaa-90");
        assertThat(ids(listBy(family.childUser, query(null, null, true, null)))).containsExactly("aaa-0", "aaa-50");
        // 검색어는 목처럼 제목만 본다. 제목이 처방 어휘로 바뀐 구간은 영상에 뜬 이름(점프 스쿼트)으로 찾히지 않는다
        assertThat(ids(listBy(family.childUser, query(null, null, false, "스쿼트"))))
                .containsExactly("aaa-50");
        assertThat(listBy(family.childUser, query(null, null, false, "점프 스쿼트")).total())
                .isZero();
        assertThat(ids(listBy(family.childUser, query(null, null, false, "점프하기"))))
                .containsExactly("aaa-90");
        assertThat(listBy(family.childUser, query(null, null, false, "휴식")).total())
                .isZero();
    }

    private static ExerciseListQuery query(
            @Nullable FitnessFactor factor, @Nullable SessionPhase phase, boolean quiet, @Nullable String q) {
        return new ExerciseListQuery(factor, phase, quiet, q, ExerciseListType.ALL, null);
    }

    @Test
    @DisplayName("목록 차례는 영상 id 의 문자열 차례, 그 안에서 시작 초의 숫자 차례다")
    void 목록_차례는_영상_id_의_문자열_차례_그_안에서_시작_초의_숫자_차례다() {
        add(
                youth("abc", 100, "여섯"),
                youth("_ab", 5, "넷"),
                youth("Abc", 5, "셋"),
                youth("abc", 20, "다섯"),
                youth("5Cr", 5, "둘"),
                youth("-EA", 5, "하나"));

        assertThat(ids(list(family.childUser, null)))
                .containsExactly("-EA-5", "5Cr-5", "Abc-5", "_ab-5", "abc-20", "abc-100");
    }

    @Test
    @DisplayName("앞 40개만 싣고 total 은 자르기 전 수다")
    void 앞_40개만_싣고_total_은_자르기_전_수다() {
        IntStream.range(0, 45).forEach(i -> add(youth("vid", i * 60, "동작 " + i)));

        ExerciseListView view = list(family.childUser, null);

        assertThat(view.clips()).hasSize(ExerciseService.PAGE);
        assertThat(view.total()).isEqualTo(45);
        assertThat(view.clips().getLast().clipId()).isEqualTo("vid-2340");
    }

    @Test
    @DisplayName("찜 목록은 profileId 가 있어야 하고, 그 프로필이 찜한 구간만 준다")
    void 찜_목록은_profileId_가_있어야_하고_그_프로필이_찜한_구간만_준다() {
        add(youth("bbb", 10, "버피"), youth("bbb", 50, "스쿼트"));
        ExerciseListQuery noProfile = new ExerciseListQuery(null, null, false, null, ExerciseListType.FAVORITES, null);

        assertThat(assertThrows(ProfileRequiredException.class, () -> listBy(family.childUser, noProfile))
                        .getCode())
                .isEqualTo("PROFILE_REQUIRED");

        // 보호자가 아이 프로필의 찜을 켠다
        service.favorite(family.parentUser, "bbb-50", new ExerciseFavoriteCommand(childId, true));

        ExerciseListView mine = listBy(
                family.childUser, new ExerciseListQuery(null, null, false, null, ExerciseListType.FAVORITES, childId));
        assertThat(ids(mine)).containsExactly("bbb-50");
        assertThat(mine.clips().getFirst().favorited()).isTrue();
        assertThat(list(family.childUser, childId).clips())
                .extracting(ExerciseView::favorited)
                .containsExactly(false, true);
        // 찜은 프로필마다 따로다 — 찜을 켠 보호자 자신의 찜 목록에는 없고, profileId 없이 보면 모두 false
        assertThat(listBy(
                                family.parentUser,
                                new ExerciseListQuery(null, null, false, null, ExerciseListType.FAVORITES, parentId))
                        .total())
                .isZero();
        assertThat(list(family.childUser, null).clips())
                .extracting(ExerciseView::favorited)
                .containsExactly(false, false);
    }

    @Test
    @DisplayName("찜은 같은 값을 다시 보내도 결과가 같다")
    void 찜은_같은_값을_다시_보내도_결과가_같다() {
        add(youth("bbb", 10, "버피"));

        ExerciseFavoriteView on =
                service.favorite(family.childUser, "bbb-10", new ExerciseFavoriteCommand(childId, true));
        service.favorite(family.childUser, "bbb-10", new ExerciseFavoriteCommand(childId, true));

        assertThat(on).isEqualTo(new ExerciseFavoriteView("bbb-10", true));
        assertThat(favorites.rows).hasSize(1);
        assertThat(favorites.rows.values()).containsExactly(Fixed.NOW);

        ExerciseFavoriteView off =
                service.favorite(family.childUser, "bbb-10", new ExerciseFavoriteCommand(childId, false));
        service.favorite(family.childUser, "bbb-10", new ExerciseFavoriteCommand(childId, false));

        assertThat(off).isEqualTo(new ExerciseFavoriteView("bbb-10", false));
        assertThat(favorites.rows).isEmpty();
    }

    @Test
    @DisplayName("목록에 나오지 않는 구간 · 누구의 찜인지 없는 요청 · 다른 가족은 찜을 바꾸지 못한다")
    void 목록에_나오지_않는_구간_누구의_찜인지_없는_요청_다른_가족은_찜을_바꾸지_못한다() {
        add(
                youth("bbb", 10, "버피"),
                youth("bbb", 50, "휴식", null, true, false, true),
                youth("bbb", 90, "스쿼트", null, true, true, false));

        for (String clipId : List.of("nope-0", "bbb-50", "bbb-90")) {
            assertThat(assertThrows(
                                    ClipNotFoundException.class,
                                    () -> service.favorite(
                                            family.childUser, clipId, new ExerciseFavoriteCommand(childId, true)))
                            .getCode())
                    .isEqualTo("CLIP_NOT_FOUND");
        }
        assertThrows(
                ProfileRequiredException.class,
                () -> service.favorite(family.childUser, "bbb-10", new ExerciseFavoriteCommand(null, true)));
        assertThrows(
                NotSameFamilyException.class,
                () -> service.favorite(other.parentUser, "bbb-10", new ExerciseFavoriteCommand(childId, true)));
        assertThat(favorites.rows).isEmpty();
    }

    @Test
    @DisplayName("어르신 프로필에는 같은 연령대 구간이 없어 빈 목록이다")
    void 어르신_프로필에는_같은_연령대_구간이_없어_빈_목록이다() {
        add(clip("aaa", 10, 70, "팔 굽혀 펴기", SessionPhase.MAIN, FitnessFactor.STRENGTH, AgeGroup.ADULT));
        UUID grandma = family.addChild("할머니", LocalDate.of(1950, 1, 1)).profileId();

        assertThat(list(family.parentUser, grandma).total()).isZero();
    }
}
