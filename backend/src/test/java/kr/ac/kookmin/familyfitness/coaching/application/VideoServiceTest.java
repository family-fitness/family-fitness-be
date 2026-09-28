package kr.ac.kookmin.familyfitness.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.ac.kookmin.familyfitness.activity.api.ActivitySource;
import kr.ac.kookmin.familyfitness.coaching.domain.ExerciseVideo;
import kr.ac.kookmin.familyfitness.coaching.domain.NotParticipantException;
import kr.ac.kookmin.familyfitness.coaching.domain.ParticipantConsentRequiredException;
import kr.ac.kookmin.familyfitness.coaching.domain.TargetMetric;
import kr.ac.kookmin.familyfitness.coaching.domain.VerifiedBy;
import kr.ac.kookmin.familyfitness.coaching.domain.VideoNotFoundException;
import kr.ac.kookmin.familyfitness.coaching.support.FakeActivity;
import kr.ac.kookmin.familyfitness.coaching.support.FakeIdentity;
import kr.ac.kookmin.familyfitness.coaching.support.Family;
import kr.ac.kookmin.familyfitness.coaching.support.Fixed;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryExerciseVideoRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryMissionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemorySessionCompletionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.InMemoryVideoInteractionRepository;
import kr.ac.kookmin.familyfitness.coaching.support.Videos;
import kr.ac.kookmin.familyfitness.identity.api.NotSameFamilyException;
import kr.ac.kookmin.familyfitness.shared.domain.AgeGroup;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VideoServiceTest {
    private final Family family = new Family();
    private final Family other = new Family();
    private final FakeIdentity identity = new FakeIdentity(family, other);
    private final FakeActivity activity = new FakeActivity();
    private final InMemorySessionCompletionRepository completions = new InMemorySessionCompletionRepository();
    private final InMemoryMissionRepository missions = new InMemoryMissionRepository(completions);
    private final InMemoryVideoInteractionRepository interactions = new InMemoryVideoInteractionRepository();
    private final InMemoryExerciseVideoRepository videos = new InMemoryExerciseVideoRepository(catalog());
    private final MissionCompletionPolicy policy =
            new MissionCompletionPolicy(activity, interactions, missions, completions);
    private final VideoService service =
            new VideoService(videos, interactions, missions, identity, activity, policy, Fixed.time());
    private final UUID childId = family.child.profileId();

    private static List<ExerciseVideo> catalog() {
        List<ExerciseVideo> all = new ArrayList<>(Videos.seed());
        all.add(Videos.video("zzz_unlabeled", null, null, "근력", 600));
        return List.copyOf(all);
    }

    private VideoListQuery query(
            VideoListType list,
            @Nullable AgeGroup ageGroup,
            @Nullable String factor,
            @Nullable String cursor,
            int size,
            boolean withProfile) {
        return new VideoListQuery(list, withProfile ? childId : null, ageGroup, factor, cursor, size);
    }

    @Test
    @DisplayName("전체 목록은 videoId 오름차순 커서 페이지이고 연령·요인 필터가 붙는다")
    void 전체_목록은_videoId_오름차순_커서_페이지이고_연령_요인_필터가_붙는다() {
        VideoListView page1 = service.list(family.childUser, query(VideoListType.ALL, null, null, null, 2, false));
        assertThat(videoIds(page1)).containsExactly("IdpXx2gm90o", "sample00002");
        assertThat(page1.nextCursor()).isEqualTo("sample00002");

        VideoListView page2 =
                service.list(family.childUser, query(VideoListType.ALL, null, null, page1.nextCursor(), 2, false));
        assertThat(videoIds(page2)).containsExactly("sample00003", "sample00004");

        VideoListView youth =
                service.list(family.childUser, query(VideoListType.ALL, AgeGroup.YOUTH, null, null, 20, false));
        assertThat(videoIds(youth)).containsExactly("IdpXx2gm90o", "sample00002", "sample00003");
        assertThat(youth.nextCursor()).isNull();

        VideoListView adultStrength =
                service.list(family.childUser, query(VideoListType.ALL, AgeGroup.ADULT, "근력", null, 20, false));
        assertThat(videoIds(adultStrength)).containsExactly("sample00004", "zzz_unlabeled");

        VideoView first = youth.videos().getFirst();
        assertThat(first.url()).endsWith("IdpXx2gm90o");
        assertThat(first.badges()).contains("조용함");
        assertThat(first.favorited()).isFalse();
        assertThat(first.maxProgress()).isNull();
        assertThat(first.label().factors()).containsExactly("유연성", "근지구력");
    }

    @Test
    @DisplayName("즐겨찾기·최근 목록은 profileId 가 필요하고 프로필의 상호작용만 본다")
    void 즐겨찾기_최근_목록은_profileId_가_필요하고_프로필의_상호작용만_본다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.list(family.childUser, query(VideoListType.FAVORITES, null, null, null, 20, false)));
        assertThrows(
                NotSameFamilyException.class,
                () -> service.list(other.parentUser, query(VideoListType.FAVORITES, null, null, null, 20, true)));

        service.favorite(family.childUser, "sample00003", new FavoriteCommand(childId, true));
        FavoriteView fav = service.favorite(family.parentUser, "IdpXx2gm90o", new FavoriteCommand(childId, true));
        assertThat(fav.favorited()).isTrue();
        assertThat(fav.favoritedAt()).isEqualTo(Fixed.NOW);
        assertThat(assertThrows(
                                VideoNotFoundException.class,
                                () -> service.favorite(family.childUser, "nope", new FavoriteCommand(childId, true)))
                        .getCode())
                .isEqualTo("VIDEO_NOT_FOUND");

        VideoListView favorites =
                service.list(family.childUser, query(VideoListType.FAVORITES, null, null, null, 1, true));
        assertThat(videoIds(favorites)).containsExactly("IdpXx2gm90o");
        assertThat(favorites.videos())
                .singleElement()
                .extracting(VideoView::favorited)
                .isEqualTo(true);
        assertThat(favorites.nextCursor()).isEqualTo("IdpXx2gm90o");
        assertThat(videoIds(service.list(
                        family.childUser, query(VideoListType.FAVORITES, null, null, "IdpXx2gm90o", 20, true))))
                .containsExactly("sample00003");

        assertThat(service.list(family.childUser, query(VideoListType.RECENT, null, null, null, 20, true))
                        .videos())
                .isEmpty();
        service.progress(family.childUser, "sample00002", new VideoProgressCommand(childId, 0.2, 60, null));
        VideoService later = new VideoService(
                videos, interactions, missions, identity, activity, policy, Fixed.time(Fixed.NOW.plusSeconds(60)));
        later.progress(family.childUser, "sample00003", new VideoProgressCommand(childId, 0.1, 30, null));
        VideoListView recent = service.list(family.childUser, query(VideoListType.RECENT, null, null, null, 20, true));
        assertThat(videoIds(recent)).containsExactly("sample00003", "sample00002");
        assertThat(recent.videos().getFirst().maxProgress()).isEqualTo(0.1);
        assertThat(recent.nextCursor()).isNull();

        service.favorite(family.childUser, "sample00003", new FavoriteCommand(childId, false));
        assertThat(videoIds(service.list(family.childUser, query(VideoListType.FAVORITES, null, null, null, 20, true))))
                .containsExactly("IdpXx2gm90o");
    }

    @Test
    @DisplayName("완주하면 영상 길이만큼 한 번만 적립하고 이후 보고는 0분이다")
    void 완주하면_영상_길이만큼_한_번만_적립하고_이후_보고는_0분이다() {
        VideoProgressView half =
                service.progress(family.childUser, "IdpXx2gm90o", new VideoProgressCommand(childId, 0.5, 300, null));
        assertThat(half.maxProgress()).isEqualTo(0.5);
        assertThat(half.completed()).isFalse();
        assertThat(half.creditedMinutes()).isEqualTo(0);
        assertThat(half.verifiedBy()).isNull();

        VideoProgressView done =
                service.progress(family.childUser, "IdpXx2gm90o", new VideoProgressCommand(childId, 0.92, 560, null));
        assertThat(done.completed()).isTrue();
        assertThat(done.creditedMinutes()).isEqualTo(10);
        assertThat(done.verifiedBy()).isEqualTo(VerifiedBy.VIDEO_PROGRESS);
        assertThat(activity.rows
                        .get(new FakeActivity.Key(childId, Fixed.TODAY, ActivitySource.VIDEO))
                        .activeMinutes())
                .isEqualTo(10);

        VideoProgressView again =
                service.progress(family.childUser, "IdpXx2gm90o", new VideoProgressCommand(childId, 1.0, 600, null));
        assertThat(again.creditedMinutes()).isEqualTo(0);
        assertThat(again.maxProgress()).isEqualTo(1.0);
        assertThat(activity.rows
                        .get(new FakeActivity.Key(childId, Fixed.TODAY, ActivitySource.VIDEO))
                        .activeMinutes())
                .isEqualTo(10);
        assertThat(interactions.find(childId, "IdpXx2gm90o").getCreditedAt()).isEqualTo(Fixed.NOW);
    }

    @Test
    @DisplayName("보호자 동의를 거둔 아이의 영상 진행 기록은 422 CONSENT_REQUIRED 이고 진행률도 활동도 남지 않는다")
    void 보호자_동의를_거둔_아이의_영상_진행_기록은_CONSENT_REQUIRED() {
        family.withdrawConsent(childId);

        assertThat(assertThrows(
                                ParticipantConsentRequiredException.class,
                                () -> service.progress(
                                        family.childUser,
                                        "IdpXx2gm90o",
                                        new VideoProgressCommand(childId, 0.95, 570, null)))
                        .getCode())
                .isEqualTo("CONSENT_REQUIRED");
        assertThat(interactions.find(childId, "IdpXx2gm90o")).isNull();
        assertThat(activity.rows).isEmpty();
    }

    @Test
    @DisplayName("missionId 가 있으면 VIDEO_DONE 미션의 참여자 진행도를 갱신한다")
    void missionId_가_있으면_VIDEO_DONE_미션의_참여자_진행도를_갱신한다() {
        MissionService missionService =
                new MissionService(missions, completions, videos, identity, identity, policy, Fixed.time());
        MissionCreatedView mission = missionService.create(
                family.parentUser,
                family.familyId,
                new CreateMissionCommand(
                        "영상 보기",
                        Fixed.WEEK_START,
                        Fixed.WEEK_START.plusDays(6),
                        TargetMetric.VIDEO_DONE,
                        1,
                        "IdpXx2gm90o",
                        List.of(childId)));

        VideoProgressView partial = service.progress(
                family.childUser, "IdpXx2gm90o", new VideoProgressCommand(childId, 0.3, 100, mission.missionId()));
        assertThat(partial.missionProgress()).isEqualTo(0.0);

        VideoProgressView done = service.progress(
                family.childUser, "IdpXx2gm90o", new VideoProgressCommand(childId, 0.95, 580, mission.missionId()));
        assertThat(done.missionProgress()).isEqualTo(1.0);
        var participant = missions.findById(mission.missionId()).participantOf(childId);
        assertThat(participant.isCompleted()).isTrue();
        assertThat(participant.getVerifiedBy()).isEqualTo(VerifiedBy.VIDEO_PROGRESS);

        assertThat(assertThrows(
                                NotParticipantException.class,
                                () -> service.progress(
                                        family.parentUser,
                                        "IdpXx2gm90o",
                                        new VideoProgressCommand(
                                                family.parent.profileId(), 1.0, 600, mission.missionId())))
                        .getCode())
                .isEqualTo("NOT_A_PARTICIPANT");
    }

    private static List<String> videoIds(VideoListView view) {
        return view.videos().stream().map(VideoView::videoId).toList();
    }
}
